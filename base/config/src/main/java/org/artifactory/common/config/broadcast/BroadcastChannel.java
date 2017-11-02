package org.artifactory.common.config.broadcast;

import org.artifactory.common.config.FileEventType;

/**
 * @author gidis
 */
public interface BroadcastChannel {

    boolean notifyConfigChanged(String name, FileEventType eventType);

    void destroy();
}
