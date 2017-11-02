package org.artifactory.common.config.watch;

import org.artifactory.common.config.FileEventType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.sql.SQLException;

/**
 * @author gidis
 */
public interface FileChangedListener {

    void fileChanged(File file, String configPrefix, WatchEvent.Kind<Path> eventType, long nanoTime) throws SQLException, IOException;

    void forceFileChanged(File file, String contextPrefix, FileEventType eventType) throws SQLException, IOException;
}