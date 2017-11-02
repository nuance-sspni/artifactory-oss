package org.artifactory.environment.converter.local.version.v1;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.ArtifactoryEncryptionKeyFileFilter;
import org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Dan Feldman
 */
public class NoNfsMasterEncryptionKeysConverter extends NoNfsBasicEnvironmentConverter {
    private static final Logger log = LoggerFactory.getLogger(NoNfsMasterEncryptionKeysConverter.class);

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return isUpgradeTo5x(source, target);
    }

    @Override
    protected void doConvert(ArtifactoryHome home, File clusterHomeDir) {
        if (clusterHomeDir != null && clusterHomeDir.exists()) {
            File etcSecurity = new File(clusterHomeDir, "ha-etc/security");
            if (etcSecurity.exists()) {
                copyMasterKeys(home, etcSecurity);
            }
        }
    }

    private void copyMasterKeys(ArtifactoryHome home, File etcSecurity) {
        File[] masterKeys = etcSecurity.listFiles(new ArtifactoryEncryptionKeyFileFilter(
                ConstantValues.securityMasterKeyLocation.getString(home)));
        if (masterKeys != null && masterKeys.length > 0) {
            log.info("Starting environment conversion: master encryption key");
            for (File masterKey : masterKeys) {
                File securityDir = home.getSecurityDir();
                safeCopyFile(masterKey, new File(securityDir, masterKey.getName()));
            }
            log.info("Finished environment conversion: master encryption key");
        }
    }
}
