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

package org.artifactory.repo.http;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.annotation.Nullable;

/**
 * Service providing idle connections monitoring
 * for {@link PoolingHttpClientConnectionManager}
 *
 * @author Michael Pasternak
 */
public interface IdleConnectionMonitorService {
    /**
     * Adds {@link org.apache.http.impl.conn.PoolingHttpClientConnectionManager} to monitor
     *
     * @param owner the owner of connectionManager
     * @param connectionManager {@link PoolingHttpClientConnectionManager}
     */
    void add(Object owner, PoolingHttpClientConnectionManager connectionManager);

    /**
     * @return {@link Thread.State}
     */
    @Nullable
    Thread.State getStatus();

    /**
     * Removes monitored {@link PoolingHttpClientConnectionManager}
     *
     * @param owner
     */
    void remove(Object owner);

    /**
     * Stops idleConnection watcher
     */
    void stop();

    /**
     * This is used for testing purposes as we don't want to expose any data on the connection details in the connection
     * manager
     */
    int getManagerSize();
}
