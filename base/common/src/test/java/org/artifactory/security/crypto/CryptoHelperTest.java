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

package org.artifactory.security.crypto;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.security.crypto.*;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.*;

/**
 * Tests the CryptoHelper.
 *
 * @author Yossi Shaul
 * @author Noam Tenne
 */
@Test
public class CryptoHelperTest extends ArtifactoryHomeBoundTest {

    public void isNotEncrypted() {
        assertFalse(CryptoHelper.isEncryptedUserPassword("blabla"));
        assertFalse(CryptoHelper.isMasterEncrypted("blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{RSA}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{ENC}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{DESede}blabla"));
        assertFalse(CryptoHelper.isMasterEncrypted("{DESede}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("\\{DESede\\}blabla"), "Escaped maven encryption prefix");
        assertFalse(CryptoHelper.isMasterEncrypted("\\{DESede\\}blabla"), "Escaped maven encryption prefix not master");
        assertFalse(CryptoHelper.isEncryptedUserPassword("\\{DESede}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{DESede\\}blabla"));
    }

    public void encryptDecryptMasterKey() {
        // First make sure no master key around
        if (CryptoHelper.hasMasterKey(ArtifactoryHome.get())) {
            CryptoHelper.removeMasterKeyFile(ArtifactoryHome.get());
        }
        String pass = "mySuper34Hard42Password";
        String nonEncryptPass = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), pass);
        assertEquals(nonEncryptPass, pass, "Before creating master key, no encryption should run");
        CryptoHelper.createMasterKeyFile(ArtifactoryHome.get());
        String encryptPass = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), pass);
        assertNotEquals(encryptPass, pass, "After creating master key, encryption should run");
        assertTrue(encryptPass.startsWith("AM"), "Encrypted password should start with AM");
        assertTrue(CryptoHelper.isMasterEncrypted(encryptPass), "Encrypted password should be master encrypted");
        assertFalse(CryptoHelper.isEncryptedUserPassword(encryptPass),
                "Encrypted password should not be password encrypted");
        String encryptPass2 = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        assertEquals(encryptPass2, encryptPass, "Encrypting twice should not do anything");

        // Encrypting password then encrypt in master
        EncryptionWrapper masterKeyWrapper = ArtifactoryHome.get().getMasterEncryptionWrapper();
        EncryptionWrapper keyWrapper = EncryptionWrapperFactory.createKeyWrapper(masterKeyWrapper, new EncodedKeyPair(
                new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair()), masterKeyWrapper));
        String passEncrypt = keyWrapper.encryptIfNeeded(pass);
        assertNotEquals(passEncrypt, encryptPass, "Encrypting pass should be different");
        assertFalse(CryptoHelper.isMasterEncrypted(passEncrypt), "Encrypted password should not be master encrypted");
        assertTrue(CryptoHelper.isEncryptedUserPassword(passEncrypt),
                "Encrypted password should be password encrypted");
        String encryptPassEncrypt = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), passEncrypt);
        assertNotEquals(encryptPassEncrypt, passEncrypt, "Encrypting pass should be different");
        assertTrue(CryptoHelper.isMasterEncrypted(encryptPassEncrypt), "Encrypted password should be master encrypted");
        assertFalse(CryptoHelper.isEncryptedUserPassword(encryptPassEncrypt),
                "Encrypted password should not be password encrypted");

        String decrypted = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        assertEquals(decrypted, pass, "decrypted password should go back to origin");

        String decryptedPass = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPassEncrypt);
        assertEquals(decryptedPass, passEncrypt, "decrypted password encrypted should go back to pass encrypt");
        assertEquals(keyWrapper.decryptIfNeeded(decryptedPass).getDecryptedData(), pass,
                "decrypted password should go back to origin");
    }

    public void removeKeyFile() {
        if (!CryptoHelper.hasMasterKey(ArtifactoryHome.get())) {
            CryptoHelper.createMasterKeyFile(ArtifactoryHome.get());
        }
        File master = ArtifactoryHome.get().getMasterKeyFile();
        assertTrue(master.exists(), "Master encryption file not found at " + master.getAbsolutePath());
        CryptoHelper.removeMasterKeyFile(ArtifactoryHome.get());
        assertFalse(master.exists(), "Master encryption file found at " + master.getAbsolutePath());
    }

    public void testEmptyOrNullPasswordEncryption() {
        if (CryptoHelper.hasMasterKey(ArtifactoryHome.get())) {
            CryptoHelper.removeMasterKeyFile(ArtifactoryHome.get());
        }
        CryptoHelper.createMasterKeyFile(ArtifactoryHome.get());
        // First test with empty password
        String resultPassEmptyPassword = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), "");
        assertEquals("", resultPassEmptyPassword);
        // Then test with a null password
        String resultPassNullPassword = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), null);
        assertEquals(resultPassNullPassword, null);
    }
}
