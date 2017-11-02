package org.artifactory.common.config.wrappers;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.ConfigurationManager;
import org.artifactory.common.config.FileEventType;
import org.artifactory.common.config.broadcast.BroadcastChannel;
import org.artifactory.common.config.db.ConfigUpdateException;
import org.artifactory.common.config.db.ConfigsDataAccessObject;
import org.artifactory.common.config.utils.ConfigWithTimestamp;
import org.artifactory.common.config.utils.FileConfigWithTimestamp;
import org.artifactory.common.ha.HaNodeProperties;
import org.jfrog.security.file.SecurityFolderHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;

import static org.artifactory.common.config.FileEventType.*;

/**
 * @author gidis
 */
public final class ConfigWrapperImpl implements ConfigWrapper {

    private final FileConfigWithTimestamp fileConfigWithTimestamp;
    private File file;
    private String name;
    private ConfigurationManager configurationManager;
    private String defaultContent;
    private boolean mandatoryConfig;
    private boolean encrypted;
    private Set<PosixFilePermission> requiredPermissions;
    private ArtifactoryHome home;
    private int retry;

    public ConfigWrapperImpl(File file, String name, ConfigurationManager configurationManager, String defaultContent,
            boolean mandatoryConfig, boolean encrypted, Set<PosixFilePermission> requiredPermissions, ArtifactoryHome home)
            throws IOException, SQLException {
        this.file = file;
        this.name = name;
        this.configurationManager = configurationManager;
        this.defaultContent = defaultContent;
        this.mandatoryConfig = mandatoryConfig;
        this.encrypted = encrypted;
        this.home = home;
        this.fileConfigWithTimestamp = new FileConfigWithTimestamp(file, configurationManager);
        this.retry = ConstantValues.configurationManagerRetryAmount.getInt(home);
        this.requiredPermissions = requiredPermissions;
        initialize();
    }

    /**
     * Synchronize file between database and the files in the cluster nodes
     */
    public void initialize() throws SQLException, IOException {
        // Initialize ignores changes to timestamp.
        changeFileTimestampIfNeeded();
        // If file exist just du update if needed
        HaNodeProperties haNodeProperties = home.getHaNodeProperties();
        if (file.exists() && (haNodeProperties == null || haNodeProperties.isPrimary())) {
            modifiedWithRetry(retry, MODIFY, false, true);
        } else {
            // If file doesn't exist then check if we need to force  delete from the database
            if (forceDelete()) {
                return;
            }
            // File doesn't exist and we do not want to delete it from DB, so lets try to bring it from DB
            if (configurationManager.isConfigTableExist()) {
                ConfigWithTimestamp dbConfig = getConfigsDataAccesObject().getConfig(name, encrypted, home);
                if (dbConfig == null) {
                    // File doesn't exist in DB so lets save the default one if needed
                    ensureConfigurationFileExist();
                } else {
                    // File exist in DB so lets bring it from DB
                    if (!this.file.getParentFile().exists()) {
                        boolean success = this.file.getParentFile().mkdirs();
                        if (!success) {
                            configurationManager.getLogChannel().debug("Failed to create directory for: " +
                                    file.getParentFile().getAbsolutePath());
                        }
                    }
                    ConfigWithTimestamp dbConfigHolder = getConfigsDataAccesObject().getConfig(name, encrypted, home);
                    if (!file.exists() || fileConfigWithTimestamp.isBefore(dbConfigHolder)) {
                        dBToFile();
                    }
                }
            } else {
                ensureConfigurationFileExist();
            }
            // Last but not least, ensure permissions
            ensureFilePermissions();
        }
    }

    @Override
    public void create() throws IOException, SQLException {
        modifiedWithRetry(retry, CREATE, false, true);
    }

    @Override
    public void modified() throws IOException, SQLException {
        modifiedWithRetry(retry, MODIFY, false, true);
    }

    @Override
    public void remove() throws SQLException {
        if (mandatoryConfig) {
            configurationManager.getLogChannel().warn("Mandatory file " + name + " was removed externally on node "
                    + home.getHaAwareHostId() + ", skipping deletion form DB.");
            return;
        }
        logAction(true, DELETE, false);
        if (getConfigsDataAccesObject().hasConfig(name)) {
            boolean removed = getConfigsDataAccesObject().removeConfig(name);
            if (removed) {
                boolean success = getBroadcastChannel().notifyConfigChanged(name, DELETE);
                if (!success) {
                    throw new RuntimeException("Failed to notify other nodes about a change in " + getFile().getAbsolutePath());
                }
            } else {
                configurationManager.getLogChannel().debug("File already deleted, skipping propagation");
            }
        }
        logAction(false, DELETE, false);
    }

    @Override
    public void remoteCreate() throws IOException, SQLException {
        modifiedWithRetry(retry, CREATE, true, false);
    }

    @Override
    public void remoteModified() throws IOException, SQLException {
        modifiedWithRetry(retry, MODIFY, true, false);
    }

    @Override
    public void remoteRemove() throws IOException, SQLException {
        if (mandatoryConfig) {
            configurationManager.getLogChannel().warn("Mandatory file " + name + " was removed remotely on node "
                    + home.getHaAwareHostId() + ", skipping deletion form DB and file system");
            return;
        }
        if (!getConfigsDataAccesObject().hasConfig(name)) {
            logAction(true, DELETE, true);
            boolean isDeleted = (!getFile().exists() || getFile().delete());
            if (!isDeleted) {
                throw new RuntimeException("Failed to remove config: " + getFile().getAbsolutePath());
            }
            logAction(false, DELETE, true);
        } else {
            modifiedWithRetry(retry, DELETE, true, false);
        }

    }

    private boolean forceDelete() {
        File forceDelete = new File(this.file.getAbsolutePath() + ".force.delete");
        if (forceDelete.exists()) {
            boolean isDeleted = forceDelete.delete();
            if (!isDeleted) {
                throw new RuntimeException("Failed to remove config: " + forceDelete.getAbsolutePath());
            }
            configurationManager.getConfigsDao().removeConfig(name);
            return true;
        }
        return false;
    }

    private void ensureConfigurationFileExist() {
        if (defaultContent == null && mandatoryConfig) {
            throw new RuntimeException("Both file and and db config doesn't exist for config:" + file.getAbsolutePath());
        }
        if (defaultContent != null) {
            try {
                //Copy from default
                URL url = ArtifactoryHome.class.getResource(defaultContent);
                if (url == null) {
                    throw new RuntimeException("Could not read classpath resource '" + defaultContent + "'.");
                }
                FileUtils.copyURLToFile(url, getFile());
                boolean success = getFile().setLastModified(System.currentTimeMillis());
                if (!success) {
                    throw new RuntimeException(
                            "Failed to modify the Last modification time for file: " + getFile().getAbsolutePath());
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not create the default '" + defaultContent + "' at '"
                        + getFile().getAbsolutePath() + "'.", e);
            }
        }
    }

    private void modifiedWithRetry(int retry, FileEventType action, boolean remote, boolean propagateEvent) throws IOException, SQLException {
        try {
            modifyInternal(action, remote, propagateEvent);
        } catch (ConfigUpdateException e) {
            if (retry > 0) {
                modifiedWithRetry(retry - 1, action, remote, propagateEvent);
            } else {
                throw e;
            }
        }

    }

    private void modifyInternal(FileEventType action, boolean remote, boolean propagateEvent) throws SQLException, IOException {
        if (changeFileTimestampIfNeeded()) {
            // timestamp changed on file, abort action and let the next filesystem trigger redo it.
            return;
        }
        Long dbConfigTimestamp = getConfigsDataAccesObject().getConfigTimestamp(name);
        if (fileConfigWithTimestamp.isAfter(dbConfigTimestamp)) {
            // currently held file newer than db - overwrite db
            logAction(true, action, remote);
            fileToDb();
            ensurePermissionsAndNotifyAll(action, remote, propagateEvent);
        } else if (fileConfigWithTimestamp.isBefore(dbConfigTimestamp)) {
            // currently held file older than db - overwrite fs
            logAction(true, action, remote);
            dBToFile();
            ensurePermissionsAndNotifyAll(action, remote, propagateEvent);
        } else {
            // else both timestamps (db and file) are equals so change in configs does nothing
            ensurePermissionsAndNotifyAll(action, remote, propagateEvent);
            configurationManager.getLogChannel().debug("Received file changed event but file is same as in the DB");
        }
    }

    private void ensurePermissionsAndNotifyAll(FileEventType action, boolean remote, boolean propagateEvent) {
        ensureFilePermissions();
        if (propagateEvent) {
            boolean success = getBroadcastChannel().notifyConfigChanged(name, action);
            if (!success) {
                throw new RuntimeException(
                        "Failed to notify other nodes about a change in " + getFile().getAbsolutePath());
            }
        }
        logAction(false, action, remote);
    }

    /**
     * Makes sure file has same permissions as intended when created
     */
    private void ensureFilePermissions() {
        if (file == null || !file.exists() || requiredPermissions == null || requiredPermissions.isEmpty()) {
            return;
        }
        String targetPermissions = PosixFilePermissions.toString(requiredPermissions);
        try {
            String currentPermissions = PosixFilePermissions.toString(SecurityFolderHelper
                    .getFilePermissionsOrDefault(file.toPath()));
            if (!Objects.equals(currentPermissions, targetPermissions)) {
                SecurityFolderHelper.setPermissionsOnSecurityFile(file.toPath(), requiredPermissions);
            }
        } catch (IOException e) {
            configurationManager.getLogChannel().error("Failed to set file permissions '" + targetPermissions
                    + "' on config " + file.getAbsolutePath(), e);
        }
    }

    private void fileToDb() throws SQLException {
        if (file.exists()) {
            getConfigsDataAccesObject().setConfig(name, fileConfigWithTimestamp, encrypted, home);
        } else {
            throw new RuntimeException(String.format("Couldn't copy the configuration %s from file system" +
                    " to to database due to config is not in file system.", file.getAbsoluteFile()));
        }
    }

    private void dBToFile() throws IOException {
        ConfigWithTimestamp dbConfigHolder = getConfigsDataAccesObject().getConfig(name, encrypted, home);
        if (dbConfigHolder != null) {
            FileOutputStream outputStream = new FileOutputStream(file);
            try (InputStream binaryStream = dbConfigHolder.getBinaryStream()) {
                IOUtils.copy(binaryStream, outputStream);
            }
            boolean success = file
                    .setLastModified(configurationManager.getDeNormalizedTime(dbConfigHolder.getTimestamp()));
            if (!success) {
                throw new RuntimeException(
                        String.format("Failed to update last modification for config %s .", file.getAbsoluteFile()));
            }
        } else {
            throw new RuntimeException(String.format("Couldn't copy the configuration %s from database to file system "
                    + "due to config is not in database", name));
        }
    }

    private void logAction(boolean begin, FileEventType action, boolean remote) {
        if (begin) {
            infoLogAction(action, remote);
        }
        debugLogAction(begin, action, remote);
    }

    private void debugLogAction(boolean begin, FileEventType action, boolean remote) {
        configurationManager.getLogChannel().debug(
                (begin ? "Start" : "End") + " " + action
                        + " on " + (remote ? "remote" : "local")
                        + " server='" + home.getHaAwareHostId() + "'" +
                        " config='" + name + "'");
    }

    private void infoLogAction(FileEventType action, boolean remote) {
        configurationManager.getLogChannel().info("[Node ID: " + home.getHaAwareHostId() + "] detected "
                + (remote ? "remote " : "local ") + action + " for config '" + name + "'");
    }

    public String getName() {
        return name;
    }

    public File getFile() {
        return file;
    }

    private ConfigsDataAccessObject getConfigsDataAccesObject() {
        return configurationManager.getConfigsDao();
    }

    private BroadcastChannel getBroadcastChannel() {
        return configurationManager.getBroadcastChannel();
    }

    /**
     * Files that have modified dates that are after this instance's current date will be modified to reflect the
     * current date, in order to avoid cases where a file with a distant future modified date is pushed into the
     * database and inhibits all changes on it until that time is reached (or manual db intervention).
     *
     * @return true if a change was made to the file's modified date - to signify the current action should abort
     * (the modified timestamp will trigger another event from the filesystem).
     */
    private boolean changeFileTimestampIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (file != null && file.exists() && (file.lastModified() > currentTime)) {
            configurationManager.getLogChannel().warn("Detected a change on file " + file.getAbsolutePath() +
                    " with a timestamp later than the system's current time.  The file's timestamp will be set as " +
                    "the current time.");
            if (!file.setLastModified(currentTime)) {
                throw new RuntimeException("Failed to modify the Last modification time for file: " + file.getAbsolutePath());
            }
            return true;
        }
        return false;
    }
}
