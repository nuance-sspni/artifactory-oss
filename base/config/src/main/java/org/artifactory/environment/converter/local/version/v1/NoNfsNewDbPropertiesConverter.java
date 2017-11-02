package org.artifactory.environment.converter.local.version.v1;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.logging.BootstrapLogger;

import java.io.File;

import static org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter.isUpgradeTo5x;
import static org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter.resolveClusterHomeDir;

/**
 * This converter is responsible for converting the storage.properties file to the new db.properties file where
 * applicable.
 * It runs only in cases where the db.properties file does not already exist locally and when there's a
 * storage.properties file to work with (locally or in the cluster nfs location if it's available)
 *
 * @author Gidi Shabat
 */
public class NoNfsNewDbPropertiesConverter implements BasicEnvironmentConverter {

    @Override
    public void convert(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        File targetDbPropertiesFile = home.getDBPropertiesFile();
        if (targetDbPropertiesFile.exists()) {
            // db.properties file exists, no need for conversion
            return;
        }
        File clusterHomeDir = resolveClusterHomeDir(home);
        File clusterStorageProperties = new File(clusterHomeDir, "ha-etc/storage.properties");
        if (clusterHomeDir != null && clusterStorageProperties.exists()) {
            // storage.properties available in cluster nfs location (cluster.home/etc/storage.properties), convert it
            convertPropertiesFile(home, targetDbPropertiesFile, clusterStorageProperties);
            return;
        }

        File localStorageProperties = new File(home.getEtcDir(), "storage.properties");
        if (localStorageProperties.exists()) {
            // storage.properties file exists locally (home/etc/storage.properties), convert it
            convertPropertiesFile(home, targetDbPropertiesFile, localStorageProperties);
        }

    }

    private void convertPropertiesFile(ArtifactoryHome home, File targetDbPropertiesFile, File storagePropertiesFile) {
        BootstrapLogger.info("Starting local environment conversion for db.properties");
        ArtifactoryDbProperties dbProperties = new ArtifactoryDbProperties(home, storagePropertiesFile);
        // Now that we have well configured DbProperty, we can save it in home/etc/db.properties file
        try {
            dbProperties.updateDbPropertiesFile(targetDbPropertiesFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert db.properties file.", e);
        }
        BootstrapLogger.info("Finished local environment conversion for db.properties");
    }

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return isUpgradeTo5x(source, target);
    }
}