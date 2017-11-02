package org.artifactory.common.config.db;

import com.google.common.collect.Lists;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.log.LogChannel;
import org.artifactory.common.config.utils.ConfigWithTimestamp;
import org.artifactory.common.config.utils.DBConfigWithTimestamp;
import org.artifactory.common.config.utils.FileConfigWithTimestamp;
import org.artifactory.common.crypto.ArtifactoryEncryptionKeyFileFilter;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author gidis
 */
public class ConfigsDataAccessObject {

    private static final String COLUMN_LAST_MODIFIED = "last_modified";
    private static final String COLUMN_CONFIG_NAME = "config_name";
    private static final String COLUMN_DATA = "data";
    private static final String CONFIG_EXISTS = "SELECT COUNT(1) FROM configs WHERE config_name = ?";
    private static final String GET_CONFIG = "SELECT * FROM configs WHERE config_name = ?";
    private static final String GET_CONFIGS_BY_PREFIX = "SELECT * FROM configs WHERE config_name LIKE ?";
    private static final String GET_CONFIG_TIMESTAMP = "SELECT last_modified FROM configs WHERE config_name = ?";
    private static final String INSERT_CONFIG = "INSERT INTO configs VALUES(?, ?, ?)";
    private static final String UPDATE_CONFIG = "UPDATE configs SET last_modified = ?, data = ? WHERE config_name = ?";

    private DbChannel dbChannel;
    private LogChannel logChannel;
    private final ArtifactoryEncryptionKeyFileFilter encryptionKeyFilter;

    public ConfigsDataAccessObject(ArtifactoryHome home) {
        this.encryptionKeyFilter =
                new ArtifactoryEncryptionKeyFileFilter(ConstantValues.securityMasterKeyLocation.getString(home));
    }

    public boolean isConfigsTableExist() {
        //noinspection EmptyTryBlock, unused
        try (ResultSet resultSet = dbChannel.executeSelect("SELECT * FROM configs")) {
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public Long getDBTime() {
        try {
            String query;
            DbType dbType = dbChannel.getDbType();
            switch (dbType) {
                case DERBY: {
                    query = "SELECT CURRENT_TIMESTAMP FROM SYSIBM.SYSDUMMY1";
                    break;
                }
                case MYSQL: {
                    query = "SELECT CURRENT_TIMESTAMP";
                    break;
                }
                case ORACLE: {
                    query = "SELECT CURRENT_TIMESTAMP FROM DUAL";
                    break;
                }
                case MSSQL: {
                    query = "SELECT CURRENT_TIMESTAMP";
                    break;
                }
                case POSTGRESQL: {
                    query = "SELECT CURRENT_TIMESTAMP";
                    break;
                }
                default:
                    throw new RuntimeException("Invalid database type: " + dbType + " expecting one of the following: "
                                    + " DERBY, MYSQL, ORACLE, MSSQL, POSTGRESQL");
            }
            try (ResultSet resultSet = dbChannel.executeSelect(query)) {
                if (resultSet.next()) {
                    return resultSet.getTimestamp(1).getTime();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch current time from the database", e);
        }
        return null;
    }

    public boolean hasConfig(String name) throws SQLException {
        try (ResultSet resultSet = dbChannel.executeSelect(CONFIG_EXISTS, name)) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    public Long getConfigTimestamp(String name) {
        try (ResultSet resultSet = dbChannel.executeSelect(GET_CONFIG_TIMESTAMP, name)) {
            if (resultSet.next()) {
                return resultSet.getLong(COLUMN_LAST_MODIFIED);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve config timestamp from database for: " + name, e);
        }
        return null;
    }

    public ConfigWithTimestamp getConfig(String name, boolean encrypted, ArtifactoryHome home) {
        try (ResultSet resultSet = dbChannel.executeSelect(GET_CONFIG, name)) {
            if (resultSet.next()) {
                long timestamp = resultSet.getLong(COLUMN_LAST_MODIFIED);
                String configName = resultSet.getString(COLUMN_CONFIG_NAME);
                byte[] content = getConfigContent(resultSet, encrypted, home);
                return new DBConfigWithTimestamp(content, timestamp, configName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve config from database for: " + name, e);
        }
        return null;
    }

    /**
     * Get all configs stored with {@param prefix} in their name column (useful for getting contents of folder)
     */
    public List<DBConfigWithTimestamp> getConfigs(String prefix, boolean encrypted, ArtifactoryHome home) {
        List<DBConfigWithTimestamp> result = Lists.newArrayList();
        EncryptionWrapper masterWrapper = encrypted ? getEncryptionWrapper(home) : null;
        try (ResultSet resultSet = dbChannel.executeSelect(GET_CONFIGS_BY_PREFIX, prefix + "%")) {
            while (resultSet.next()) {
                long timestamp = resultSet.getLong(COLUMN_LAST_MODIFIED);
                String name = resultSet.getString(COLUMN_CONFIG_NAME);
                byte[] content = this.getConfigContent(resultSet, masterWrapper);
                result.add(new DBConfigWithTimestamp(content, timestamp, name));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch configs for prefix: " + prefix, e);
        }
        return result;
    }

    public void setConfig(String name, FileConfigWithTimestamp configWithTime, boolean encrypted, ArtifactoryHome home) {
        BlobWrapper blobWrapper;
        ByteArrayInputStream streamToDb = null;
        long timestamp = configWithTime.getTimestamp();
        int updatedRows;
        try {
            byte[] configBytes;
            try (InputStream stream = configWithTime.getBinaryStream()) {
                configBytes = IOUtils.toByteArray(stream);
            }
            if (encrypted) {
                configBytes = encryptConfig(configBytes, home);
            }
            streamToDb = new ByteArrayInputStream(configBytes);
            blobWrapper = new BlobWrapper(streamToDb, configBytes.length);
            if (hasConfig(name)) {
                logChannel.debug("Updating database with config changes for " + name);
                updatedRows = dbChannel.executeUpdate(UPDATE_CONFIG, timestamp, blobWrapper, name);
            } else {
                try {
                    logChannel.debug("Creating database config changes for " + name);
                    updatedRows = dbChannel.executeUpdate(INSERT_CONFIG, name, timestamp, blobWrapper);
                } catch (SQLException e) {
                    //Insert failed, assuming a unique constraint violation due to a race, so trying to update instead.
                    logChannel.debug("Insert failed (optimistic), updating database config changes for " + name);
                    streamToDb = new ByteArrayInputStream(configBytes);
                    blobWrapper = new BlobWrapper(streamToDb, configBytes.length);
                    updatedRows = dbChannel.executeUpdate(UPDATE_CONFIG, timestamp, blobWrapper, name);
                }
            }
        } catch (SQLException se) {
            throw new ConfigUpdateException("Failed to insert/update config '" + name + "' to database.", se);
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert/update config '" + name + "' to database.", e);
        } finally {
            IOUtils.closeQuietly(streamToDb);
        }
        if (updatedRows <= 0) {
            logChannel.debug("Update/Insert config '" + name + "' finished without an exception and without changes to the database.");
        }
    }

    public boolean removeConfig(String name) {
        try {
            return dbChannel.executeUpdate("DELETE from configs WHERE config_name = ? ", name) > 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove config for name: " + name, e);
        }
    }

    public DbChannel getDbChannel() {
        return dbChannel;
    }

    public void setDbChannel(DbChannel dbChannel) {
        this.dbChannel = dbChannel;
    }

    public void setLogChannel(LogChannel logChannel) {
        this.logChannel = logChannel;
    }

    private byte[] getConfigContent(ResultSet resultSet, boolean encrypted, ArtifactoryHome home) throws IOException, SQLException {
        EncryptionWrapper masterWrapper = encrypted ? getEncryptionWrapper(home) : null;
        return getConfigContent(resultSet, masterWrapper);
    }

    private byte[] getConfigContent(ResultSet resultSet, @Nullable EncryptionWrapper masterWrapper) throws IOException, SQLException {
        byte[] content;
        try (InputStream data = resultSet.getBinaryStream(COLUMN_DATA)) {
            content = IOUtils.toByteArray(data);
            //TODO [by dan]: throw if its empty?
        }
        return decryptConfigIfNeeded(content, masterWrapper);
    }

    private byte[] encryptConfig(byte[] configBytes, ArtifactoryHome home) throws IOException {
        byte[] encrypted = configBytes;
        EncryptionWrapper masterWrapper = getEncryptionWrapper(home);
        if (masterWrapper != null) {
            encrypted = masterWrapper.encryptIfNeeded(new String(configBytes, Charsets.UTF_8)).getBytes(Charsets.UTF_8);
        }
        return encrypted;
    }

    private byte[] decryptConfigIfNeeded(@Nullable byte[] content, @Nullable EncryptionWrapper masterWrapper) throws IOException {
        byte[] decrypted = content;
        if (content != null && masterWrapper != null) {
            decrypted = masterWrapper.decryptIfNeeded(new String(content, Charsets.UTF_8))
                    .getDecryptedData().getBytes(Charsets.UTF_8);
        }
        return decrypted;
    }

    private EncryptionWrapper getEncryptionWrapper(ArtifactoryHome home) {
        File communicationKeyFile = home.getCommunicationKeyFile();
        EncryptionWrapper masterWrapper = null;
        if (communicationKeyFile.exists()) {
            masterWrapper = EncryptionWrapperFactory.createMasterWrapper(communicationKeyFile,
                    communicationKeyFile.getParentFile(), 3, encryptionKeyFilter);

        }
        return masterWrapper;
    }
}
