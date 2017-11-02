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

package org.artifactory.security.interceptor;

import org.apache.commons.lang.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.FileEventType;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.crypto.CryptoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author Chen Keinan
 */
public class StoragePropertiesEncryptInterceptor {
    private static final Logger log = LoggerFactory.getLogger(StoragePropertiesEncryptInterceptor.class);

    /**
     * encrypt or decrypt storage properties file
     *
     * @param encrypt - if true encrypt else decrypt
     */
    public void encryptOrDecryptStoragePropertiesFile(boolean encrypt) {
        try {
            File propertiesFile = getDbPropertiesFile();
            ArtifactoryDbProperties dbProperties = ContextHelper.get().beanForType(ArtifactoryDbProperties.class);
            String password = dbProperties.getProperty(ArtifactoryDbProperties.Key.password);
            if (StringUtils.isNotBlank(password)) {
                dbProperties.setPassword(getNewPassword(encrypt, password));
                dbProperties.updateDbPropertiesFile(propertiesFile);
                try {
                    ContextHelper.get().getConfigurationManager().forceFileChanged(dbProperties.getDbPropertiesFile(),
                            "db.properties", FileEventType.MODIFY);
                } catch (Exception e) {
                    log.debug("Failed to propagate db.properties change", e);
                    log.warn("Failed to propagate db.properties change");
                }
            }
//            StorageProperties storageProperties = ContextHelper.get().beanForType(StorageProperties.class);
//            String s3Credential = storageProperties.getProperty(StorageProperties.Key.binaryProviderS3Credential);
//            if (StringUtils.isNotBlank(s3Credential)) {
//                storageProperties.setS3Credential(getNewPassword(encrypt, s3Credential));
//            }
//            String s3ProxyCredential = storageProperties.getProperty(StorageProperties.Key.binaryProviderS3ProxyCredential);
//            if (StringUtils.isNotBlank(s3ProxyCredential)) {
//                storageProperties.setS3ProxyCredential(getNewPassword(encrypt, s3ProxyCredential));
//            }
//            storageProperties.updateStoragePropertiesFile(propertiesFile);
        } catch (IOException e) {
            log.error("Error Loading encrypt storage properties File" + e.getMessage(), e, log);
        }
    }

    /**
     * get properties file from context Artifactory home
     * getDbPropertiesFile@return Storage properties File
     */
    private File getDbPropertiesFile() {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        return artifactoryHome.getDBPropertiesFile();
    }

    private String getNewPassword(boolean encrypt, String password) {
        if (StringUtils.isNotBlank(password)) {
            if (encrypt) {
                return CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), password);
            } else {
                return CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), password);
            }
        }
        return null;
    }
}
