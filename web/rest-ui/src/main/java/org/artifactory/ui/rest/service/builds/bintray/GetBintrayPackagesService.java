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

package org.artifactory.ui.rest.service.builds.bintray;

import com.google.common.collect.Lists;
import org.artifactory.api.bintray.BintrayService;
import org.artifactory.api.bintray.exception.BintrayException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.builds.BintrayModel;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetBintrayPackagesService implements RestService {

    @Autowired
    BintrayService bintrayService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        Map<String, String> headersMap = RequestUtils.getHeadersMap(request.getServletRequest());
        String repoKey = request.getQueryParamByKey("key");
        // get repo packages
        BintrayModel bintrayModel = getRepoPackages(response, headersMap, repoKey);
        response.iModel(bintrayModel);

    }

    private BintrayModel getRepoPackages(RestResponse artifactoryResponse, Map<String, String> headersMap, String repoKey) {
        List<String> repoPackages = getRepoPackages(repoKey, artifactoryResponse, headersMap);
        BintrayModel bintrayModel = new BintrayModel();
        bintrayModel.setBinTrayPackages(repoPackages);
        return bintrayModel;
    }

    /**
     * get repositories packages
     *
     * @param repoKey    - repo key
     * @param response   - encapsulated data related to response
     * @param headersMap - headers map
     * @return - list of repository packages
     */
    private List<String> getRepoPackages(String repoKey, RestResponse response, Map<String, String> headersMap) {
        List<String> repoPackages = Lists.newArrayList();
        try {
            repoPackages = bintrayService.getPackagesToDeploy(repoKey, headersMap);
        } catch (IOException e) {
            response.error("Connection failed with exception: " + e.getMessage());
        } catch (BintrayException e) {
            response.error("Could not retrieve packages list for '" + repoKey + "'");
        }
        return repoPackages;
    }
}
