package org.artifactory.common.config;

import org.artifactory.common.config.broadcast.BroadcastChannel;
import org.artifactory.common.config.db.ConfigsDataAccessObject;
import org.artifactory.common.config.db.DbChannel;
import org.artifactory.common.config.log.LogChannel;
import org.artifactory.common.config.utils.TimeProvider;
import org.artifactory.common.config.watch.FileChangedListener;


public interface ConfigurationManager extends FileChangedListener, TimeProvider {

    LogChannel getLogChannel();

    boolean isConfigTableExist();

    ConfigsDataAccessObject getConfigsDao();

    BroadcastChannel getBroadcastChannel();

    void setPermanentDBChannel(DbChannel dbChannel);

    void setPermanentLogChannel();

    void setPermanentBroadcastChannel(BroadcastChannel propagationService);

    void remoteConfigChanged(String name, FileEventType eventType) throws Exception;

    long getDeNormalizedTime(long timestamp);

    void initDbChannels();

    void initDbProperties();

    void startSync();

    void destroy();
}