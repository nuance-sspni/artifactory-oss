package org.artifactory.storage;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.ResourceUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author gidis
 */
@Test
public class DbPropertiesEncryptDecryptTest extends ArtifactoryHomeBoundTest {

    ArtifactoryDbProperties sp;

    @BeforeMethod
    public void loadProperties() throws IOException {
        String filePath = "/storage/dbpostgres.properties";
        sp = new ArtifactoryDbProperties(ArtifactoryHome.get(), ResourceUtils.getResourceAsFile(filePath));
    }

    @Test()
    public void propertiesPasswordEncryptionTest() throws IOException {
        String filePath = "/storage/dbpostgres.properties";
        if (!CryptoHelper.hasMasterKey(ArtifactoryHome.get())) {
            CryptoHelper.createMasterKeyFile(ArtifactoryHome.get());
        }
        String pass = sp.getProperty(ArtifactoryDbProperties.Key.password);
        assertTrue(!CryptoHelper.isEncryptedUserPassword(pass));
        pass = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), pass);
        int numOfLineBeforeEncryptAndSaving = StorageTestHelper.getFileNumOfLines(filePath);
        int passwordLinePositionBeforeEncryptAndSave = StorageTestHelper.getKeyPositionLine(filePath, "password");
        sp.setPassword(pass);
        sp.updateDbPropertiesFile(getPropertiesStorageFile(filePath));
        int numOfLineAfterEncryptAndSaving = StorageTestHelper.getFileNumOfLines(filePath);
        int passwordLinePositionAfterEncryptAndSave = StorageTestHelper.getKeyPositionLine(filePath, "password");
        // check that comments are maintain
        assertEquals(numOfLineBeforeEncryptAndSaving, numOfLineAfterEncryptAndSaving);
        // check that order is maintain
        assertEquals(passwordLinePositionBeforeEncryptAndSave, passwordLinePositionAfterEncryptAndSave);
    }

    private File getPropertiesStorageFile(String filePath) {
        return ResourceUtils.getResourceAsFile(filePath);
    }


}
