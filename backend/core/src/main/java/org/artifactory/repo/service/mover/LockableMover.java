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

package org.artifactory.repo.service.mover;

import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.md.Properties;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.sapi.common.Lock;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;

/**
 * @author Chen Keinan
 */
public interface LockableMover {

    @Lock
    void moveCopyFile(VfsItem source, RepoRepoPath<LocalRepo> targetRrp, BaseRepoPathMover repoPathMover, MoveMultiStatusHolder status);

    @Lock
    VfsFolderRepo moveCopyFolder(VfsItem source, RepoRepoPath<LocalRepo> targetRrp, BaseRepoPathMover repoPathMove, MoveMultiStatusHolder status);

    @Lock
    void postFolderProcessing(VfsItem sourceItem, VfsFolder targetFolder, RepoRepoPath<LocalRepo> targetRrp, BaseRepoPathMover repoPathMover,
                              Properties properties, int numOfChildren);
}
