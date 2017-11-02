/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.config;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.MutableStatusHolder;
import org.artifactory.converter.ConverterManager;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.reader.CentralConfigReader;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.signingkeys.SigningKeysSettings;
import org.artifactory.jaxb.JaxbHelper;
import org.artifactory.repository.onboarding.OnboardingYamlBootstrapper;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.AccessLogger;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.security.service.BasicCacheModel;
import org.artifactory.storage.db.security.service.VersioningCache;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.util.ExceptionUtils;
import org.artifactory.util.Files;
import org.artifactory.util.SerializablePair;
import org.artifactory.version.ArtifactoryVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.VersionProvider;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This class wraps the JAXB config descriptor.
 */
@Repository("centralConfig")
@Reloadable(beanClass = InternalCentralConfigService.class,
        initAfter = {DbService.class, ConfigurationChangesInterceptors.class})
public class CentralConfigServiceImpl implements InternalCentralConfigService {
    private static final Logger log = LoggerFactory.getLogger(CentralConfigServiceImpl.class);

    @Autowired
    private AuthorizationService authService;

    @Autowired
    private ConfigsService configsService;

    @Autowired
    private ConfigurationChangesInterceptors interceptors;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private ArtifactoryServersCommonService serversService;

    private VersioningCache<CentralConfigDescriptorCache> descriptorCache;

    public CentralConfigServiceImpl() {
    }

    @Override
    public void init() {
        initCacheAndGetCurrent();
    }

    private MutableCentralConfigDescriptor initCacheAndGetCurrent() {
        SerializablePair<MutableCentralConfigDescriptor, Boolean> result = getCurrentConfig();
        MutableCentralConfigDescriptor currentConfig = result.getFirst();
        if (descriptorCache == null) {
            long timeout = ConstantValues.centralConfigDirtyReadsTimeoutMillis.getLong();
            descriptorCache = new VersioningCache<>(timeout, new CentralConfigCacheLoader());
            boolean updateDescriptor = result.getSecond();
            if (updateDescriptor) {
                saveDescriptorInternal(currentConfig);
                descriptorCache.promoteDbVersion(); // in case of get calls to the cache before save, cache will be null
            }
        }
        return currentConfig;
    }

    private MutableCentralConfigDescriptor bootstrapConfigDescriptorFromYaml(
            MutableCentralConfigDescriptor currentConfig) {
        File yamlImportFile = ContextHelper.get().getArtifactoryHome().getArtifactoryBootstrapYamlImportFile();
        if (yamlImportFile.exists()) {
            return new OnboardingYamlBootstrapper(yamlImportFile, currentConfig).loadBootstrapSettingsFromYaml();
        }
        return null;
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor) {
        // Nothing to do
    }

    @Override
    public void destroy() {
        // Nothing to do
    }

    /**
     * Convert and save the artifactory config descriptor
     */
    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        // getCurrentConfig() will always return the latest version (ie will do the conversion)
        MutableCentralConfigDescriptor artifactoryConfig = initCacheAndGetCurrent();
        moveKeyStorePasswordToConfig(source, target, artifactoryConfig);
        saveNewBootstrapConfigFile(getDescriptor()); // Descriptor is reloaded in order to get the ks password encrypted if needed
    }

    protected void moveKeyStorePasswordToConfig(CompoundVersionDetails source, CompoundVersionDetails target,
            MutableCentralConfigDescriptor artifactoryConfig) {
        if (source.getVersion().afterOrEqual(ArtifactoryVersion.v522m001) || target.getVersion().before(ArtifactoryVersion.v522m001)) {
            return; // Conversion is not needed
        }
        String password = configsService.getConfig("keystore:password");
        if (StringUtils.isBlank(password)) {
            return;
        }

        // add password to Config descriptor Object
        SecurityDescriptor securityDescriptor = artifactoryConfig.getSecurity();
        SigningKeysSettings signingKeysSettings = securityDescriptor.getSigningKeysSettings();
        if (signingKeysSettings == null) {
            signingKeysSettings = new SigningKeysSettings();
        }
        signingKeysSettings.setKeyStorePassword(password);
        securityDescriptor.setSigningKeysSettings(signingKeysSettings);

        // Save Config descriptor to DB and file
        saveDescriptorInternal(artifactoryConfig);
        descriptorCache.promoteDbVersion();
        addonsManager.addonByType(HaAddon.class).propagateConfigReload();
        storeLatestConfigToFile(getConfigXml());

        try {
            configsService.deleteConfig("keystore:password");
        } catch (Exception e) {
            log.error("Deletion of Key Store Password from the database during conversion has failed");
        }
        log.info("Key store password location conversion was done");
    }

    private void saveNewBootstrapConfigFile(CentralConfigDescriptor artifactoryConfig) {
        String artifactoryConfigXml = JaxbHelper.toXml(artifactoryConfig);

        File bootstrapConfigFile = ArtifactoryHome.get().getArtifactoryConfigBootstrapFile();
        File parentFile = bootstrapConfigFile.getParentFile();
        if (parentFile.canWrite()) {
            try {
                log.info("Automatically converting the config file, original will be saved in " + parentFile.getAbsolutePath());
                File newConfigFile = bootstrapConfigFile.exists() ?
                    ArtifactoryHome.get().getArtifactoryConfigNewBootstrapFile() : bootstrapConfigFile;

                FileOutputStream fos = new FileOutputStream(newConfigFile);
                IOUtils.write(artifactoryConfigXml, fos);
                fos.close();
                if (newConfigFile != bootstrapConfigFile) {
                    Files.switchFiles(newConfigFile, bootstrapConfigFile);
                }
            } catch (Exception e) {
                log.warn("The converted config xml is:\n" + artifactoryConfigXml +
                        "\nThe new configuration is saved in DB but it failed to be saved automatically to '" +
                        parentFile.getAbsolutePath() + "' due to :" + e.getMessage() + ".\n", e);
            }
        } else {
            log.warn("The converted config xml is:\n" + artifactoryConfigXml +
                    "\nThe new configuration is saved in DB but it failed to be saved automatically to '" +
                    parentFile.getAbsolutePath() + "' since the folder is not writable.\n");
        }
    }

    private SerializablePair<MutableCentralConfigDescriptor, Boolean> getCurrentConfig() {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();

        //First try to see if there is an import config file to load
        String currentConfigXml = artifactoryHome.getImportConfigXml();
        boolean updateDescriptor = true;
        //If no import config file exists, or is empty, load from storage
        if (StringUtils.isBlank(currentConfigXml)) {
            currentConfigXml = loadConfigFromStorage();
            if (!StringUtils.isBlank(currentConfigXml)) {
                // TODO: Check the version is good, because if converted means update = true
                updateDescriptor = false;
            }
        }
        //Otherwise, load bootstrap config
        if (StringUtils.isBlank(currentConfigXml)) {
            log.info("Loading bootstrap configuration (artifactory home dir is {}).", artifactoryHome.getHomeDir());
            currentConfigXml = artifactoryHome.getBootstrapConfigXml();
        }
        MutableCentralConfigDescriptor configDescriptor = new CentralConfigReader().read(currentConfigXml);
        // try to import from yaml file
        MutableCentralConfigDescriptor yamlConfigDescriptor = bootstrapConfigDescriptorFromYaml(configDescriptor);
        if (yamlConfigDescriptor != null) {
            configDescriptor = yamlConfigDescriptor;
            updateDescriptor = true;
        }
        artifactoryHome.renameInitialConfigFileIfExists();
        log.trace("Current config xml is:\n{}", currentConfigXml);
        return new SerializablePair<>(configDescriptor, updateDescriptor);
    }

    @Nullable
    private String loadConfigFromStorage() {
        //Check in DB
        String dbConfigName = ArtifactoryHome.ARTIFACTORY_CONFIG_FILE;
        if (configsService.hasConfig(dbConfigName)) {
            log.debug("Loading existing configuration from storage.");
            return configsService.getConfig(dbConfigName);
        }
        return null;
    }

    @Override
    public void saveDescriptor(CentralConfigDescriptor descriptor) {
        CentralConfigDescriptor oldDescriptor = descriptorCache.get().descriptor;
        saveDescriptorInternal(descriptor);
        reloadConfiguration();
        interceptors.onAfterSave(descriptor, oldDescriptor);
    }

    private void saveDescriptorInternal(CentralConfigDescriptor descriptor) {
        log.trace("Setting central config descriptor for config #{}.", System.identityHashCode(this));
        assertSaveDescriptorAllowed();
        // call the interceptors before saving the new descriptor
        interceptors.onBeforeSave(descriptor);
        // Check that the proxies in the proxy list are unique
        checkUniqueProxies(descriptor);
        // Now save the Descriptor
        log.info("Saving new configuration in storage...");
        String configString = JaxbHelper.toXml(descriptor);
        configsService
                .addOrUpdateConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE, configString, System.currentTimeMillis());
        log.info("New configuration saved.");
    }

    @Override
    public CentralConfigDescriptor getDescriptor() {
        return descriptorCache.get().descriptor;
    }

    @Override
    public DateTimeFormatter getDateFormatter() {
        return descriptorCache.get().dateFormatter;
    }

    @Override
    public String getServerName() {
        return descriptorCache.get().serverName;
    }

    @Override
    public String format(long date) {
        return descriptorCache.get().dateFormatter.print(date);
    }

    @Override
    public VersionInfo getVersionInfo() {
        return new VersionInfo(ConstantValues.artifactoryVersion.getString(),
                ConstantValues.artifactoryRevision.getString());
    }

    @Override
    public String getConfigXml() {
        return JaxbHelper.toXml(descriptorCache.get().descriptor);
    }

    @Override
    public void setConfigXml(String xmlConfig) {
        CentralConfigDescriptor newDescriptor = new CentralConfigReader().read(xmlConfig);
        saveAndReloadContext(newDescriptor);
        storeLatestConfigToFile(getConfigXml());
        addonsManager.addonByType(HaAddon.class).propagateConfigReload();
    }

    @Override
    public void setLogo(File logo) throws IOException {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        final File targetFile = new File(artifactoryHome.getLogoDir(), "logo");
        if (logo == null) {
            FileUtils.deleteQuietly(targetFile);
        } else {
            FileUtils.copyFile(logo, targetFile);
        }
    }

    @Override
    public boolean defaultProxyDefined() {
        List<ProxyDescriptor> proxyDescriptors = descriptorCache.get().descriptor.getProxies();
        for (ProxyDescriptor proxyDescriptor : proxyDescriptors) {
            if (proxyDescriptor.isDefaultProxy()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MutableCentralConfigDescriptor getMutableDescriptor() {
        return (MutableCentralConfigDescriptor) SerializationUtils.clone(descriptorCache.get().descriptor);
    }

    @Override
    public void saveEditedDescriptorAndReload(CentralConfigDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalStateException("Currently edited descriptor is null.");
        }

        if (!authService.isAdmin()) {
            throw new AuthorizationException("Only an admin user can save the artifactory configuration.");
        }

        // before doing anything do a sanity check that the edited descriptor is valid
        // will fail if not valid without affecting the current configuration
        // in any case we will use this newly loaded config as the descriptor
        String configXml = JaxbHelper.toXml(descriptor);
        setConfigXml(configXml);
    }

    @Override
    public void reloadConfiguration() {
        CentralConfigDescriptor oldDescriptor = descriptorCache.get().descriptor;
        descriptorCache.promoteDbVersion();
        if (oldDescriptor != null) {
            callReload(oldDescriptor);
        }
    }

    @Override
    public void reloadConfigurationLazy() {
        CentralConfigDescriptor oldDesciptor = descriptorCache.get().descriptor;
        descriptorCache.promoteDbVersion();
        // Async
        if (oldDesciptor != null) {
            ContextHelper.get().beanForType(InternalCentralConfigService.class).callReload(oldDesciptor);
        }
    }

    @Override
    public void callReload(CentralConfigDescriptor oldDesciptor) {
        InternalContextHelper.get().reload(oldDesciptor);
    }

    @Override
    public void importFrom(ImportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        File dirToImport = settings.getBaseDir();
        //noinspection ConstantConditions
        if (dirToImport != null && dirToImport.isDirectory() && dirToImport.listFiles().length > 0) {
            status.status("Importing config...", log);
            File newConfigFile = new File(settings.getBaseDir(), ArtifactoryHome.ARTIFACTORY_CONFIG_FILE);
            if (newConfigFile.exists()) {
                status.status("Reloading configuration from " + newConfigFile, log);
                String xmlConfig = Files.readFileToString(newConfigFile);
                setConfigXml(xmlConfig);
                status.status("Configuration reloaded from " + newConfigFile, log);
            }
        } else if (settings.isFailIfEmpty()) {
            String error = "The given base directory is either empty, or non-existent";
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public void exportTo(ExportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Exporting config...", log);
        File destFile = new File(settings.getBaseDir(), ArtifactoryHome.ARTIFACTORY_CONFIG_FILE);
        JaxbHelper.writeConfig(descriptorCache.get().descriptor, destFile);
    }

    private void saveAndReloadContext(CentralConfigDescriptor newDescriptor) {
        //Reload only if all single artifactory or unique schema version in Artifactory HA cluster
        log.info("Reloading configuration...");
        try {
            CentralConfigDescriptor oldDescriptor = getDescriptor();
            if (oldDescriptor == null) {
                throw new IllegalStateException("The system was not loaded, and a reload was called");
            }

            //saveDescriptor() will set the new date formatter and server name
            saveDescriptor(newDescriptor);

            // TODO: [by FSI] If reload fails, we have the new descriptor in memory but not used
            // Need to find ways to revert or be very robust on reload.
            log.info("Configuration reloaded.");
            AccessLogger.configurationChanged();
            log.debug("Old configuration:\n{}", JaxbHelper.toXml(oldDescriptor));
            log.debug("New configuration:\n{}", JaxbHelper.toXml(newDescriptor));
        } catch (ConfigurationException e) {
            log.warn("Failed to reload configuration: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            String msg = "Failed to reload configuration: " + e.getMessage();
            Throwable xrayUnsupportedOperationException = ExceptionUtils.
                    getCauseOfTypes(e, XrayOperationException.class);
            if (xrayUnsupportedOperationException == null) {
                log.error(msg, e);
            }
            throw new RuntimeException(msg, e);
        }
    }


    private void assertSaveDescriptorAllowed() {
        if (!isSaveDescriptorAllowed()) {
            throw new RuntimeException(
                    "unstable environment: Found one or more servers with different version Config Reload denied.");
        }
    }

    @Override
    public boolean isSaveDescriptorAllowed() {
        // Approved if not HA
        HaCommonAddon haAddon = addonsManager.addonByType(HaCommonAddon.class);
        if (haAddon.isHaEnabled()) {
            // Get the context
            ArtifactoryContext artifactoryContext = ContextHelper.get();
            // Get the converter manager
            ConverterManager converterManager = artifactoryContext.getConverterManager();
            if (haAddon.isPrimary() && converterManager != null && converterManager.isConverting()) {
                return true;
            }
            // Denied if found two nodes with different versions
            List<ArtifactoryServer> otherRunningHaMembers = serversService.getOtherRunningHaMembers();
            VersionProvider versionProvider = artifactoryContext.getVersionProvider();
            CompoundVersionDetails runningVersion = versionProvider.getRunning();
            for (ArtifactoryServer otherRunningHaMember : otherRunningHaMembers) {
                String otherArtifactoryVersion = otherRunningHaMember.getArtifactoryVersion();
                if (!runningVersion.getVersionName().equals(otherArtifactoryVersion)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void storeLatestConfigToFile(String configXml) {
        try {
            Files.writeContentToRollingFile(configXml, ArtifactoryHome.get().getArtifactoryConfigLatestFile());
        } catch (IOException e) {
            log.error("Error occurred while performing a backup of the latest configuration.", e);
        }
    }

    private void checkUniqueProxies(CentralConfigDescriptor descriptor) {
        List<ProxyDescriptor> proxies = descriptor.getProxies();
        Map<String, ProxyDescriptor> map = new HashMap<>(proxies.size());
        for (ProxyDescriptor proxy : proxies) {
            String key = proxy.getKey();
            ProxyDescriptor oldProxy = map.put(key, proxy);
            if (oldProxy != null) {
                throw new RuntimeException("Duplicate proxy key in configuration: " + key + ".");
            }
        }
    }

    private class CentralConfigCacheLoader implements Callable<CentralConfigDescriptorCache> {

        @Override
        public CentralConfigDescriptorCache call() {
            return getCentralConfigDescriptorCache();
        }
    }

    private CentralConfigDescriptorCache getCentralConfigDescriptorCache() {
        log.debug("Starting to load central config");
        try {
            String currentConfigXml = loadConfigFromStorage();
            if (!StringUtils.isBlank(currentConfigXml)) {
                CentralConfigDescriptor config = new CentralConfigReader().read(currentConfigXml);
                //Create the date formatter
                String dateFormat = config.getDateFormat();
                DateTimeFormatter dateFormatter = DateTimeFormat.forPattern(dateFormat);
                //Get the server name
                String serverName = config.getServerName();
                if (serverName == null) {
                    log.debug("No custom server name in configuration. Using hostname instead.");
                    try {
                        serverName = InetAddress.getLocalHost().getHostName();
                    } catch (UnknownHostException e) {
                        log.warn("Could not use local hostname as the server instance id: {}", e.getMessage());
                        serverName = "localhost";
                    }
                }
                log.debug("Successfully finished load central config");
                return new CentralConfigDescriptorCache(config, serverName, dateFormatter);
            }
            log.error("Could not load configuration due to: No config file exist in DB");
        } catch (Exception e) {
            log.error("Could not load configuration due to: " + e.getMessage(), e);
        }
        return new CentralConfigDescriptorCache(null, null, null);
    }


    public static class CentralConfigDescriptorCache implements BasicCacheModel {

        public final CentralConfigDescriptor descriptor;
        public final String serverName;
        final DateTimeFormatter dateFormatter;
        private long version;

        public CentralConfigDescriptorCache(CentralConfigDescriptor descriptor, String serverName,
                DateTimeFormatter dateFormatter) {
            this.descriptor = descriptor;
            this.serverName = serverName;
            this.dateFormatter = dateFormatter;
        }

        @Override
        public long getVersion() {
            return version;
        }

        @Override
        public void setVersion(long version) {
            this.version = version;
        }

        @Override
        public void destroy() {

        }
    }
}