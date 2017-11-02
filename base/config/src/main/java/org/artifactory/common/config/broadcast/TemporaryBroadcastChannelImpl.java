package org.artifactory.common.config.broadcast;

import com.google.common.collect.Sets;
import org.artifactory.common.config.FileEventType;
import org.jfrog.client.util.Pair;

import java.util.Set;

/**
 * @author gidis
 */
public class TemporaryBroadcastChannelImpl implements BroadcastChannel {

    private Set<Pair<String, FileEventType>> notifications = Sets.newHashSet();

    public Set<Pair<String, FileEventType>> getNotifications() {
        return notifications;
    }

    @Override
    public boolean notifyConfigChanged(String name, FileEventType eventType) {
        notifications.add(new Pair<>(name, eventType));
        return true;
    }

    @Override
    public void destroy() {
        // Empty notifications will have no time to process
        notifications.clear();
    }
}
