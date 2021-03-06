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

import com.google.common.collect.Maps;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Maps all available urls FOR P2 ONLY (filtered by maven types)
 *
 * @author Dan Feldman
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetRemoteRepoUrlMappingService implements RestService<Map<String, String>> {

    @Autowired
    RepositoryService repoService;

    @Override
    public void execute(ArtifactoryRestRequest<Map<String, String>> request, RestResponse response) {
        ConcurrentMap<String, String> repoKeyToUrl = Maps.newConcurrentMap();
        repoService.getRemoteRepoDescriptors().parallelStream()
                .filter(repoDesc -> repoDesc.getType().isMavenGroup())
                .map(repoDesc -> repoKeyToUrl.put(repoDesc.getKey(), repoDesc.getUrl()))
                .collect(Collectors.toList());
        response.iModel(repoKeyToUrl);
    }
}
