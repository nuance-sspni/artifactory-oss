package org.artifactory.converter.helpers;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.ConfigurationManager;
import org.artifactory.common.config.ConfigurationManagerImpl;
import org.artifactory.common.config.FileEventType;
import org.artifactory.common.config.broadcast.BroadcastChannel;
import org.artifactory.common.config.db.ConfigsDataAccessObject;
import org.artifactory.common.config.db.DbChannel;
import org.artifactory.common.config.log.LogChannel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.sql.SQLException;

/**
 * @author Gidi Shabat
 */
public class MockConfigurationManagerImpl implements ConfigurationManager {

    public MockConfigurationManagerImpl(ArtifactoryHome home) {
        ConfigurationManagerImpl.createDefaultFiles(home);
        ConfigurationManagerImpl.initDbProperties(home);
    }

    @Override
    public LogChannel getLogChannel() {
        return null;
    }

    @Override
    public boolean isConfigTableExist() {
        return false;
    }

    @Override
    public ConfigsDataAccessObject getConfigsDao() {
        return null;
    }

    @Override
    public BroadcastChannel getBroadcastChannel() {
        return null;
    }

    @Override
    public void setPermanentDBChannel(DbChannel dbChannel) {

    }

    @Override
    public void setPermanentLogChannel() {

    }

    @Override
    public void setPermanentBroadcastChannel(BroadcastChannel propagationService) {

    }

    @Override
    public void remoteConfigChanged(String name, FileEventType eventType) throws Exception {

    }

    @Override
    public long getDeNormalizedTime(long timestamp) {
        return 0;
    }

    @Override
    public void initDbChannels() {

    }

    @Override
    public void startSync() {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void initDbProperties() {

    }

    @Override
    public void fileChanged(File file, String configPrefix, WatchEvent.Kind<Path> eventType, long nanoTime) throws SQLException, IOException {

    }

    @Override
    public void forceFileChanged(File file, String contextPrefix, FileEventType eventType) throws SQLException, IOException {

    }

    @Override
    public long getNormalizedTime(long timestamp) {
        return 0;
    }
}
