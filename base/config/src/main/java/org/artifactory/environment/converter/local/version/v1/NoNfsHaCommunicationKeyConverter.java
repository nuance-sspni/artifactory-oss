package org.artifactory.environment.converter.local.version.v1;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.ArtifactoryEncryptionKeyFileFilter;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.logging.BootstrapLogger;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.file.SecurityFolderHelper;

import java.io.File;
import java.io.IOException;

import static org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter.resolveClusterDataDir;

/**
 * This converter is responsible for setting up the cluster communication.key file as needed when converting to V5.0,
 * although it always runs at startup to facilitate easy scaling of cluster nodes.
 *
 * Users can either generate one and put it in the cluster nfs or the node's local etc folder - in case it's in the
 * local location the node will start up using it, in case it's in the nfs location it will be copied locally.
 *
 * If the file does not exist locally the converter checks for it's existence in the cluster nfs location (might have
 * been generated and copied there by a previous node) - if it exists there it will be copied locally.
 *
 * Lastly, if the file does not exist in both local and nfs locations it is assumed this node is the first one to start,
 * the key is generated and copied over to the cluster nfs location if it is available.
 *
 * @author Gidi Shabat
 * @author Dan Feldman
 */
public class NoNfsHaCommunicationKeyConverter implements BasicEnvironmentConverter {

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return true;
    }

    @Override
    public void convert(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        File targetHaNodePropertiesFile = home.getArtifactoryHaNodePropertiesFile();
        // If not HA do nothing
        if (!targetHaNodePropertiesFile.exists()) {
            return;
        }
        File localKeyFile = home.getCommunicationKeyFile();
        // Local communication.key file exists, nothing to do
        if (localKeyFile.exists()) {
            return;
        }
        BootstrapLogger.info("Starting local environment conversion: communication.key");
        File clusterDataDir = resolveClusterDataDir(home);
        if (clusterDataDir != null) {
            handleKeyGenerationWithExistingDataDir(localKeyFile, clusterDataDir);
        } else {
            handleKeyGenerationWithoutDataDir(home, localKeyFile);
        }
        // Assert operation was successful.
        if (!localKeyFile.exists()) {
            throw new RuntimeException("Invalid state: communication key '" + localKeyFile.getAbsolutePath() + "' should exist");
        }
        home.setCommunicationKeyEncryptionWrapper(EncryptionWrapperFactory.createMasterWrapper(localKeyFile,
                localKeyFile.getParentFile(), 3,
                new ArtifactoryEncryptionKeyFileFilter(ConstantValues.securityMasterKeyLocation.getString(home))));
        BootstrapLogger.info("Finished local environment conversion: communication.key");
    }

    private void handleKeyGenerationWithoutDataDir(ArtifactoryHome artHome, File localKeyFile) {
        // Member nodes cannot generate communication.key
        if (artHome.getHaNodeProperties().isPrimary()) {
            // cluster dir unavailable and no local key, just generate one
            try {
                generateLocalKeyFile(localKeyFile);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate cluster communication key: " + e.getMessage(), e);
            }
        }
    }

    private void handleKeyGenerationWithExistingDataDir(File localKeyFile, File clusterDataDir) {
        // cluster dir exists, and no local key - check if there's one there
        File nfsKeyFile = new File(clusterDataDir, "security/communication.key");
        if (nfsKeyFile.exists()) {
            // key file present in nfs - copy over to local
            try {
                FileUtils.copyFile(nfsKeyFile, localKeyFile);
            } catch (Exception e) {
                throw new RuntimeException("Failed to copy cluster communication key from "
                        + nfsKeyFile.getAbsolutePath() + " to " + localKeyFile.getAbsolutePath() + ": "
                        + e.getMessage(), e);
            }
        } else {
            // key file doesn't exist locally or in nfs, generate one and copy to nfs for the other nodes to use.
            try {
                generateLocalKeyFile(localKeyFile);
                FileUtils.copyFile(localKeyFile, nfsKeyFile);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate cluster communication key: " + e.getMessage(), e);
            }
        }
    }

    private void generateLocalKeyFile(File localKeyFileLocation) throws IOException {
        SecurityFolderHelper.setPermissionsOnSecurityFolder(localKeyFileLocation.getParentFile());
        SecurityFolderHelper.createKeyFile(localKeyFileLocation);
    }
}
