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

package org.artifactory.repo;

import java.util.List;

/**
 * @author Yoav Landman
 */
public interface LocalRepositoryConfiguration extends RepositoryConfiguration {

    String TYPE = "local";

    boolean isBlackedOut();

    String getChecksumPolicyType();

    boolean isHandleReleases();

    boolean isHandleSnapshots();

    int getMaxUniqueSnapshots();

    int getMaxUniqueTags();

    List<String> getPropertySets();

    String getSnapshotVersionBehavior();

    boolean isSuppressPomConsistencyChecks();

    boolean isArchiveBrowsingEnabled();

    boolean isXrayIndex();

    String getXrayMinimumBlockedSeverity();

    boolean isBlockXrayUnscannedArtifacts();

    boolean isCalculateYumMetadata();

    boolean isEnableFileListsIndexing();

    int getYumRootDepth();

    String getYumGroupFileNames();

}
