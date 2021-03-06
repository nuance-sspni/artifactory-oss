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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.commons.lang.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoResolver;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.exception.RepoConfigException;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Get all resolved repositories for the current virtual repository
 *
 * @author Aviad Shikloshi
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetResolvedRepositories<T extends VirtualRepositoryConfigModel> implements RestService<T> {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RepoConfigValidator repoValidator;

    @Autowired
    private CentralConfigService configService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {

        VirtualRepositoryConfigModel virtualModel = request.getImodel();
        VirtualBasicRepositoryConfigModel basic = virtualModel.getBasic();

        try {
            List<VirtualSelectedRepository> selectedRepositories = basic.getSelectedRepositories();
            List<RepoDescriptor> selectedRepoDesc = selectedRepositories.stream()
                    .map(this::getDescriptorFromKey)
                    .collect(Collectors.toList());
            repoValidator.validateSelectedReposInVirtualExist(selectedRepositories, configService.getMutableDescriptor());
            VirtualRepoResolver resolver = initRepositoriesResolverWithCurrentRepos(selectedRepoDesc,
                    virtualModel.getGeneral());
            List<RealRepoDescriptor> orderedRepos = resolver.getOrderedRepos();
            selectedRepositories = orderedRepos.stream().map(VirtualSelectedRepository::new).collect(Collectors.toList());
            response.iModelList(selectedRepositories);
        } catch (RepoConfigException e) {
            response.error(e.getMessage()).responseCode(e.getStatusCode());
        }
    }

    // To avoid cache repository descriptor we will look the repository key first in virtual and than in remote and local
    private RepoDescriptor getDescriptorFromKey(VirtualSelectedRepository repository) {
        String repoKey = repository.getRepoName();
        RepoDescriptor descriptor = repositoryService.virtualRepoDescriptorByKey(repoKey);
        if (descriptor == null) {
            descriptor = repositoryService.remoteRepoDescriptorByKey(repoKey);
        }
        if (descriptor == null) {
            descriptor = repositoryService.localRepoDescriptorByKey(repoKey);
        }
        return descriptor;
    }

    /**
     * Create repository resolver with our current data
     *
     * @param general        the repository general representation to extract repository key if exists
     * @return virtual repository resolver using our up to date data
     */
    private VirtualRepoResolver initRepositoriesResolverWithCurrentRepos(List<RepoDescriptor> selectedRepoDesc,
            GeneralRepositoryConfigModel general) {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setRepositories(selectedRepoDesc);
        String repoKey = StringUtils.EMPTY;
        if (general != null && general.getRepoKey() != null) {
            repoKey = general.getRepoKey();
        }
        virtualRepoDescriptor.setKey(repoKey);
        return new VirtualRepoResolver(virtualRepoDescriptor);
    }
}
