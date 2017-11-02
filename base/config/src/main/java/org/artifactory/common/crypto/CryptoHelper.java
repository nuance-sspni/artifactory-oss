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

package org.artifactory.common.crypto;

import org.apache.commons.lang.StringUtils;
import org.artifactory.common.ArtifactoryHome;
import org.jfrog.security.crypto.EncodingType;
import org.jfrog.security.file.SecurityFolderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;


/**
 * Helper class for encrypting/decrypting passwords.
 *
 * @author Yossi Shaul
 */
public abstract class CryptoHelper {
    private static final Logger log = LoggerFactory.getLogger(CryptoHelper.class);

    private CryptoHelper() {
        // utility class
    }

    public static boolean isMasterEncrypted(String in) {
        return EncodingType.ARTIFACTORY_MASTER.isEncodedByMe(in);
    }

    public static boolean isEncryptedUserPassword(String in) {
        return EncodingType.ARTIFACTORY_PASSWORD.isEncodedByMe(in);
    }

    public static boolean isApiKey(String in) {
        return EncodingType.ARTIFACTORY_API_KEY.isEncodedByMe(in);
    }

    public static String encryptIfNeeded(ArtifactoryHome home, String password) {
        if(StringUtils.isBlank(password)) {
             //No password, no encryption
            return password;
        }
        return home.getMasterEncryptionWrapper().encryptIfNeeded(password);
    }

    public static String decryptIfNeeded(ArtifactoryHome home, String password) {
        if (isMasterEncrypted(password)) {
            File keyFile = home.getMasterKeyFile();
            if (keyFile.exists()) {
                return home.getMasterEncryptionWrapper().decryptIfNeeded(password).getDecryptedData();
            } else {
                log.warn("Encrypted password found and no Master Key file exists at " + keyFile.getAbsolutePath());
            }
        }
        return password;    }

    /**
     * Renames the master key file, effectively disabling encryption.
     * @return the renamed master key file
     */
    public static File removeMasterKeyFile(ArtifactoryHome home) {
        File keyFile = home.getMasterKeyFile();
        File renamedKey = SecurityFolderHelper.removeKeyFile(keyFile);
        unsetMasterEncryptionWrapper(home);
        return renamedKey;
    }

    public static void unsetMasterEncryptionWrapper(ArtifactoryHome home) {
        home.unsetMasterEncryptionWrapper();
    }

    /**
     * Creates a master encryption key file. Throws an exception if the key file already exists of on any failure with
     * file or key creation.
     */
    public static void createMasterKeyFile(ArtifactoryHome home) {
        File keyFile = home.getMasterKeyFile();
        try {
            SecurityFolderHelper.setPermissionsOnSecurityFolder(keyFile.getParentFile());
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not create and set permission on " + keyFile.getParentFile().getAbsolutePath(), e);
        }
        SecurityFolderHelper.createKeyFile(keyFile);
        home.unsetMasterEncryptionWrapper();
    }

    public static boolean hasMasterKey(ArtifactoryHome home) {
        return home.getMasterKeyFile().exists();
    }

    public static File getMasterKey(ArtifactoryHome home) {
        return home.getMasterKeyFile();
    }
}
