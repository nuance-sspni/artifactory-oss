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

package org.artifactory.addon.oauth;

import org.artifactory.security.props.auth.model.OauthModel;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Travis Foster
 */
public interface OAuthHandler {

    /**
     * Handle identityProvider login response.
     */
    Object handleLoginResponse(HttpServletRequest request);

    /**
     * Handle login event from Artifactory login link.
     * Create login request and redirect to the OAuth login page.
     */
    List<OAuthLoginUrl> getActiveProviders(HttpServletRequest request);

    /**
     * Handle login from external command line tool.
     * Use basic auth to log in and return an access token.
     */
    Object handleLogin(String method, String name, String path, HttpServletRequest request, SsoLoginModel ssoLoginModel);

    /**
     * Get the name of the provider specified for NPM logins (if exists)
     */
    String getNpmLoginHandler();


    /**
     * use rest api to get user active token
     *
     * @param providerName - provider name (git enterprise and etc)
     * @param userName     - user name
     * @param basicAuth    - basic authorization
     * @return Oauth model with token
     */
    OauthModel getCreateToken(String providerName, String userName, String basicAuth);
}
