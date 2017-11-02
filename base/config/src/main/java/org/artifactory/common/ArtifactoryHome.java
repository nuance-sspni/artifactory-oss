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

package org.artifactory.common;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.crypto.ArtifactoryEncryptionKeyFileFilter;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.mime.MimeTypes;
import org.artifactory.mime.MimeTypesReader;
import org.artifactory.version.ArtifactoryVersionReader;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.security.crypto.DummyEncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.file.SecurityFolderHelper;
import org.jfrog.security.util.ULID;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.rmi.dgc.VMID;
import java.util.Collection;
import java.util.Date;

/**
 * @author yoavl
 */
public class ArtifactoryHome {
    public static final String LOCK_FILENAME = ".lock";
    public static final String ARTIFACTORY_PROPERTIES_FILE = "artifactory.properties";
    public static final String ARTIFACTORY_GPG_PUBLIC_KEY = "artifactory.gpg.public";
    public static final String ARTIFACTORY_GPG_PRIVATE_KEY = "artifactory.gpg.private";
    public static final String ARTIFACTORY_SSH_PUBLIC_KEY = "artifactory.ssh.public";
    public static final String ARTIFACTORY_SSH_PRIVATE_KEY = "artifactory.ssh.private";
    public static final String BINARY_STORE_FILE_NAME = "binarystore.xml";
    public static final String LICENSE_FILE_NAME = "artifactory.lic";
    public static final String CLUSTER_LICENSE_FILE_NAME = "artifactory.cluster.license";
    public static final String DB_PROPS_FILE_NAME = "db.properties";
    public static final String COMMUNICATION_KEY_FILE_NAME = "communication.key";
    public static final String COMMUNICATION_TOKEN_FILE_NAME = "communication.token";
    public static final String SYS_PROP = "artifactory.home";
    public static final String SERVLET_CTX_ATTR = "artifactory.home.obj";
    public static final String MISSION_CONTROL_FILE_NAME = "mission.control.properties";
    public static final String ARTIFACTORY_CONVERTER_OBJ = "artifactory.converter.manager.obj";
    public static final String ARTIFACTORY_VERSION_PROVIDER_OBJ = "artifactory.version.provider.obj";
    public static final String ARTIFACTORY_CONFIG_MANAGER_OBJ = "artifactory.config.manager.obj";
    public static final String ARTIFACTORY_CONFIG_FILE = "artifactory.config.xml";
    public static final String ARTIFACTORY_CONFIG_BOOTSTRAP_FILE = "artifactory.config.bootstrap.xml";
    public static final String ARTIFACTORY_SYSTEM_PROPERTIES_FILE = "artifactory.system.properties";
    public static final String LOGBACK_CONFIG_FILE_NAME = "logback.xml";
    public static final String MIME_TYPES_FILE_NAME = "mimetypes.xml";
    public static final String ARTIFACTORY_HA_NODE_PROPERTIES_FILE = "ha-node.properties";
    public static final String ARTIFACTORY_HA_CLUSTER_ID_FILE = "cluster.id";
    public static final String BOOTSTRAP_BUNDLE_FILENAME = "bootstrap.bundle.tar.gz";
    public static final String ETC_DIR_NAME = "etc";
    public static final String SECURITY_DIR_NAME = "security";

    private static final String ENV_VAR = "ARTIFACTORY_HOME";
    private static final String ARTIFACTORY_CONFIG_LATEST_FILE = "artifactory.config.latest.xml";
    private static final String ARTIFACTORY_CONFIG_IMPORT_FILE = "artifactory.config.import.xml";
    private static final String ARTIFACTORY_BOOTSTRAP_YAML_IMPORT_FILE = "artifactory.config.import.yml";
    private static final InheritableThreadLocal<ArtifactoryHome> current = new InheritableThreadLocal<>();
    private static final EncryptionWrapper DUMMY_WRAPPER = new DummyEncryptionWrapper();
    private static final String VM_HOST_ID = new VMID().toString();
    private final File homeDir;
    private MimeTypes mimeTypes;
    private HaNodeProperties haNodeProperties;
    private EncryptionWrapper masterEncryptionWrapper;
    private File etcDir;
    private File dataDir;
    private File securityDir;
    private File backupDir;
    private File tempWorkDir;
    private File supportDir;
    private File tempUploadDir;
    private File pluginsDir;
    private File logoDir;
    private File logDir;
    private File accessClientDir;
    private File bundledAccessHomeDir;
    private ArtifactorySystemProperties artifactorySystemProperties;
    private ArtifactoryDbProperties dbProperties;
    private EncryptionWrapper communicationKeyEncryptionWrapper;

    /**
     * protected constructor for testing usage only.
     */
    protected ArtifactoryHome() {
        homeDir = null;
    }

    public ArtifactoryHome(SimpleLog logger) {
        String homeDirPath = findArtifactoryHome(logger);
        homeDir = new File(homeDirPath);
        create();
    }

    public ArtifactoryHome(File homeDir) {
        if (homeDir == null) {
            throw new IllegalArgumentException("Home dir path cannot be null");
        }
        this.homeDir = homeDir;
        create();
    }

    public static void bind(ArtifactoryHome props) {
        current.set(props);
    }

    public static void unbind() {
        current.remove();
    }

    public static boolean isBound() {
        return current.get() != null;
    }

    public static ArtifactoryHome get() {
        ArtifactoryHome home = current.get();
        if (home == null) {
            throw new IllegalStateException("Artifactory home is not bound to the current thread.");
        }
        return home;
    }

    private static void checkWritableDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory() || !dir.canWrite()) {
            String message = "Directory '" + dir.getAbsolutePath() + "' is not writable!";
            System.out.println(ArtifactoryHome.class.getName() + " - Warning: " + message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Checks the existence of the logback configuration file under the etc directory. If the file doesn't exist this
     * method will extract a default one from the war.
     */
    public File getLogbackConfig() {
        return new File(etcDir, LOGBACK_CONFIG_FILE_NAME);
    }

    public ArtifactorySystemProperties getArtifactoryProperties() {
        return artifactorySystemProperties;
    }

    public MimeTypes getMimeTypes() {
        return mimeTypes;
    }

    public File getHomeDir() {
        return homeDir;
    }

    public File getDataDir() {
        return dataDir;
    }

    public File getArtifactoryLockFile() {
        return new File(homeDir, "data/" + LOCK_FILENAME);
    }

    public File getEtcDir() {
        return etcDir;
    }

    /**
     * Get the HA etc directory. This method exists for backward compatibility only (mission control plugins use
     * this method)
     *
     * @return The etc dir
     *
     * @deprecated use {@link #getEtcDir()} instead.
     */
    @Deprecated
    public File getHaAwareEtcDir() {
        return etcDir;
    }

    public File getSecurityDir() {
        return securityDir;
    }

    public File getLogDir() {
        return logDir;
    }

    public File getBackupDir() {
        return backupDir;
    }

    public File getTempWorkDir() {
        return tempWorkDir;
    }

    public File getSupportDir() {
        return supportDir;
    }

    public File getTempUploadDir() {
        return tempUploadDir;
    }

    public File getPluginsDir() {
        return pluginsDir;
    }

    public File getLogoDir() {
        return logoDir;
    }

    public File getAccessClientDir() {
        return accessClientDir;
    }

    public File getAccessAdminCredsFile() {
        return new File(accessClientDir, "keys/access.creds");
    }

    public File getBundledAccessHomeDir() {
        return bundledAccessHomeDir;
    }

    public File getBundledAccessConfigFile() {
        return new File(bundledAccessHomeDir, "etc/access.config");
    }

    public File getDBPropertiesFile() {
        return new File(getEtcDir(), DB_PROPS_FILE_NAME);
    }

    public File getLicenseFile() {
        String licenseFileName = isHaConfigured() ? CLUSTER_LICENSE_FILE_NAME : LICENSE_FILE_NAME;
        return new File(getEtcDir(), licenseFileName);
    }

    public File getOrCreateSubDir(String subDirName) throws IOException {
        return getOrCreateSubDir(getHomeDir(), subDirName);
    }

    public File getArtifactorySystemPropertiesFile() {
        return new File(getEtcDir(), ARTIFACTORY_SYSTEM_PROPERTIES_FILE);
    }

    public File getArtifactoryHaNodePropertiesFile() {
        //This method is called also before the 'etcDir' member is initialized - hence use "etc" explicitly
        return new File(getHomeDir(), "etc/" + ARTIFACTORY_HA_NODE_PROPERTIES_FILE);
    }

    public File getArtifactoryHaClusterIdFile() {
        return new File(etcDir, ARTIFACTORY_HA_CLUSTER_ID_FILE);
    }

    public File getMimeTypesFile() {
        return new File(getEtcDir(), MIME_TYPES_FILE_NAME);
    }

    public File getBinaryStoreXmlFile() {
        return new File(getEtcDir(), BINARY_STORE_FILE_NAME);
    }

    public File getArtifactoryConfigFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_FILE);
    }

    public File getArtifactoryConfigLatestFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_LATEST_FILE);
    }

    public File getArtifactoryConfigImportFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_IMPORT_FILE);
    }

    public File getArtifactoryBootstrapYamlImportFile() {
        return new File(getEtcDir(), ARTIFACTORY_BOOTSTRAP_YAML_IMPORT_FILE);
    }

    public File getArtifactoryPropertiesFile() {
        return new File(getEtcDir(), ARTIFACTORY_PROPERTIES_FILE);
    }

    public File getArtifactoryOldPropertiesFile() {
        return new File(getDataDir(), ARTIFACTORY_PROPERTIES_FILE);
    }

    public File getArtifactoryConfigBootstrapFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_BOOTSTRAP_FILE);
    }

    public File getArtifactoryConfigNewBootstrapFile() {
        return new File(getEtcDir(), "new_" + ArtifactoryHome.ARTIFACTORY_CONFIG_BOOTSTRAP_FILE);
    }

    public File getMasterKeyFile() {
        String keyFileLocation = ConstantValues.securityMasterKeyLocation.getString(this);
        File keyFile = new File(keyFileLocation);
        if (!keyFile.isAbsolute()) {
            keyFile = new File(etcDir, keyFileLocation);
        }
        return keyFile;
    }

    public File getCommunicationKeyFile() {
        return new File(securityDir, COMMUNICATION_KEY_FILE_NAME);
    }

    public File getCommunicationTokenFile() {
        return new File(securityDir, COMMUNICATION_TOKEN_FILE_NAME);
    }

    public File getBootstrapBundleFile() {
        return new File(getEtcDir(), BOOTSTRAP_BUNDLE_FILENAME);
    }

    public File getArtifactoryGpgPublicKeyFile() {
        return new File(getSecurityDir(), ARTIFACTORY_GPG_PUBLIC_KEY);
    }

    public File getArtifactoryGpgPrivateKeyFile() {
        return new File(getSecurityDir(), ARTIFACTORY_GPG_PRIVATE_KEY);
    }

    public File getArtifactorySshPublicKeyFile() {
        return new File(getEtcDir(), "ssh/" + ARTIFACTORY_SSH_PUBLIC_KEY);
    }

    public File getArtifactorySshPrivateKeyFile() {
        return new File(getEtcDir(), "ssh/" + ARTIFACTORY_SSH_PRIVATE_KEY);
    }

    public EncryptionWrapper getMasterEncryptionWrapper() {
        // Temporary fix for the HA master key synchronization issue. We always check if the key exist to ensure that
        // the dummy key will not get 'stuck' in the memory while another ha node re-created master encryption key
        File masterKeyFile = getMasterKeyFile();
        if ((masterEncryptionWrapper == null || masterEncryptionWrapper instanceof DummyEncryptionWrapper) &&
                masterKeyFile.exists()) {
            int numOfFallbackKeys = ConstantValues.securityMasterKeyNumOfFallbackKeys.getInt(this);
            masterEncryptionWrapper = EncryptionWrapperFactory
                    .createMasterWrapper(masterKeyFile, masterKeyFile.getParentFile(), numOfFallbackKeys,
                            new ArtifactoryEncryptionKeyFileFilter(ConstantValues.securityMasterKeyLocation.getString(this)));
        } else if (masterEncryptionWrapper == null){
            masterEncryptionWrapper = DUMMY_WRAPPER;
        }
        return masterEncryptionWrapper;
    }

    public void unsetMasterEncryptionWrapper() {
        this.masterEncryptionWrapper = null;
    }

    public EncryptionWrapper getCommunicationKeyEncryptionWrapper() {
        return communicationKeyEncryptionWrapper;
    }

    public void setCommunicationKeyEncryptionWrapper(EncryptionWrapper communicationKeyEncryptionWrapper) {
        this.communicationKeyEncryptionWrapper = communicationKeyEncryptionWrapper;
    }

    /**
     * Return DB configuration
     */
    public ArtifactoryDbProperties getDBProperties() {
        return dbProperties;
    }

    public void initDBProperties() {
        File dbPropertiesFile = getDBPropertiesFile();
        if (!dbPropertiesFile.exists()) {
            throw new IllegalStateException("Artifactory could not start because db.properties could not be found.");
        }
        dbProperties = new ArtifactoryDbProperties(this);
    }

    /**
     * Calculate a unique id for the VM to support Artifactories with the same ip (e.g. accross NATs)
     */
    public String getHostId() {
        if (artifactorySystemProperties != null) {
            String result = artifactorySystemProperties.getProperty(ConstantValues.hostId);
            if (StringUtils.isNotBlank(result)) {
                return result;
            }
        }
        // TODO: Should support the HA Node host id system
        return VM_HOST_ID;
    }

    /**
     * Takes node id from ha properties into account first, used mainly for logs until we sort out RTFACT-13003
     */
    public String getHaAwareHostId() {
        //TODO [by dan]: do we want  to use cluster.id ?
        if (haNodeProperties != null) {
            return haNodeProperties.getServerId();
        } else {
            return getHostId();
        }
    }

    /**
     * @return the {@link HaNodeProperties} object that represents the
     * {@link #ARTIFACTORY_HA_NODE_PROPERTIES_FILE} contents, or null if HA was not configured properly
     */
    @Nullable
    public HaNodeProperties getHaNodeProperties() {
        return haNodeProperties;
    }

    /**
     * Returns the content of the artifactory.config.import.xml file
     *
     * @return Content of artifactory.config.import.xml if exists, null if not
     */
    public String getImportConfigXml() {
        File importConfigFile = getArtifactoryConfigImportFile();
        if (importConfigFile.exists()) {
            try {
                String configContent = FileUtils.readFileToString(importConfigFile, "utf-8");
                if (StringUtils.isNotBlank(configContent)) {
                    File bootstrapConfigFile = getArtifactoryConfigBootstrapFile();
                    org.artifactory.util.Files.switchFiles(importConfigFile, bootstrapConfigFile);
                    return configContent;
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not read data from '" + importConfigFile.getAbsolutePath() +
                        "' file due to: " + e.getMessage(), e);
            }
        }
        return null;
    }

    public void renameInitialConfigFileIfExists() {
        File initialConfigFile = getArtifactoryConfigFile();
        if (initialConfigFile.isFile()) {
            org.artifactory.util.Files.switchFiles(initialConfigFile,
                    getArtifactoryConfigBootstrapFile());
        }
    }

    public String getBootstrapConfigXml() {
        File oldLocalConfig = getArtifactoryConfigFile();
        File newBootstrapConfig = getArtifactoryConfigBootstrapFile();
        String result;
        if (newBootstrapConfig.exists()) {
            try {
                result = FileUtils.readFileToString(newBootstrapConfig, "utf-8");
            } catch (IOException e) {
                throw new RuntimeException("Could not read data from '" + newBootstrapConfig.getAbsolutePath() +
                        "' file due to: " + e.getMessage(), e);
            }
        } else if (oldLocalConfig.exists()) {
            try {
                result = FileUtils.readFileToString(oldLocalConfig, "utf-8");
            } catch (IOException e) {
                throw new RuntimeException("Could not read data from '" + newBootstrapConfig.getAbsolutePath() +
                        "' file due to: " + e.getMessage(), e);
            }
        } else {
            String resPath = "/META-INF/default/" + ARTIFACTORY_CONFIG_FILE;
            InputStream is = ArtifactoryHome.class.getResourceAsStream(resPath);
            if (is == null) {
                throw new RuntimeException("Could read the default configuration from classpath at " + resPath);
            }
            try {
                result = IOUtils.toString(is, "utf-8");
            } catch (IOException e) {
                throw new RuntimeException("Could not read data from '" + resPath +
                        "' file due to: " + e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * return true only if both HA property files are configures HA node properties and cluster properties
     */
    public boolean isHaConfigured() {
        //haNodeProperties is essentially the first thing to be inited when home is constructed, it's not null if the
        //ha-node.properties file existed when init occurred, it's also inited a second time on the reload after
        //startup so we're extra-safe.
        return haNodeProperties != null;
    }
/* TODO: [by fsi] This is too much... Need to have good check after all init done!
        File communicationKeyFile = getCommunicationKeyFile();
        if (!communicationKeyFile.exists()) {
            IllegalStateException exception = new IllegalStateException("Cannot have HA node properties configured without a communication key file!\n" +
                    "Properties file present at '" + artifactoryHaNodePropertiesFile.getAbsolutePath() + "'\n" +
                    "And the communication key file should be at '" + communicationKeyFile.getAbsolutePath() + "'\n" +
                    "Make sure The Artifactory 5.x converters where executed correctly!");
            if (ConstantValues.test.getBoolean(this)) {
                System.err.println("HA Configured but no communication key configured. Please fix the test");
                exception.printStackTrace();
                return true;
            } else {
                throw exception;
            }
        }
    }*/

    //Be careful with this, The config manager init calls this when db is not available so it must be taken from META-INF
    public CompoundVersionDetails getRunningArtifactoryVersion() {
        try (InputStream inputStream = ArtifactoryHome.class.getResourceAsStream("/META-INF/artifactory.properties")) {
            CompoundVersionDetails details = ArtifactoryVersionReader.read(inputStream);
            //Sanity check
            if (!details.isCurrent()) {
                throw new IllegalStateException("Running version is not the current version. Running: " + details
                        + " Current: " + details.getVersion());
            }
            return details;
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unexpected exception occurred: Fail to load artifactory.properties from class resource", e);
        }
    }

    private void create() {
        try {
            // We need to load the HA node properties in order to fetch the the legacy ha-data and ha-backup location
            initHaNodeProperties();
            // Init home properties
            dataDir = getOrCreateSubDir("data");
            backupDir = getOrCreateSubDir("backup");
            supportDir = getOrCreateSubDir("support");
            etcDir = getOrCreateSubDir(ETC_DIR_NAME);
            securityDir = getOrCreateSubDir(getEtcDir(), SECURITY_DIR_NAME);
            accessClientDir = getOrCreateSubDir(getSecurityDir(), "access");
            bundledAccessHomeDir = getOrCreateSubDir("access");
            SecurityFolderHelper.setPermissionsOnSecurityFolder(securityDir);
            logDir = getOrCreateSubDir("logs");
            File tempRootDir = getOrCreateSubDir(dataDir, "tmp");
            tempWorkDir = getOrCreateSubDir(tempRootDir, "work");
            tempUploadDir = getOrCreateSubDir(tempRootDir, "artifactory-uploads");

            //Check the write access to all directories that need it
            checkWritableDirectory(dataDir);
            checkWritableDirectory(logDir);
            checkWritableDirectory(backupDir);
            checkWritableDirectory(supportDir);
            checkWritableDirectory(tempRootDir);
            checkWritableDirectory(tempWorkDir);
            checkWritableDirectory(tempUploadDir);

            pluginsDir = getOrCreateSubDir(getEtcDir(), "plugins");
            logoDir = getOrCreateSubDir(getEtcDir(), "ui");
            checkWritableDirectory(pluginsDir);
            checkWritableDirectory(logoDir);
            try {
                // Never delete all files because in HA shared env (shared data dir), new nodes that starting might
                // delete files that older nodes just created and yet read/used them.
                AgeFileFilter ageFileFilter = new AgeFileFilter(DateUtils.addDays(new Date(), -1));
                Collection<File> files = FileUtils.listFiles(tempRootDir, ageFileFilter, DirectoryFileFilter.DIRECTORY);
                for (File childFile : files) {
                    FileUtils.forceDelete(childFile);
                }
                // Don't clean up all empty directories blindly because it includes required folders (e.g. work & uploads)
                org.artifactory.util.Files.cleanupEmptyDirectories(tempRootDir, file ->
                        !file.equals(tempWorkDir) && !file.equals(tempUploadDir)
                );
            } catch (Exception e) {
                System.out.println(ArtifactoryHome.class.getName() +
                        " - Warning: unable to clean temporary directories. Cause: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not initialize artifactory home directory due to: " + e.getMessage(), e);
        }
    }

    public void initPropertiesAndReload() {
        initArtifactorySystemProperties();
        reloadDbAndHaProperties();
        // Reload the data and backup dirs the directories might change after the NoNFS converter to support legacy
        reloadDataAndBackupDir();
        initCommunicationKeyWrapper();
        initMimeTypes();
        initClusterId();
    }

    /**
     * Generate a cluster ID file if it does not already exist and HA is configured
     */
    private void initClusterId() {
        File clusterIdFile = getArtifactoryHaClusterIdFile();
        if (isHaConfigured() && !clusterIdFile.exists()) {
            String clusterId;
            File accessServiceIdFile = new File(getAccessClientDir(), "keys/service_id");
            if (accessServiceIdFile.exists()) {
                // If there is already a service ID file, we assume this instance started as a standalone and was now
                // converted to an HA node. So we reuse the instance ID from the service ID as the cluster ID.
                try {
                    String serviceId = Files.readAllLines(accessServiceIdFile.toPath()).get(0);
                    clusterId = serviceId.substring(serviceId.indexOf('@') + 1);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read access service ID from file '" + accessServiceIdFile + "'", e);
                }
            } else {
                clusterId = ULID.random().toLowerCase();
            }
            try {
                Files.write(clusterIdFile.toPath(), clusterId.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create cluster ID file.", e);
            }
        }
    }

    /**
     * Get the artifactory HA cluster ID.
     * @return the cluster ID if HA is configured, or <tt>null</tt> otherwise.
     */
    @Nullable
    public String getClusterId() {
        File clusterIdFile = getArtifactoryHaClusterIdFile();
        if (clusterIdFile.exists()) {
            try {
                return Files.readAllLines(clusterIdFile.toPath()).get(0);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read cluster ID from file.", e);
            }
        }
        return null;
    }

    /**
     * Reloads the ha-node.properties and db.properties files that may have changed by the no-nfs converters
     */
    private void reloadDbAndHaProperties() {
        initHaNodeProperties();
        initDBProperties();
    }

    /**
     * Reload the data and backup dirs the directories might change after the no-nfs converters to support legacy
     * ha-data and ha-backup dirs
     */
    private void reloadDataAndBackupDir()  {
        try {
            dataDir = getOrCreateSubDir("data");
            backupDir = getOrCreateSubDir("backup");
        } catch (Exception e){
            throw new RuntimeException("Failed to reload the data and backup directories.", e);
        }
    }

    private void initCommunicationKeyWrapper() {
        try {
            File communicationKey = getCommunicationKeyFile();
            if (communicationKey.exists()) {
                EncryptionWrapper masterWrapper = EncryptionWrapperFactory.createMasterWrapper(communicationKey,
                        communicationKey.getParentFile(), 3,
                        new ArtifactoryEncryptionKeyFileFilter(ConstantValues.securityMasterKeyLocation.getString(this)));
                setCommunicationKeyEncryptionWrapper(masterWrapper);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init communication key.", e);
        }
    }

    /**
     * loads ha-node.properties into memory if they exist
     */
    private void initHaNodeProperties() {
        //If ha props exist, load them
        File haPropertiesFile = getArtifactoryHaNodePropertiesFile();
        if (haPropertiesFile.exists()) {
            //load ha properties
            haNodeProperties = new HaNodeProperties();
            haNodeProperties.load(haPropertiesFile);
        }
    }

    public void initArtifactorySystemProperties() {
        try {
            File file = getArtifactorySystemPropertiesFile();
            artifactorySystemProperties = new ArtifactorySystemProperties();
            artifactorySystemProperties.loadArtifactorySystemProperties(file, getRunningArtifactoryVersion());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse artifactory system properties from: " +
                    getArtifactorySystemPropertiesFile().getAbsolutePath(), e);
        }
    }

    private void initMimeTypes() {
        try {
            File mimeTypesFile = getMimeTypesFile();
            String mimeTypesXml = FileUtils.readFileToString(mimeTypesFile);
            mimeTypes = new MimeTypesReader().read(mimeTypesXml);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse mime types file from: " +
                    getMimeTypesFile().getAbsolutePath(), e);
        }
    }

    private File getOrCreateSubDir(File parent, String subDirName) throws IOException {
        String path = null;
        HaNodeProperties haNodeProperties = getHaNodeProperties();
        if (haNodeProperties != null) {
            path = haNodeProperties.getProperty(subDirName);
        }
        File subDir;
        if (StringUtils.isNotBlank(path)) {
            File userPath = new File(path);
            if(userPath.isAbsolute()) {
                // If the user provided absolute path then there is no need for the parent dir.
                subDir = userPath;
            }else{
                // If the user provided relative path then the "subDir" should be merge of "parent" dir and "userPath".
                subDir = new File(parent, path);
            }
        } else {
            // If the user didn't provide userPath then use the default name
            subDir = new File(parent, subDirName);
        }
        FileUtils.forceMkdir(subDir);
        return subDir;
    }

    private String findArtifactoryHome(SimpleLog logger) {
        String home = System.getProperty(SYS_PROP);
        String artHomeSource = "System property";
        if (home == null) {
            //Try the environment var
            home = System.getenv(ENV_VAR);
            artHomeSource = "Environment variable";
            if (home == null) {
                home = new File(System.getProperty("user.home", "."), ".artifactory").getAbsolutePath();
                artHomeSource = "Default (user home)";
            }
        }
        home = home.replace('\\', '/');
        logger.log("Using artifactory.home at '" + home + "' resolved from: " + artHomeSource);
        return home;
    }

    public void writeArtifactoryProperties() {
        File artifactoryPropertiesFile = getArtifactoryPropertiesFile();
        //Copy the artifactory.properties file into the data folder
        try {
            //Copy from default
            URL resource = ArtifactoryHome.class.getResource("/META-INF/" + ARTIFACTORY_PROPERTIES_FILE);
            FileUtils.copyURLToFile(resource, artifactoryPropertiesFile);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy " + ARTIFACTORY_PROPERTIES_FILE + " to " +
                    artifactoryPropertiesFile.getAbsolutePath(), e);
        }
    }

    /**
     * Missing Closure ;-)
     */
    public interface SimpleLog {
        void log(String message);
    }
}
