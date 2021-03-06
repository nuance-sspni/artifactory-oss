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

package org.artifactory.ui.rest.service.admin.security.group;

import org.apache.http.HttpStatus;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.GroupInfo;
import org.artifactory.ui.rest.model.admin.security.group.DeleteGroupsModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.util.List;


/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteGroupService<T extends DeleteGroupsModel> implements RestService<T> {
    @Autowired
    protected UserGroupService userGroupService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        T model = request.getImodel();
        for (String groupName : model.getGroupNames()) {
            if (groupName == null || groupName.length() == 0) {
                response.responseCode(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            GroupInfo group = userGroupService.findGroup(groupName);
            if (group != null && group.isAdminPrivileges()) {
                List<String> adminGroupNames = userGroupService.getAllAdminGroupsNames();
                adminGroupNames.remove(groupName);
                boolean adminUserExists = userGroupService.adminUserExists();
                if (!adminUserExists && adminGroupNames.size() == 0) {
                    response.error("Cannot delete group '"+groupName+"'. There must be at least one user configured with admin privileges.")
                            .responseCode(HttpStatus.SC_BAD_REQUEST);
                    return;
                }
            }
            userGroupService.deleteGroup(groupName);

        }
        if(model.getGroupNames().size()>1){
            response.info("Successfully removed "+model.getGroupNames().size()+" groups");
        }else if(model.getGroupNames().size()==1){
            response.info("Successfully removed group '" + model.getGroupNames().get(0) + "'");
        }
    }
}
