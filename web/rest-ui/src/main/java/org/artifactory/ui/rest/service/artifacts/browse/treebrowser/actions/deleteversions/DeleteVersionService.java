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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.deleteversions;

import org.artifactory.api.module.VersionUnit;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.StatusHolder;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifactVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteVersionService implements RestService {

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        List<? extends RestModel> deleteArtifactVersionsList = request.getModels();
        Set<VersionUnit> versionUnitSet = new HashSet<>();
        createVersionUnitSet(deleteArtifactVersionsList, versionUnitSet);
        // delete versions
        deleteVersionsAndUpdateResponse(response, versionUnitSet);
    }

    /**
     * delete version and update response
     *
     * @param response       - encapsulate data related to response
     * @param versionUnitSet - version units
     */
    private void deleteVersionsAndUpdateResponse(RestResponse response, Set<VersionUnit> versionUnitSet) {
        StatusHolder statusHolder = repositoryService.undeployVersionUnits(versionUnitSet);
        if (statusHolder.isError()) {
            response.error(statusHolder.getLastError().getMessage());
        } else if (statusHolder.getLastWarning() != null) {
            response.warn("The operation finished with warnings, check the log for more information.");
        } else {
            response.info("Selected versions deleted successfully");
        }
    }

    private void createVersionUnitSet(List<? extends RestModel> deleteArtifactVersionsList,
            Set<VersionUnit> versionUnitSet) {
        if (deleteArtifactVersionsList != null && !deleteArtifactVersionsList.isEmpty()) {
            deleteArtifactVersionsList.forEach(deleteArtifactVersion -> {
                VersionUnit versionUnit = new VersionUnit(null, new HashSet<RepoPath>());
                ((DeleteArtifactVersion) deleteArtifactVersion).getRepoPaths().forEach(repoKeyPath -> {
                    String repoKey = repoKeyPath.getRepoKey();
                    String path = repoKeyPath.getPath();
                    RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
                    versionUnit.getRepoPaths().add(repoPath);
                });
                versionUnitSet.add(versionUnit);
            });
        }
    }
}
