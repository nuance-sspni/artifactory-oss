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

package org.artifactory.addon.ha.message;

import org.artifactory.fs.WatcherInfo;
import org.artifactory.repo.RepoPath;

/**
 * @author mamo
 */
public interface WatchesHaMessage extends HaMessage {

    public class AddWatch implements WatchesHaMessage {
        public final long nodeId;
        public final WatcherInfo watchInfo;

        public AddWatch(long nodeId, WatcherInfo watchInfo) {
            this.nodeId = nodeId;
            this.watchInfo = watchInfo;
        }
    }

    public class DeleteAllWatches implements WatchesHaMessage {
        public final RepoPath repoPath;

        public DeleteAllWatches(RepoPath repoPath) {
            this.repoPath = repoPath;
        }
    }

    public class DeleteUserWatches implements WatchesHaMessage {
        public final RepoPath repoPath;
        public final String username;

        public DeleteUserWatches(RepoPath repoPath, String username) {
            this.repoPath = repoPath;
            this.username = username;
        }
    }

    public class DeleteAllUserWatches implements WatchesHaMessage {
        public final String username;

        public DeleteAllUserWatches(String username) {
            this.username = username;
        }
    }
}
