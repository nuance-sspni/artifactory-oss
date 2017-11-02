package org.artifactory.common.config;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.broadcast.BroadcastChannel;
import org.artifactory.common.config.broadcast.TemporaryBroadcastChannelImpl;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.ConfigsDataAccessObject;
import org.artifactory.common.config.db.DbChannel;
import org.artifactory.common.config.db.TemporaryDBChannel;
import org.artifactory.common.config.log.LogChannel;
import org.artifactory.common.config.log.PermanentLogChannel;
import org.artifactory.common.config.log.TemporaryLogChannel;
import org.artifactory.common.config.utils.DBConfigWithTimestamp;
import org.artifactory.common.config.watch.FileWatchingManager;
import org.artifactory.common.config.wrappers.ConfigWrapper;
import org.artifactory.common.config.wrappers.ConfigWrapperImpl;
import org.jfrog.client.util.Pair;
import org.jfrog.client.util.PathUtils;
import org.jfrog.security.file.SecurityFolderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.common.ArtifactoryHome.*;

/**
 * @author gidis
 *         The class is responsable to synchronize shared files between the cluster nodes
 */
public class ConfigurationManagerImpl implements ConfigurationManager {

    private static final ArrayList<String> SECURITY_EXCLUDED_ENTRIES = Lists.newArrayList(
            "etc/security/binstore", "etc/security/" + COMMUNICATION_KEY_FILE_NAME, "etc/security/" + COMMUNICATION_TOKEN_FILE_NAME);
    private final FileWatchingManager javaFileWatcher;
    private long timeGap;
    private ArtifactoryHome home;
    private ConfigsDataAccessObject configsDao;
    private Map<String, ConfigWrapper> sharedConfigsByFile;
    private BroadcastChannel broadcastChannel;
    private boolean configTableExist;
    private LogChannel logChannel;

    /**
     * Initialize JavaFilesWatcher and register the shared files in order to receive events on file changes and then
     * synchronize the changes with the database and the other nodes
     */
    public ConfigurationManagerImpl(ArtifactoryHome home) {
        this.sharedConfigsByFile = Maps.newHashMap();
        this.home = home;
        this.javaFileWatcher = new FileWatchingManager(this);
        // Init LogChannel
        this.logChannel = new TemporaryLogChannel();
    }

    public static void createDefaultFiles(ArtifactoryHome home) {
        //  Ensure mimetypes file exist
        ensureConfigurationFileExist("/META-INF/default/" + MIME_TYPES_FILE_NAME, home.getMimeTypesFile());
        // Ensure artifactory system properties exit
        ensureConfigurationFileExist("/META-INF/default/" + ARTIFACTORY_SYSTEM_PROPERTIES_FILE,
                home.getArtifactorySystemPropertiesFile());
        // Ensure binarystore.xml exit
        ensureConfigurationFileExist("/META-INF/default/" + BINARY_STORE_FILE_NAME, home.getBinaryStoreXmlFile());
        // Ensure logback.xml exists
        ensureConfigurationFileExist("/META-INF/default/" + LOGBACK_CONFIG_FILE_NAME, home.getLogbackConfig());
    }

    public static void ensureConfigurationFileExist(String defaultContent, File file) {
        try {
            if (file.exists()) {
                return;
            }
            //Copy from default
            URL url = ArtifactoryHome.class.getResource(defaultContent);
            if (url == null) {
                throw new RuntimeException("Could not read classpath resource '" + defaultContent + "'.");
            }
            FileUtils.copyURLToFile(url, file);
            boolean success = file.setLastModified(System.currentTimeMillis());
            if (!success) {
                throw new RuntimeException("Failed to modify the Last modification time for file: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create the default '" + defaultContent + "' at '"
                    + file.getAbsolutePath() + "'.", e);
        }
    }

    @Override
    public void initDbChannels() {
        TemporaryDBChannel dbChannel = getTemporaryDBChannel();
        try {
            // Create data access object with access to DB
            this.configsDao = new ConfigsDataAccessObject(home);
            this.configsDao.setDbChannel(dbChannel);
            this.configsDao.setLogChannel(logChannel);
            // Create temporary broadcast channel which will collect all the events and distribute them
            // once the permanent broadcast channel is ready
            this.broadcastChannel = new TemporaryBroadcastChannelImpl();
            this.configTableExist = getConfigsDao().isConfigsTableExist();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't init the Configuration manager.", e);
        }
    }

    private TemporaryDBChannel getTemporaryDBChannel() {
        File systemProps = home.getArtifactorySystemPropertiesFile();
        boolean systemPropsOriginallyPresent = false;
        if (systemProps.exists()) {
            systemPropsOriginallyPresent = true;
        } else {
            ensureConfigurationFileExist("/META-INF/default/artifactory.system.properties", systemProps);
        }
        //Temporarily load system.props into home for the EncryptionWrapper in the next step
        home.initArtifactorySystemProperties();
        // Temp dbProperties for the temp channel, the actual dbProperties are loaded later by ArtifactoryHome
        ArtifactoryDbProperties dbProperties = new ArtifactoryDbProperties(home);
        // Init connection with database
        TemporaryDBChannel dbChannel = new TemporaryDBChannel(dbProperties);
        if (!systemPropsOriginallyPresent) {
            // If system.props was not present initially remove the file now, reload will take care of it later.
            try {
                Files.deleteIfExists(systemProps.toPath());
            } catch (IOException e) {
                logChannel.warn("Failed to remove temporary artifactory.system.properties file from location: "
                        + systemProps.getAbsolutePath());
            }
        }
        return dbChannel;
    }

    @Override
    public void startSync() {
        try {
            if (configTableExist) {
                startFileSync();
            }
            createDefaultFiles(home);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start file sync to db: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        if (broadcastChannel != null) {
            broadcastChannel.destroy();
        }
        javaFileWatcher.destroy();
        if (configsDao != null) {
            configsDao.getDbChannel().close();
        }
    }

    @Override
    public void initDbProperties() {
        initDbProperties(home);
    }

    /**
     * Used for test purposes, leave public
     */
    public static void initDbProperties(ArtifactoryHome home) {
        ensureConfigurationFileExist("/META-INF/default/db/derby.properties", home.getDBPropertiesFile());
        home.initDBProperties();
    }

    private void startFileSync() throws IOException, SQLException {
        // Normalize time
        timeGap = System.currentTimeMillis() - getConfigsDao().getDBTime();
        // Register for changes in the following directories
        javaFileWatcher.registerDirectoryListener(home.getEtcDir());
        // Register files only in DB environment
        registerSharedFiles();
    }

    /**
     * The method register the shared files in the JavaFilesWatcher to receive file change on the files
     */
    private void registerSharedFiles() throws IOException, SQLException {
        // Register db properties
        registerConfig(home.getDBPropertiesFile(), "db.properties", null, true, false);
        // Register artifactory system properties
        registerConfig(home.getArtifactorySystemPropertiesFile(), "artifactory.system.properties",
                "/META-INF/default/" + ARTIFACTORY_SYSTEM_PROPERTIES_FILE, true, false);
        // Register mimeTypes
        registerConfig(home.getMimeTypesFile(), "artifactory.mimeType", "/META-INF/default/" + MIME_TYPES_FILE_NAME,
                true, false);
        // Register artifactory storage xml
        registerConfig(home.getBinaryStoreXmlFile(), "artifactory.binarystore.xml",
                "/META-INF/default/" + BINARY_STORE_FILE_NAME, true, true);
        // Register plugins
        registerFolder(home.getPluginsDir(), "artifactory.plugin.", false);
        // Register UI
        registerFolder(home.getLogoDir(), "artifactory.ui.", false);
        // Register Security dir
        registerFolder(home.getSecurityDir(), "artifactory.security.", true);
        // Register Cluster License file
        if (home.isHaConfigured()) {
            registerConfig(home.getLicenseFile(), "artifactory.cluster.license", null, false, true);
            // Register artifactory HA cluster ID
            registerConfig(home.getArtifactoryHaClusterIdFile(), "artifactory.cluster.id", null, false, false);
        }
        // Register access server configuration file
        registerConfig(home.getBundledAccessConfigFile(), "access.server.bundled.config", null, false, false);
    }

    /**
     * The method register shared folder such as plugins and UI in the JavaFilesWatcher to receive file change on the files
     */
    private void registerFolder(File folder, String prefixConfigName, boolean encrypted) throws IOException, SQLException {
        if (folder == null) {
            return;
        } else if (!prefixConfigName.endsWith(".") && !prefixConfigName.endsWith("/")) {
            throw new IllegalArgumentException(
                    "Prefix config name for folder must end with dot or slash, it was " + prefixConfigName);
        } else if (isEntryExcluded(folder)) {
            // Folder is in excluded list, skip it
            return;
        }
        FileUtils.forceMkdir(folder);
        javaFileWatcher.registerDirectoryListener(folder, prefixConfigName);
        if (folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = prefixConfigName + file.getName();
                    if (file.isDirectory()) {
                        registerFolder(file, name + "/", encrypted);
                    } else {
                        String absolutePath = file.getAbsolutePath();
                        if (!isEntryExcluded(file)) {
                            ConfigWrapper configWrapper = new ConfigWrapperImpl(file, name, this,
                                    null, false, encrypted, getPermissionsFor(file), home);
                            this.sharedConfigsByFile.put(absolutePath, configWrapper);
                        }
                    }
                }
            }
        }
        List<DBConfigWithTimestamp> configs = configsDao.getConfigs(prefixConfigName, encrypted, home);
        for (DBConfigWithTimestamp metaData : configs) {
            String name = metaData.getName();
            String fileName = name.replaceFirst(prefixConfigName, "");
            File file = new File(folder, fileName);
            if (!sharedConfigsByFile.containsKey(file.getAbsolutePath())) {
                ConfigWrapper configWrapper = new ConfigWrapperImpl(file, name, this,
                        null, false, encrypted, getPermissionsFor(file), home);
                this.sharedConfigsByFile.put(folder.getAbsolutePath(), configWrapper);
            }
        }
    }

    /**
     * Helper method to simplify the code, which actually does the file registration
     */
    private void registerConfig(File file, String configName, String defaultContent, boolean mandatoryConfig,
            boolean encrypted) throws IOException, SQLException {
        if (isEntryExcluded(file)) {
            // File is in excludes list, don't sync!
            return;
        }
        ConfigWrapper configWrapper = new ConfigWrapperImpl(file, configName, this, defaultContent,
                mandatoryConfig, encrypted, getPermissionsFor(file), home);
        this.sharedConfigsByFile.put(file.getAbsolutePath(), configWrapper);
    }

    /**
     * The method is being invoked by JAVA's WatchService after file change
     * There are three types of changes ENTRY_DELETE, ENTRY_CREATE, ENTRY_MODIFY
     */
    @Override
    public void fileChanged(File file, String configPrefix, WatchEvent.Kind<Path> eventType, long timestamp)
            throws SQLException, IOException {
        String eventTypeName = eventType.name();
        String filePath = file.getAbsolutePath();
        if (home.getHaNodeProperties() != null && !home.getHaNodeProperties().isPrimary()) {
            logChannel.debug("Local file event '" + eventTypeName + "' intercepted for file '" + filePath +
                    "', but this node is not the primary. This change will not be propagated to other nodes, and the " +
                    "file may be overwritten when an event from the master node is intercepted for it.");
            return;
        }
        if (!isEntryExcluded(file)) {
            fileChanged(file, configPrefix, FileEventType.fromValue(eventTypeName), sharedConfigsByFile.get(filePath));
        } else {
            logChannel.debug("Local file event '" + eventTypeName + "' intercepted for file '" + filePath +
                    "', but the file the excludes list. This change will not be propagated to other nodes.");
        }
    }

    private boolean isEntryExcluded(File file) {
        String filePath = file.getAbsolutePath();
        if (filePath.contains(home.getSecurityDir().getAbsolutePath())) {
            if (SECURITY_EXCLUDED_ENTRIES.stream().anyMatch(filePath::endsWith)) {
                return true;
            }
            //This is an ugly hack to sync only access.creds and service_id files, but not any other file under the access folder
            if (filePath.contains("/etc/security/access/")) {
                return !filePath.endsWith("etc/security/access/keys") &&
                       !filePath.endsWith("etc/security/access/keys/access.creds") &&
                        !filePath.endsWith("etc/security/access/keys/service_id");
            }
        }
        return false;
    }

    @Override
    public void forceFileChanged(File file, String configPrefix, FileEventType eventType) throws SQLException, IOException {
        ConfigWrapper configWrapper = sharedConfigsByFile.get(file.getAbsolutePath());
        fileChanged(file, configPrefix, eventType, configWrapper);
    }

    private void fileChanged(File file, String configPrefix, FileEventType eventType, ConfigWrapper configWrapper) {
        try {
            switch (eventType) {
                case DELETE: {
                    handleFileDeletedEvent(file, configWrapper);
                    break;
                }
                case CREATE: {
                    handleFileCreatedEvent(file, configPrefix, configWrapper);
                    break;
                }
                case MODIFY: {
                    handleFileModifiedEvent(configWrapper);
                    break;
                }
            }
        } catch (Exception e) {
            logChannel.error("Config manager Failed to handle file change for file: " + file.getAbsolutePath(), e);
        }
    }

    private void handleFileModifiedEvent(ConfigWrapper configWrapper) throws IOException, SQLException {
        if (configWrapper != null) {
            configWrapper.modified();
        }
    }

    private void handleFileCreatedEvent(File file, String configPrefix, ConfigWrapper configWrapper) throws IOException, SQLException {
        if (configWrapper != null) {
            configWrapper.create();
        } else if (!home.getEtcDir().getAbsolutePath().equals(file.getParentFile().getAbsolutePath())) {
            if (file.isDirectory()) {
                boolean encrypted = file.getAbsolutePath().startsWith(home.getSecurityDir().getAbsolutePath());
                registerFolder(file, configPrefix + file.getName() + "/", encrypted);
            } else {
                configWrapper = new ConfigWrapperImpl(file, configPrefix + file.getName(), this,
                        null, false, false,
                        getPermissionsFor(file), home);
                sharedConfigsByFile.put(file.getAbsolutePath(), configWrapper);
                configWrapper.create();
            }
        }
    }

    private void handleFileDeletedEvent(File file, ConfigWrapper configWrapper) throws SQLException {
        if (configWrapper != null) {
            configWrapper.remove();
            if (!home.getEtcDir().getAbsolutePath().equals(file.getParentFile().getAbsolutePath())) {
                sharedConfigsByFile.remove(file.getAbsolutePath());
            }
        }
    }

    /**
     * The method replaces the initial db channel into the permanent implementation
     */
    public void setPermanentDBChannel(DbChannel permanentDbChannel) {
        try {
            // Replace DB channel
            DbChannel dbChannel = this.configsDao.getDbChannel();
            if (dbChannel instanceof TemporaryDBChannel) {
                logChannel.info("Replacing temporary DB channel with permanent DB channel");
                this.configsDao.setDbChannel(permanentDbChannel);
                dbChannel.close();
                logChannel.info("Successfully closed temporary DB channel");
                if (!configTableExist) {
                    configTableExist = true;
                    startFileSync();
                }
            } else {
                // Can reach here only on reload
                this.configsDao.setDbChannel(permanentDbChannel);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace temporary db channel with the permanent one due to: "
                    + e.getMessage(), e);
        }
    }

    /**
     * The method replaces the initial log channel into the permanent implementation
     */
    public void setPermanentLogChannel() {
        // Replace log channel
        Logger logger = LoggerFactory.getLogger(ConfigurationManagerImpl.class);
        logChannel = new PermanentLogChannel(logger);
        configsDao.setLogChannel(logChannel);
    }

    /**
     * The method replaces the initial broadcast channel into the permanent implementation
     */
    public void setPermanentBroadcastChannel(BroadcastChannel broadcastChannel) {
        // Make sure that this can be called only once
        if (this.broadcastChannel instanceof TemporaryBroadcastChannelImpl) {
            // Replace broadcastChannel but first fire accumulated events.
            TemporaryBroadcastChannelImpl initialBroadcastChannel = (TemporaryBroadcastChannelImpl) this.broadcastChannel;
            // Replace broadcastChannel
            this.broadcastChannel = broadcastChannel;
            // Fire accumulated notifications events
            Set<Pair<String, FileEventType>> notifications = initialBroadcastChannel.getNotifications();
            notifications.forEach(pair -> broadcastChannel.notifyConfigChanged(pair.getFirst(), pair.getSecond()));
        } else {
            // Can reach here only on reload
            this.broadcastChannel = broadcastChannel;
        }
    }

    public void remoteConfigChanged(String configName, FileEventType eventType) throws Exception {
        handleByConfigPrefix(configName);
        for (Map.Entry<String, ConfigWrapper> entry : sharedConfigsByFile.entrySet()) {
            if (entry.getValue().getName().equals(configName)) {
                switch (eventType) {
                    case MODIFY:
                        entry.getValue().remoteModified();
                        break;
                    case CREATE:
                        entry.getValue().remoteCreate();
                        break;
                    case DELETE:
                        entry.getValue().remoteRemove();
                        break;
                }
            }
        }
    }

    private void handleByConfigPrefix(String configName) throws IOException, SQLException {
        String pluginPrefix = "artifactory.plugin.";
        if (configName.startsWith(pluginPrefix)) {
            File eventFile = resolvePath(home.getPluginsDir(), pluginPrefix, configName);
            doRecursive(eventFile, home.getPluginsDir(), true, configName, false);
        }
        String uiPrefix = "artifactory.ui.";
        if (configName.startsWith(uiPrefix)) {
            File eventFile = resolvePath(home.getLogoDir(), uiPrefix, configName);
            doRecursive(eventFile, home.getLogoDir(), true, configName, false);
        }
        String securityPrefix = "artifactory.security.";
        if (configName.startsWith(securityPrefix)) {
            File eventFile = resolvePath(home.getSecurityDir(), securityPrefix, configName);
            doRecursive(eventFile, home.getSecurityDir(), true, configName, true);
        }
    }

    private void doRecursive(File temp, File root, boolean file, String name, boolean encrypted) throws IOException, SQLException {
        if (temp.getAbsolutePath().equals(root.getAbsolutePath())) {
            return;
        }
        doRecursive(temp.getParentFile(), root, false, PathUtils.getParent(name) + "/", false);
        ConfigWrapper configWrapper = sharedConfigsByFile.get(temp.getAbsolutePath());
        if (configWrapper == null) {
            if (file) {
                registerConfig(temp, name, null, false, encrypted);
            } else {
                registerFolder(temp, name, encrypted);
            }
        }
    }

    private File resolvePath(File baseFile, String prefix, String name) {
        String path = name.replace(prefix, "");
        return new File(baseFile, path);
    }

    /**
     * If {@param prefixConfigName} contains security this file is in the security folder and should be created with
     * {@link SecurityFolderHelper#PERMISSIONS_MODE_600}, else the default permission of the underlying linux
     * is used. If master and nodes do not have same Posix setup permissions gap may appear.
     *
     * @param confFile the configuration file
     */
    private Set<PosixFilePermission> getPermissionsFor(File confFile) {
        if (confFile.getAbsolutePath().contains(home.getSecurityDir().getPath())) {
            return SecurityFolderHelper.PERMISSIONS_MODE_600;
        } else {
            // TODO: [by fsi RTFACT-13528] should read current file permissions and send to DB
            return null;
        }
    }

    public BroadcastChannel getBroadcastChannel() {
        return broadcastChannel;
    }

    public ConfigsDataAccessObject getConfigsDao() {
        return configsDao;
    }

    public ArtifactoryHome getHome() {
        return home;
    }

    public long getNormalizedTime(long timestamp) {
        return timestamp - timeGap;
    }

    @Override
    public long getDeNormalizedTime(long timestamp) {
        return timestamp + timeGap;
    }

    public boolean isConfigTableExist() {
        return configTableExist;
    }

    public LogChannel getLogChannel() {
        return logChannel;
    }
}
