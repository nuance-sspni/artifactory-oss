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

package org.artifactory.ui.rest.service.builds.buildsinfo;

import org.artifactory.api.build.BuildService;
import org.artifactory.api.build.GeneralBuild;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.builds.GeneralBuildInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetPrevBuildListService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetPrevBuildListService.class);

    @Autowired
    private BuildService buildService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String buildName = request.getPathParamByKey("name");
        String date = request.getPathParamByKey("date");
        // fetch build info data
        fetchAllBuildsData(response, buildName, date);
    }

    /**
     * fetch all build data by type
     *
     * @param artifactoryResponse - encapsulate data related to response
     * @param buildName           - current build name
     * @param buildDate           - current build date
     */
    private void fetchAllBuildsData(RestResponse artifactoryResponse, String buildName, String buildDate) {
        List<GeneralBuild> prevBuildsList = buildService.getPrevBuildsList(buildName, buildDate);
        List<GeneralBuildInfo> generalBuildInfoList = prevBuildsList.stream()
                .map(this::getBuildInfoFromBuildRun)
                .collect(Collectors.toList());
        artifactoryResponse.iModelList(generalBuildInfoList);
    }

    private GeneralBuildInfo getBuildInfoFromBuildRun(GeneralBuild buildRun) {
        return new GeneralBuildInfo.BuildBuilder()
                .buildNumber(buildRun.getBuildNumber())
                .buildName(buildRun.getBuildName())
                .buildStat(buildRun.getStatus())
                .time(buildRun.getBuildDate()).build();
    }
}
