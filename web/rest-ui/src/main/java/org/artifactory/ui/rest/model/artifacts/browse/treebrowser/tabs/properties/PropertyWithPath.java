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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties;

import org.artifactory.rest.common.model.BaseModel;

/**
 * @author Gidi Shabat
 */
public class PropertyWithPath extends BaseModel{
    private String name;
    private String path;
    private String repoKey;
    private boolean recursive;

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getRepoKey() {
        return repoKey;
    }
}
