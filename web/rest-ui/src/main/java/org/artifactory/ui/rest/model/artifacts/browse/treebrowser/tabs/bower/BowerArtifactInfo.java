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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.bower;

import org.artifactory.addon.bower.BowerDependencies;
import org.artifactory.addon.bower.BowerMetadataInfo;
import org.artifactory.addon.bower.BowerPkgInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;

import java.util.List;

/**
 * @author Chen Keinan
 */
public class BowerArtifactInfo extends BaseArtifactInfo {
    private BowerPkgInfo bowerPkgInfo;
    private List<BowerDependencies> bowerDependencies;
    private List<String> mainFiles;
    private List<String> ignoredFiles;

    BowerArtifactInfo() {
    }

    public BowerArtifactInfo(BowerMetadataInfo bowerMetadataInfo) {
        this.bowerPkgInfo = bowerMetadataInfo.getBowerPkgInfo();
        this.bowerDependencies = bowerMetadataInfo.getBowerDependencies();
        this.mainFiles = bowerMetadataInfo.getMainFiles();
        this.ignoredFiles = bowerMetadataInfo.getIgnoredFiles();
    }

    public BowerPkgInfo getBowerPkgInfo() {
        return bowerPkgInfo;
    }

    public void setBowerPkgInfo(BowerPkgInfo bowerPkgInfo) {
        this.bowerPkgInfo = bowerPkgInfo;
    }

    public List<BowerDependencies> getBowerDependencies() {
        return bowerDependencies;
    }

    public void setBowerDependencies(List<BowerDependencies> bowerDependencies) {
        this.bowerDependencies = bowerDependencies;
    }

    public List<String> getMainFiles() {
        return mainFiles;
    }

    public void setMainFiles(List<String> mainFiles) {
        this.mainFiles = mainFiles;
    }

    public List<String> getIgnoredFiles() {
        return ignoredFiles;
    }

    public void setIgnoredFiles(List<String> ignoredFiles) {
        this.ignoredFiles = ignoredFiles;
    }
}
