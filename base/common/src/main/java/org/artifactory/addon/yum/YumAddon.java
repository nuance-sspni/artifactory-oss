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

package org.artifactory.addon.yum;

import org.artifactory.addon.Addon;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.RequestContext;

import javax.annotation.Nullable;

/**
 * @author Noam Y. Tenne
 */
public interface YumAddon extends Addon {

    String REPO_DATA_DIR = "repodata/";

    /**
     * Activate the YUM metadata calculation for all needed paths under the local repository.
     * The paths are the parent of all repodata needed based on the yum depth parameter.
     * Each calculation will be queued and processed asynchronously.
     *
     * @param repo the local repository to activate YUM calculation on.
     */
    void requestAsyncRepositoryYumMetadataCalculation(LocalRepoDescriptor repo, String passphrase);

    /**
     * Activate the YUM metadata calculation for the specific list of paths.
     * Each paths should be a parent of a repodata folder that need recalculation.
     * Each calculation will be queued and processed asynchronously.
     *
     * @param passphrase            the private key passphrase provided by a REST calculation call
     * @param repoPaths
     */
    void requestAsyncRepositoryYumMetadataCalculation(String passphrase, RepoPath... repoPaths);

    /**
     * Activate the YUM metadata calculation for all needed paths under the local repository.
     * The paths are the parent of all repodata needed based on the yum depth parameter.
     * Each calculation will be processed in a separate transaction but synchronously to this method.
     *
     * @param repo                  the local repository to activate YUM calculation on.
     * @param passphrase            the private key passphrase provided by a REST calculation call
     */
    void requestYumMetadataCalculation(LocalRepoDescriptor repo, String passphrase);

    /**
     *  get Rpm file Meta data
     */
    ArtifactRpmMetadata getRpmMetadata(FileInfo fileInfo) ;

    /**
     * Triggers an async calculation on {@param requestedPath} using information from {@param requestContext}
     * across all of the virtual's aggregated repos that contain this path.
     */
    void calculateVirtualYumMetadataAsync(RepoPath requestedPath, @Nullable RequestContext requestContext);

    /**
     * Triggers a calculation on {@param requestedPath} using information from {@param requestContext}
     * across all of the virtual's aggregated repos that contain this path.
     * This method blocks until the path is deemed calculated which is when either another thread has finished
     * calculating this path (it was already running when this request came) or this thread has calculated it.
     * Once this method returns the index is either available in the virtual cache or it doesn't exist (because nothing
     * was calculated)
     */
    void calculateVirtualYumMetadata(RepoPath requestedPath, @Nullable RequestContext requestContext);

    /**
     * Checks if the repo that contains {@param yumRepoRootPath} is aggregated in any virtual repos, and triggers
     * an async calculation on those virtuals if yes.
     */
    void invokeVirtualCalculationIfNeeded(RepoPath yumRepoRootPath);
}
