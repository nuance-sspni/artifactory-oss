package org.artifactory.environment.converter.shared.version.v1;

import com.google.common.collect.Lists;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.BlobWrapper;
import org.artifactory.common.config.db.TemporaryDBChannel;
import org.artifactory.util.CommonDbUtils;
import org.artifactory.util.ResourceUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author gidis
 */
public class NoNfsDbConfigsTableConverter extends NoNfsBasicEnvironmentConverter {
    private static final Logger log = LoggerFactory.getLogger(NoNfsDbConfigsTableConverter.class);

    //Illegal path characters - covers all platforms (albeit very restrictive) but we map actual config names so its ok.
    private final static String ILLEGAL_PATH_CHARACTERS = "[^\\w.-]";
    private final static String MAP_FILE_NAME = "configName_to_fileName.map";

    private TemporaryDBChannel dbChannel;
    private File conversionTempDir;

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return isUpgradeTo5x(source, target);
    }

    @Override
    protected void doConvert(ArtifactoryHome artifactoryHome, File clusterHomeDir) {
        this.conversionTempDir = new File(artifactoryHome.getDataDir(), "db_conversion_temp");
        try {
            setupDbChannel(artifactoryHome);
            if (!shouldRun()) {
                return;
            }
            log.info("Starting configs table v5.0 schema conversion");
            List<ConfigInfo> configs = getExistingConfigsFromDb();
            createFilesystemBackups(configs);
            runDbConversion();
            copyConfigsBackToDB(configs);
            deleteBackups();
            log.info("Finished configs table v5.0 schema conversion");
        } catch (Exception e) {
            throw new RuntimeException("Couldn't convert configs table: " + e.getMessage(), e);
        } finally {
            if (dbChannel != null) {
                dbChannel.close();
            }
        }
    }

    private List<ConfigInfo> getExistingConfigsFromDb() throws SQLException, IOException {
        List<ConfigInfo> configs = Lists.newArrayList();
        try (ResultSet resultSet = dbChannel.executeSelect("SELECT * FROM configs")) {
            while (resultSet.next()) {
                String name = resultSet.getString("config_name");
                InputStream dbBlobStream = null;
                InputStream bufferedBinaryStream = null;
                try {
                    dbBlobStream = resultSet.getBinaryStream("data");
                    bufferedBinaryStream = org.apache.commons.io.IOUtils.toBufferedInputStream(dbBlobStream);
                    byte[] data = IOUtils.toByteArray(bufferedBinaryStream);
                    configs.add(new ConfigInfo(name, data));
                } finally {
                    IOUtils.closeQuietly(dbBlobStream);
                    IOUtils.closeQuietly(bufferedBinaryStream);
                }
            }
        }
        return configs;
    }

    /**
     * Backups are kept only in the event the db conversion failed, due to the fact that the DB can hold configs that
     * names that conflict with OS-specific pathname restrictions a map file is also kept to map
     * actual_config_name -> temp_filename.
     */
    private void createFilesystemBackups(List<ConfigInfo> configs) throws IOException {
        if (conversionTempDir.exists() && conversionTempDir.list()!=null && conversionTempDir.list().length>0 ) {
            String tempBackupDirName = conversionTempDir.getAbsolutePath() + "." + System.currentTimeMillis();
            log.warn("Found existing backup dir for config files {}. Renaming it to {}",
                    conversionTempDir.getAbsolutePath(), tempBackupDirName);
            // If we fail on the move let the exception propagate, don't want to overwrite any backups...
            FileUtils.moveDirectory(conversionTempDir, new File(tempBackupDirName));
        }
        File mapFile = new File(conversionTempDir, MAP_FILE_NAME);
        for (ConfigInfo config : configs) {
            File configFile = new File(conversionTempDir, config.name.replaceAll(ILLEGAL_PATH_CHARACTERS, "_"));
            Files.createFile(configFile.toPath());
            try (FileOutputStream outputStream = new FileOutputStream(configFile)) {
                outputStream.write(config.data);
            } catch (IOException ioe) {
                log.error("Failed to create a filesystem backup for config {} from the DB", config.name);
                throw ioe;
            }
            try (FileWriter mapFileWriter = new FileWriter(mapFile, true)) {
                mapFileWriter.write(config.name + " -> " + configFile.getAbsolutePath() + "\n");
            } catch (Exception e) {
                // Don't stop the conversion for failing to write the map file, put the mapping in the log instead.
                String err = "Can't write db configs temp map file: ";
                log.debug(err, e);
                log.warn(err + "{}. temp file for config {} was created under file {}", e.getMessage(),
                        config.name, configFile.getAbsolutePath());
            }
        }
    }

    private void runDbConversion() throws IOException, SQLException {
        String dbTypeName = dbChannel.getDbType().toString();
        String resourcePath = "/conversion/" + dbTypeName + "/" + dbTypeName + "_v500a.sql";
        InputStream resource = ResourceUtils.getResource(resourcePath);
        if (resource == null) {
            throw new IOException("Database DDL resource not found at: '" + resourcePath + "'");
        }
        Connection connection = dbChannel.getConnection();
        CommonDbUtils.executeSqlStream(connection, resource);
    }

    private void copyConfigsBackToDB(List<ConfigInfo> configs) {
        for (ConfigInfo config : configs) {
            BlobWrapper blobWrapper = new BlobWrapper(new ByteArrayInputStream(config.data), config.data.length);
            int result;
            String err = "Failed to upload config " + config.name + " into DB";
            try {
                result = dbChannel.executeUpdate("INSERT INTO configs values(?,?,?)", config.name,
                        System.currentTimeMillis(), blobWrapper);
                if (result != 1) {
                    log.error(err);
                    throw new RuntimeException(err);
                }
            } catch (SQLException e) {
                log.error(err);
                throw new RuntimeException(err);
            }
        }
    }

    private void deleteBackups() {
        log.info("Clearing backup folder at {}", conversionTempDir.getAbsolutePath());
        try {
            FileUtils.deleteDirectory(conversionTempDir);
        } catch (IOException e) {
            log.error("Unable to clean temp backup dir at {} : {}", conversionTempDir.getAbsolutePath(), e.getMessage());
            log.debug(e.getMessage(), e);
        }
    }

    private void setupDbChannel(ArtifactoryHome artifactoryHome) throws IOException {
        deleteBackups(); // Just in case
        FileUtils.forceMkdir(conversionTempDir);
        ArtifactoryDbProperties dbProperties = new ArtifactoryDbProperties(artifactoryHome);
        dbChannel = new TemporaryDBChannel(dbProperties);
    }

    private boolean shouldRun() {
        try (ResultSet resultSet = dbChannel.executeSelect("SELECT * FROM configs")) {
            try {
                log.debug("configs table exists in db, testing for existence of last_modified column");
                resultSet.findColumn("last_modified");
            } catch (SQLException sqe) {
                log.debug("last_modified column does not exist in configs table, conversion will run.");
                return true;
            }
        } catch (Exception e) {
            //Table does not exist, this is a new installation and the table will be created during service conversion
            //Sync events are collected in the special db channel and are released once db is available.
            log.debug("configs table does not exist in db, conversion will not run.");
            return false;
        }
        log.debug("last_modified column already exists in configs table, conversion will not run.");
        return false;
    }

    private class ConfigInfo {

        final String name;
        final byte[] data;

        ConfigInfo(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}
