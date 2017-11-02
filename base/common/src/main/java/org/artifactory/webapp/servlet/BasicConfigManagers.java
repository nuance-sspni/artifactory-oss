package org.artifactory.webapp.servlet;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.ConfigurationManager;
import org.artifactory.common.config.ConfigurationManagerImpl;
import org.artifactory.converter.ConverterManager;
import org.artifactory.converter.ConvertersManagerImpl;
import org.artifactory.converter.VersionProviderImpl;
import org.artifactory.version.VersionProvider;

import javax.servlet.ServletContext;

/**
 * @author Fred Simon on 9/16/16.
 */
public class BasicConfigManagers {

    public final ArtifactoryHome artifactoryHome;
    public final ConfigurationManager configurationManager;
    public final VersionProvider versionProvider;
    public final ConverterManager convertersManager;

    public BasicConfigManagers(ServletContext servletContext) {
        this.artifactoryHome = (ArtifactoryHome) servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR);
        this.versionProvider = (VersionProvider) servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ);
        this.convertersManager = (ConverterManager) servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_CONVERTER_OBJ);
        this.configurationManager = (ConfigurationManager) servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_CONFIG_MANAGER_OBJ);
    }

    public BasicConfigManagers(ArtifactoryHome artifactoryHome) {
        this(artifactoryHome, new ConfigurationManagerImpl(artifactoryHome));
    }

    public BasicConfigManagers(ArtifactoryHome artifactoryHome, ConfigurationManager configurationManager) {
        this.artifactoryHome = artifactoryHome;
        // Create configuration manager that will synchronize the shared files
        this.configurationManager = configurationManager;
        // Create the version provider managing running and previous versions
        this.versionProvider = new VersionProviderImpl(artifactoryHome);
        // Create the converter manager that will convert the needed configurations, files and DB tables
        this.convertersManager = new ConvertersManagerImpl(artifactoryHome, versionProvider, configurationManager);
    }

    public void addServletAttributes(ServletContext servletContext) {
        // Add the artifactory home to the servlet context
        servletContext.setAttribute(ArtifactoryHome.SERVLET_CTX_ATTR, artifactoryHome);
        // Add the converterManager to the servlet context
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_CONVERTER_OBJ, convertersManager);
        // Add the version provider to the servlet context
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ, versionProvider);
        // Add the configuration manager to the servlet context
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_CONFIG_MANAGER_OBJ, configurationManager);
    }

    /**
     * Extract specific init-params from the servlet context and set them in their corresponding {@link ConstantValues}
     * (only if a value exists - non <tt>null</tt>).
     * @param servletContext the servlet context
     * @see ConstantValues#accessClientServerUrlOverride
     */
    public void inheritInitParamsAsConstantValues(ServletContext servletContext) {
        String propertyName = ConstantValues.accessClientServerUrlOverride.getPropertyName();
        String value = servletContext.getInitParameter(propertyName);
        if (value != null) {
            artifactoryHome.getArtifactoryProperties().setProperty(propertyName, value);
        }
    }

    public void initialize() {
        initHomes();
        // Now that we have converted configuration we can load the properties/configuration into memory
        artifactoryHome.initPropertiesAndReload();
    }

    private void initHomes() {
        // Do environment conversion
        versionProvider.initOriginalHomeVersion();
        convertersManager.convertPreInit();
        // Need to ensure db.properties exist at this point, default or converted
        configurationManager.initDbProperties();
        // Now that the local environment is converted and ready, init the configuration manager's temp db channels
        configurationManager.initDbChannels();
        // DBChannel is required for version resolution, now we can create it after the config manager inited
        versionProvider.init();
        // Prepare files and DB before sync
        convertersManager.convertHomeSync();
        // Sync or create default files after mandatory env conversion is done
        configurationManager.startSync();
        convertersManager.convertHome();
    }
}
