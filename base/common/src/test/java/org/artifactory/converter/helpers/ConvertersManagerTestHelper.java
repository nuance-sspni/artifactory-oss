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

package org.artifactory.converter.helpers;

import org.apache.commons.io.FileUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.ConfigurationManager;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.converter.ConvertersManagerImpl;
import org.artifactory.converter.VersionProviderImpl;
import org.artifactory.test.TestUtils;
import org.artifactory.util.ResourceUtils;
import org.artifactory.version.ArtifactoryVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.webapp.servlet.BasicConfigManagers;
import org.joda.time.DateTimeUtils;
import org.testng.Assert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import static org.artifactory.common.ArtifactoryHome.*;
import static org.artifactory.common.ConstantValues.*;

/**
 * Helper static methods for the ConverterManagerTest.
 *
 * @author Gidi Shabat
 */
public class ConvertersManagerTestHelper {

    /**
     * Creates Complete environment files and database simulator
     */
    public static ArtifactoryContext createEnvironment(File home, ArtifactoryVersion homeVersion,
            ArtifactoryVersion dbVersion) throws IOException {
        createHomeEnvironment(home, homeVersion);
        // Create ArtifactoryHome
        ArtifactoryHome artifactoryHome = new MockArtifactoryHome(home);
        // Create configuration  manager
        ConfigurationManager configurationManager = new MockConfigurationManagerImpl(artifactoryHome);
        BasicConfigManagers bcm = new BasicConfigManagers(artifactoryHome, configurationManager);
        ((ConvertersManagerImpl) bcm.convertersManager).addPreInitConverter(
                new MockHomeLocalConverter(home + "/.history/preInitFiles.test"));
        ((ConvertersManagerImpl) bcm.convertersManager).addSyncFilesConverter(
                new MockHomeSharedConverter(home + "/.history/syncFiles.test"));
        ((ConvertersManagerImpl) bcm.convertersManager).addHomeConverter(
                new MockHomeSharedConverter(home + "/.history/homeFiles.test"));
        // Do environment conversion
        bcm.initialize();
        if (dbVersion != null) {
            TestUtils.setField(bcm.versionProvider, "originalDbVersion",
                    new CompoundVersionDetails(dbVersion, dbVersion.name(), Long.toString(dbVersion.getRevision()),
                            0L));
        }
        artifactoryHome.getArtifactoryProperties().setProperty(ConstantValues.test.getPropertyName(), "true");
        // Finally we can start artifactory
        MockArtifactoryContext artifactoryContext = new MockArtifactoryContext(dbVersion, 1,
                (ConvertersManagerImpl) bcm.convertersManager, (VersionProviderImpl) bcm.versionProvider,
                configurationManager, true);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryHome);
        MockHomeSharedConverter artifactoryConverter = new MockHomeSharedConverter(home + "/.history/db.test");
        bcm.convertersManager.serviceConvert(artifactoryConverter);
        bcm.convertersManager.afterServiceConvert();
        bcm.convertersManager.afterContextReady();
        return artifactoryContext;
    }

    public static boolean isArtifactoryLocalHomePropertiesHasBeenUpdated(File home) throws IOException {
        return isFileChanged(home + "/.history/homeFiles.test");
    }

    public static boolean isArtifactorySharedHomePropertiesHasBeenUpdated(File home) throws IOException {
        return isFileChanged(home + "/.history/syncFiles.test");
    }

    public static boolean isServiceConvertWasRun(File home) throws IOException {
        return isFileChanged(home + "/.history/db.test");
    }

    public static boolean isArtifactoryPropertiesHasBeenUpdated(File home) throws IOException {
        return isFileChanged(home + "/.artifactory/etc/artifactory.properties");
    }

    private static boolean isFileChanged(String path) throws IOException {
        File file = new File(path);
        return file.exists();
    }

    public static void createHomeEnvironment(File home, ArtifactoryVersion homeVersion) throws IOException {
        File artDir = new File(home, ".artifactory");
        File artHa = new File(home, ".artifactory-ha");

        FileUtils.deleteDirectory(artDir);
        FileUtils.deleteDirectory(artHa);
        FileUtils.deleteDirectory(new File(home, ".history"));
        FileUtils.deleteQuietly(new File(home, ".artifactory/data/artifactory.properties"));

        home.mkdir();
        Assert.assertTrue(artDir.mkdir());
        Assert.assertTrue(artHa.mkdir());
        Assert.assertTrue(new File(home, ".history").mkdir());
        Assert.assertTrue(new File(artHa, "ha-data").mkdir());
        Assert.assertTrue(new File(artHa, "ha-etc").mkdir());
        if (homeVersion != null) {
            File dataDir = new File(artDir, "data");
            File etcDir = new File(artDir, "etc");
            Assert.assertTrue(dataDir.mkdir());
            Assert.assertTrue(etcDir.mkdir());
            Properties artifactoryProperties = createArtifactoryProperties(homeVersion);
            String basePath = "/converters/templates/home/" + homeVersion.getValue();
            try (FileOutputStream out = new FileOutputStream(new File(dataDir, "artifactory.properties"))) {
                artifactoryProperties.store(out, "");
            }
            try (FileOutputStream out = new FileOutputStream(new File(etcDir, ARTIFACTORY_HA_NODE_PROPERTIES_FILE))) {
                Properties haNodeProperties = createHaNodeProperties(home);
                haNodeProperties.store(out, "");
            }
            if (!homeVersion.isCurrent()) {
                safeResourceCopy(etcDir, basePath, LOGBACK_CONFIG_FILE_NAME);
                safeResourceCopy(etcDir, basePath, MIME_TYPES_FILE_NAME);
            }

            // Update cluster files
            artifactoryProperties = createArtifactoryProperties(homeVersion);
            try (FileOutputStream out = new FileOutputStream(
                    home + "/.artifactory-ha/ha-data/" + "artifactory.properties")) {
                artifactoryProperties.store(out, "");
            }
            if (!homeVersion.isCurrent()) {
                // HA
                File haEtc = new File(home + "/.artifactory-ha/ha-etc/");
                safeResourceCopy(haEtc, basePath, LOGBACK_CONFIG_FILE_NAME);
                safeResourceCopy(haEtc, basePath, MIME_TYPES_FILE_NAME);
            }
        }
    }

    protected static void safeResourceCopy(File etcDir, String basePath, String fileName) {
        String resourcePath = basePath + "/" + fileName;
        try {
            String logbackValue = ResourceUtils.getResourceAsString(resourcePath);
            File destFile = new File(etcDir, fileName);
            FileUtils.write(destFile, logbackValue);
        } catch (Exception e) {
            // Not too much of an issue
            System.out.println("Could not copy the file from " + resourcePath + " due to " + e.getMessage());
        }
    }

    public static Properties createHaNodeProperties(File home) {
        Properties properties = new Properties();
        properties.put(HaNodeProperties.PROP_NODE_ID, "pom");
        properties.put("cluster.home", new File(home, ".artifactory-ha").getAbsolutePath());
        properties.put(HaNodeProperties.PROP_CONTEXT_URL, "localhost");
        // TODO: Test block when conf as slave
        properties.put(HaNodeProperties.PROP_PRIMARY, "true");
        return properties;
    }

    public static Properties createArtifactoryProperties(ArtifactoryVersion version) {
        Properties properties = new Properties();
        properties.put(artifactoryVersion.getPropertyName(), version.getValue());
        properties.put(artifactoryRevision.getPropertyName(), "" + version.getRevision());
        properties.put(artifactoryTimestamp.getPropertyName(), "" + DateTimeUtils.currentTimeMillis());
        return properties;
    }

    public static File createHaBackupFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File backupDir = new File(artHaDir, "ha-backup");
        FileUtils.forceMkdir(backupDir);
        File backupFile = new File(backupDir, "backup.txt");
        FileUtils.touch(backupFile);
        return backupFile;
    }

    public static File createArtifactorySystemPropertiesFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File artifactorySystemPropertiesFile = new File(etcDir, "artifactory.system.properties");
        FileUtils.writeStringToFile(artifactorySystemPropertiesFile, "test=just_test");
        return artifactorySystemPropertiesFile;
    }

    public static File createBinaryStoreXmlFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File binaryStoreXml = new File(etcDir, "binarystore.xml");
        FileUtils.writeStringToFile(binaryStoreXml, "<config version=\"2\">\n" +
                "    <chain template=\"file-system\"/>\n" +
                "</config>");
        return binaryStoreXml;
    }

    public static File createMimeTypeXmlFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File mimeTypeXml = new File(etcDir, "mimetypes.xml");
        String mimeTypesString = ResourceUtils.getResourceAsString("/converters/templates/home/3.1.0/mimetypes.xml");
        FileUtils.writeStringToFile(mimeTypeXml, mimeTypesString);
        return mimeTypeXml;
    }

    public static File createStoragePropertiesXmlFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File storageProperties = new File(etcDir, "storage.properties");
        FileUtils.writeStringToFile(storageProperties,
                "type=derby\n" +
                        "url=jdbc:derby:{db.home};create=true\n" +
                        "driver=org.apache.derby.jdbc.EmbeddedDriver\n" +
                        "username=tester\n" +
                        "password=password\n" +
                        "binary.provider.s3.bucket.name=data1212\n" +
                        "binary.provider.type=s3Old\n" +
                        "\n" +
                        "## S3 identity\n" +
                        "binary.provider.s3.identity=AKIAIQKSNWN5FBM7L6DQ\n" +
                        "\n" +
                        "## S3 credential\n" +
                        "binary.provider.s3.credential=orzT0nSPFIkAfNeSetIkb7Arq2luP9KYcOZISUR+\n" +
                        "\n" +
                        "## S3 endpoint\n" +
                        "binary.provider.s3.endpoint= http://s3.amazonaws.com\n" +
                        "\n" +
                        "## Wait for Hazelcast time\n" +
                        "binary.provider.eventually.persisted.wait.hazelcast.time=5\n" +
                        "\n" +
                        "## Wait time between file scan\n" +
                        "binary.provider.eventually.dispatcher.sleep.time=9");
        return storageProperties;
    }

    public static File createHaPluginsFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File pluginsDir = new File(artHaDir, "ha-etc/plugins");
        FileUtils.forceMkdir(pluginsDir);
        File pluginFile = new File(pluginsDir, "plugin.groovy");
        FileUtils.touch(pluginFile);
        return pluginFile;
    }

    public static File createHaUiFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File uiDir = new File(artHaDir, "ha-etc/ui");
        FileUtils.forceMkdir(uiDir);
        File uiFile = new File(uiDir, "plugin.groovy");
        FileUtils.touch(uiFile);
        return uiFile;
    }

    public static void createSshKeys(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File privateKey = new File(artHaDir, "ha-etc/ssh/artifactory.ssh.private");
        FileUtils.touch(privateKey);
        File publicKey = new File(artHaDir, "ha-etc/ssh/artifactory.ssh.public");
        FileUtils.touch(publicKey);
    }

}
