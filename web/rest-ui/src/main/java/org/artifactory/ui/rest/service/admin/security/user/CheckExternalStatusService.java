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

package org.artifactory.ui.rest.service.admin.security.user;

import org.artifactory.api.security.UserAwareAuthenticationProvider;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CheckExternalStatusService<T extends User> implements RestService<T> {

    @Autowired
    private UserAwareAuthenticationProvider provider;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        User user = request.getImodel();
        String externalUserStatus = getExternalUserStatus(user);
        user.setExternalRealmStatus(externalUserStatus);
        response.iModel(user);
    }


    /**
     * check if user exist in external system and return status
     *
     * @param user - user details
     * @return - external user status
     */
    private String getExternalUserStatus(User user) {
        String userStatus;
        if (provider.userExists(user.getName(), user.getRealm())) {
            userStatus = "Active user";
        } else {
            userStatus = "Inactive user";
        }
        return userStatus;
    }
}
