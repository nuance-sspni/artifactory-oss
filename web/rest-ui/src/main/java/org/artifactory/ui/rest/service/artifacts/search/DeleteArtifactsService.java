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

package org.artifactory.ui.rest.service.artifacts.search;

import org.apache.commons.lang.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.StatusHolder;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifact;
import org.artifactory.ui.rest.model.artifacts.search.DeleteArtifactsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteArtifactsService<T extends DeleteArtifactsModel> implements RestService<T> {
    private static final Logger log = LoggerFactory.getLogger(DeleteArtifactsService.class);

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        DeleteArtifactsModel model = request.getImodel();
        for (DeleteArtifact deleteArtifactModel : model.getArtifacts()) {
            String repoKey = deleteArtifactModel.getRepoKey();
            String path = deleteArtifactModel.getPath();
            if(StringUtils.isNotBlank(repoKey) && StringUtils.isNotBlank(path)) {
                RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
                // delete artifact from repo path
                StatusHolder statusHolder = deleteArtifact(repoPath);
                // update response data
                updateResponseData(response, statusHolder, repoPath);
            }else {
                log.warn("Failed to delete item : {} ", repoKey+"/"+path);
            }
        }
    }

    /**
     * update response feedback
     */
    private void updateResponseData(RestResponse artifactoryResponse, StatusHolder statusHolder, RepoPath repoPath) {
        if (statusHolder.isError()) {
            artifactoryResponse.error("Fail to delete the artifact : "+repoPath);
        } else {
            artifactoryResponse.info("Successfully deleted artifacts");
        }
    }

    /**
     * un deploy artifact from repo path
     *
     * @param repoPath - artifact repo path
     * @return - status of the un deploy
     */
    protected StatusHolder deleteArtifact(RepoPath repoPath) {
        return repositoryService.undeploy(repoPath, true, true);
    }
}