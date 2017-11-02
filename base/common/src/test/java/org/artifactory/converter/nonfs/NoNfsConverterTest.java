package org.artifactory.converter.nonfs;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.converter.helpers.MockArtifactoryHome;
import org.artifactory.environment.converter.local.PreInitConverter;
import org.artifactory.environment.converter.shared.SharedEnvironmentConverter;
import org.artifactory.test.TestUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.artifactory.converter.helpers.ConvertersManagerTestHelper.*;
import static org.artifactory.version.ArtifactoryVersion.v4111;

/**
 * @author gidis
 */
@Test
public class NoNfsConverterTest {

    private File home;

    @BeforeClass
    public void init() throws IOException {
        home = TestUtils.createTempDir(getClass());
        createHomeEnvironment(home, v4111);
    }

    public void convertDbPropertiesFromHa() throws IOException {
        ArtifactoryHome artifactoryHome = new MockArtifactoryHome(home);
        File artDir = new File(home, ".artifactory");
        File etcDir = new File(artDir, "etc");
        File pluginsDir = new File(etcDir, "plugins");
        File pluginFile = new File(pluginsDir, "plugin.groovy");
        File uiFileDir = new File(etcDir, "ui");
        File uiFile = new File(uiFileDir, "ui.log");
        File artifactorySystemPropertiesFile = new File(etcDir, "artifactory.system.properties");
        File binaryStoreXml = new File(etcDir, "binarystore.xml");
        File mimeTypeXml = new File(etcDir, "mimetypes.xml");
        File dbPropertiesFile = new File(etcDir, "db.properties");
        File homePropertiesFile = new File(etcDir, "home.properties");
        File storageProperties = new File(etcDir, "storage.properties");
        File sshPrivateKey = new File(etcDir, "security/artifactory.ssh.private");
        File sshPublicKey = new File(etcDir, "security/artifactory.ssh.public");
        // Create Full Ha environment
        createHaEnvironment(home);
        // Assert configuration not exist in home directory
        cleanAndAssert(pluginFile);
        cleanAndAssert(uiFile);
        cleanAndAssert(artifactorySystemPropertiesFile);
        cleanAndAssert(binaryStoreXml);
        cleanAndAssert(mimeTypeXml);
        cleanAndAssert(dbPropertiesFile);
        cleanAndAssert(homePropertiesFile);
        cleanAndAssert(storageProperties);
        cleanAndAssert(sshPrivateKey);
        cleanAndAssert(sshPublicKey);
        // Run converters
        runConverters(artifactoryHome);
        // Assert configuration exist in home directory
        Assert.assertTrue(pluginFile.exists());
        Assert.assertTrue(artifactorySystemPropertiesFile.exists());
        Assert.assertTrue(binaryStoreXml.exists());
        Assert.assertTrue(mimeTypeXml.exists());
        Assert.assertTrue(dbPropertiesFile.exists());
        Assert.assertTrue(pluginFile.exists());
        Assert.assertFalse(storageProperties.exists());
        Assert.assertTrue(sshPrivateKey.exists());
        Assert.assertTrue(sshPublicKey.exists());
        // Assert db properties content
        ArtifactoryDbProperties dbProperties = new ArtifactoryDbProperties(artifactoryHome, dbPropertiesFile);
        Assert.assertTrue(dbProperties.getPassword().equals("password"));
        // Assert artifactory.system.properties content
        String content = FileUtils.readFileToString(artifactorySystemPropertiesFile);
        Assert.assertTrue(content.equals("test=just_test"));
    }

    private void runConverters(ArtifactoryHome artifactoryHome) {
        try {
            ArtifactoryHome.bind(artifactoryHome);
            PreInitConverter localEnvironmentConverter = new PreInitConverter(artifactoryHome);
            localEnvironmentConverter.convert(new CompoundVersionDetails(v4111, v4111.name(),
                    Long.toString(v4111.getRevision()), 0L), artifactoryHome.getRunningArtifactoryVersion());
            SharedEnvironmentConverter sharedEnvironmentConverter = new SharedEnvironmentConverter(artifactoryHome);
            sharedEnvironmentConverter.convert(new CompoundVersionDetails(v4111, v4111.name(),
                    Long.toString(v4111.getRevision()), 0L), artifactoryHome.getRunningArtifactoryVersion());

        } finally {
            ArtifactoryHome.unbind();
        }
    }

    private void createHaEnvironment(File home) throws IOException {
        createHaPluginsFile(home);
        createHaUiFile(home);
        createHaBackupFile(home);
        createArtifactorySystemPropertiesFile(home);
        createBinaryStoreXmlFile(home);
        createMimeTypeXmlFile(home);
        createStoragePropertiesXmlFile(home);
        createSshKeys(home);
    }

    static void cleanAndAssert(File file) {
        if (file.exists()) {
            boolean delete = file.delete();
            if (!delete) {
                Assert.fail();
            }
        }
        Assert.assertFalse(file.exists());
    }
}
