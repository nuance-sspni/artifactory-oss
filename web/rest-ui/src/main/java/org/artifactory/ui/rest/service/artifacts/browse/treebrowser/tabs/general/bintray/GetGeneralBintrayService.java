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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.bintray;

import org.artifactory.api.bintray.BintrayPackageInfo;
import org.artifactory.api.bintray.BintrayService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.bintray.BintrayInfoModel;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetGeneralBintrayService implements RestService {

    @Autowired
    private AuthorizationService authService;

    @Autowired
    private BintrayService bintrayService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String sha1 = request.getQueryParamByKey("sha1");
        Map<String, String> headersMap = RequestUtils.getHeadersMap(request.getServletRequest());
        BintrayInfoModel bintrayInfoModel = getBintrayInfoModel(sha1, headersMap);
        response.iModel(bintrayInfoModel);
    }

    private BintrayInfoModel getBintrayInfoModel(String sha1, Map<String, String> headersMap) {
        BintrayInfoModel bintrayInfoModel = new BintrayInfoModel();

        if(  ConstantValues.bintrayUIHideUploads.getBoolean()) {
            return bintrayInfoModel;
        }
        boolean anonymousUser = authService.isAnonymous();
        boolean hasSystemAPIKey = bintrayService.hasBintraySystemUser();
        boolean userHasBintrayAuth = bintrayService.isUserHasBintrayAuth();
        if (anonymousUser && !hasSystemAPIKey) {
            bintrayInfoModel.setErrorMessage("Please login to view package information from Bintray's JCenter.");
            return bintrayInfoModel;
        }
        if (!userHasBintrayAuth && !hasSystemAPIKey) {
            bintrayInfoModel.setErrorMessage("To view package information from Bintray, please configure " +
                    "your Bintray credentials in the user profile page.");
            return bintrayInfoModel;
        }
        BintrayPackageInfo bintrayPackageInfo = bintrayService.getBintrayPackageInfo(sha1, headersMap);
        if (bintrayPackageInfo != null) {
            bintrayInfoModel.setName(bintrayPackageInfo.getName());
            bintrayInfoModel.setNameLink(buildTitleLink(bintrayPackageInfo));
            bintrayInfoModel.setDescription(bintrayPackageInfo.getDesc());
            bintrayInfoModel.setLatestVersion(bintrayPackageInfo.getLatest_version());
            bintrayInfoModel.setLatestVersionLink(buildLatestVersionLink(bintrayPackageInfo));
            bintrayInfoModel.setIconURL(buildIconURL(bintrayPackageInfo));
        } else {
            bintrayInfoModel.setErrorMessage("Could not retrieve package information from Bintray's JCenter.");
        }

        return bintrayInfoModel;
    }

    private String buildTitleLink(BintrayPackageInfo packageInfo) {
        return ConstantValues.bintrayUrl.getString() + "/pkg/show/general" + "/" +
                packageInfo.getOwner() + "/" + packageInfo.getRepo() + "/" + packageInfo.getName();
    }

    private String buildLatestVersionLink(BintrayPackageInfo packageInfo) {
        return ConstantValues.bintrayUrl.getString() + "/version/show/general" + "/" + packageInfo.getOwner() + "/" +
                packageInfo.getRepo() + "/" + packageInfo.getName() + "/" + packageInfo.getLatest_version();
    }

    private String buildIconURL(BintrayPackageInfo packageInfo) {
        return ConstantValues.bintrayApiUrl.getString() + "/packages/bintray/jcenter/" +
                packageInfo.getName() + "/images/avatar";
    }
}
