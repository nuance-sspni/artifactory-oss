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

import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;

/**
 * @author mamo
 */
public interface NuPkgHaMessage extends HaMessage {

    public class Added implements NuPkgHaMessage {
        public final RepoPath repoPath;
        public final Properties properties;

        public Added(RepoPath repoPath, Properties properties) {
            this.repoPath = repoPath;
            this.properties = properties;
        }
    }

    public class Removed implements NuPkgHaMessage {
        public final String repoKey;
        public final String packageId;
        public final String packageVersion;

        public Removed(String repoKey, String packageId, String packageVersion) {
            this.repoKey = repoKey;
            this.packageId = packageId;
            this.packageVersion = packageVersion;
        }
    }
}
