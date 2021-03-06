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

package org.artifactory.ui.rest.service.admin.security.permissions;

import org.apache.commons.lang.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AclService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.AclInfo;
import org.artifactory.ui.rest.model.admin.security.permissions.DeletePermissionTargetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Chen keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeletePermissionsTargetService<T extends DeletePermissionTargetModel> implements RestService<T> {
    @Autowired
    AclService aclService;
    @Autowired
    CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        T model = request.getImodel();
        for (String permissionId : model.getPermissionTargetNames()) {
            if (StringUtils.isNotBlank(permissionId)) {
                // get acl to delete
                AclInfo aclInfo = aclService.getAcl(permissionId);
                // delete acl
                aclService.deleteAcl(aclInfo.getPermissionTarget());
            }
        }
        if(model.getPermissionTargetNames().size() > 1) {
            response.info("Successfully removed "+model.getPermissionTargetNames().size()+" permission targets");
        }else if(model.getPermissionTargetNames().size()==1){
            response.info("Successfully removed permission target '" + model.getPermissionTargetNames().get(0) + "'");
        }
    }
}
