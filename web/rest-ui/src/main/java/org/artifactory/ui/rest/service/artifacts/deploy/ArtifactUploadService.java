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

package org.artifactory.ui.rest.service.artifacts.deploy;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.deploy.UploadArtifactInfo;
import org.artifactory.ui.utils.MultiPartUtils;
import org.artifactory.ui.utils.UnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactUploadService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(ArtifactUploadService.class);

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private MavenService mavenService;

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        if (!authorizationService.canDeployToLocalRepository()) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            log.error("User " + authorizationService.currentUsername() +
                    " had no deploy permissions on any local repository");
            return;
        }
        String uploadDir = ContextHelper.get().getArtifactoryHome().getTempUploadDir().getAbsolutePath();
        try {
            MultiPartUtils.createTempFolderIfNotExist(uploadDir);
            // get upload model
            UploadArtifactInfo uploadArtifactInfo = (UploadArtifactInfo) request.getImodel();
            // save file data tto temp
            List<String> fileNames = new ArrayList<>();
            MultiPartUtils.saveFileDataToTemp(centralConfigService, uploadArtifactInfo.fetchFormDataMultiPart(),
                    uploadDir, fileNames, false);

            File file = new File(uploadDir, fileNames.get(0));
            // get artifact info
            uploadArtifactInfo = UnitUtils.getUnitInfo(file, uploadArtifactInfo, mavenService);
            HaCommonAddon haAddon = addonsManager.addonByType(HaCommonAddon.class);
            if (haAddon.isHaEnabled()) {
                uploadArtifactInfo.setHandlingNode(haAddon.getCurrentMemberServerId());
            }
            response.iModel(uploadArtifactInfo);
        } catch (Exception e) {
            response.error(e.getMessage());
        }
    }
}
