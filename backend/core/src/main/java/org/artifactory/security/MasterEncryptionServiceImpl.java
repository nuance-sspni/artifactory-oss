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

package org.artifactory.security;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.MasterEncryptionService;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.FileEventType;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.layout.EncryptConfigurationInterceptor;
import org.artifactory.logging.sumo.SumoLogicTokenManager;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.interceptor.ApiKeysEncryptor;
import org.artifactory.security.interceptor.StoragePropertiesEncryptInterceptor;
import org.artifactory.security.interceptor.UserPasswordEncryptor;
import org.artifactory.security.log.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Implementation of the master encryption service based on symmetric key and Base58 encoding.
 *
 * @author Yossi Shaul
 */
@Service
public class MasterEncryptionServiceImpl implements MasterEncryptionService {
    private static final Logger log = LoggerFactory.getLogger(MasterEncryptionServiceImpl.class);

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private ApiKeysEncryptor apiKeysEncryptor;

    @Autowired
    private UserPasswordEncryptor userPasswordEncryptor;

    @Autowired
    private SumoLogicTokenManager sumoLogicTokenManager;

    @Autowired
    private AccessService accessService;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private AuditLogger auditLog;

    /**
     * Order is <b>VERY</b> important here, don't mess it up!
     */
    @Override
    public void encrypt() {
        AccessLogger.approved("Encrypting with master encryption key");
        // Create the master key if needed
        ArtifactoryHome home = ArtifactoryHome.get();
        if (!CryptoHelper.hasMasterKey(home)) {
            CryptoHelper.createMasterKeyFile(home);
            propagateMasterKeyFileChange(ArtifactoryHome.get().getMasterKeyFile(),  FileEventType.CREATE);
            addonsManager.addonByType(HaCommonAddon.class).propagateMasterEncryptionKeyChanged();
        }
        accessService.encryptOrDecrypt(true);
        apiKeysEncryptor.encryptOrDecrypt(true);
        userPasswordEncryptor.encryptOrDecrypt(true);
        sumoLogicTokenManager.encryptOrDecryptAllTokens(true);
        new StoragePropertiesEncryptInterceptor().encryptOrDecryptStoragePropertiesFile(true);
        // config interceptor will encrypt the config before it is saved to the database
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor);
        auditLog.configurationEncrypted();
    }

    /**
     * Order is <b>VERY</b> important here, don't mess it up!
     */
    @Override
    public void decrypt() {
        if (!CryptoHelper.hasMasterKey(ArtifactoryHome.get())) {
            throw new IllegalStateException("Cannot decrypt without master key file");
        }
        AccessLogger.approved("Decrypting with master encryption key");
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        accessService.encryptOrDecrypt(false);
        apiKeysEncryptor.encryptOrDecrypt(false);
        userPasswordEncryptor.encryptOrDecrypt(false);
        sumoLogicTokenManager.encryptOrDecryptAllTokens(false);
        new StoragePropertiesEncryptInterceptor().encryptOrDecryptStoragePropertiesFile(false);
        EncryptConfigurationInterceptor.decrypt(mutableDescriptor);
        File oldKeyFile = CryptoHelper.getMasterKey(ArtifactoryHome.get());
        File renamedKeyFile = CryptoHelper.removeMasterKeyFile(ArtifactoryHome.get());
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor);
        notifyKeyDeleted(oldKeyFile, renamedKeyFile);
        auditLog.configurationDecrypted();
    }

    // Order is important, don't touch me!
    private void notifyKeyDeleted(File oldKeyFile, File renamedKeyFile) {
        propagateMasterKeyFileChange(oldKeyFile, FileEventType.DELETE);
        propagateMasterKeyFileChange(renamedKeyFile, FileEventType.CREATE);
        addonsManager.addonByType(HaCommonAddon.class).propagateMasterEncryptionKeyChanged();
    }

    private void propagateMasterKeyFileChange(File masterKey, FileEventType eventType) {
        try {
            ContextHelper.get().getConfigurationManager().forceFileChanged(masterKey,"artifactory.security.", eventType);
        } catch (Exception e) {
            log.error("Failed to propagate master key file change", e);
        }
    }
}
