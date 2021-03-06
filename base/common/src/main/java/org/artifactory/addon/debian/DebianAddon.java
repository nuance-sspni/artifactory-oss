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

package org.artifactory.addon.debian;

import org.artifactory.addon.Addon;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * @author Gidi Shabat
 */
public interface DebianAddon extends Addon {

    void recalculateAll(LocalRepoDescriptor localRepoDescriptor, String password, boolean delayed, boolean writeProps);

    void calculateMetaData(Set<DebianCalculationEvent> calculationRequests,
            @Nullable Collection<RepoPath> propertyWriterEntries, boolean delayed);

    boolean hasPrivateKey();

    boolean hasPublicKey();

    boolean foundExpiredAndRemoteIsNewer(RepoResource remoteResource, RepoResource cachedResource);

    /**
     * Used to get the package metadata for the UI info tab
     *
     * @param repoPath Path to the package
     * @return DebianMetadataInfo instance - UI model for the info tab
     */
    DebianMetadataInfo getDebianMetaDataInfo(RepoPath repoPath);
}
