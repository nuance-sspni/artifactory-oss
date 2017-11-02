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

package org.artifactory.security;

import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.oauth.OAuthSettings;
import org.artifactory.security.props.auth.ExternalUsersTokenCache;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.util.dateUtils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.text.ParseException;

/**
 * @author Chen  Keinan
 */
@Component
public class LoginHandlerImpl implements LoginHandler {
    private static final Logger log = LoggerFactory.getLogger(LoginHandlerImpl.class);

    @Autowired
    private OauthManager oauthManager;

    @Autowired
    private ExternalUsersTokenCache externalUsersTokenCache;

    @Override
    public OauthModel doBasicAuthWithDb(String[] tokens,
            AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource) throws IOException, ParseException {
        assert tokens.length == 2;
        AuthenticationManager authenticationManager = ContextHelper.get().beanForType(AuthenticationManager.class);
        String username = tokens[0];
        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(username, tokens[1]);
        authRequest.setDetails(authenticationDetailsSource);
        Authentication authenticate = authenticationManager.authenticate(authRequest);
        SecurityContextHolder.getContext().setAuthentication(authenticate);
        boolean putInCache = false;
        TokenKeyValue tokenKeyValue = oauthManager.getToken(username);
        if (tokenKeyValue == null) {
            tokenKeyValue = oauthManager.createToken(username);
        }
        if (tokenKeyValue == null) {
            log.debug("could not create and persist token for authenticated user {}, storing generated token in shared cache.", username);
            tokenKeyValue = oauthManager.generateToken(username);
            if (tokenKeyValue != null) {
                externalUsersTokenCache.put(tokenKeyValue, (UserDetails) authenticate.getPrincipal());
                putInCache = true;
            } else {
                throw new RuntimeException("failed to generate token for authenticated user: " + username);
            }
        }
        AuthenticationModel oauthModel = new AuthenticationModel(tokenKeyValue.getToken(), DateUtils.formatBuildDate(System.currentTimeMillis()));
        if (putInCache) {
            oauthModel.setExpiresIn(externalUsersTokenCache.getExpirationPeriodInSeconds());
        }
        return oauthModel;
    }

    @Override
    public OauthModel doBasicAuthWithProvider(String header, String username) {
        OAuthHandler oAuthHandler = ContextHelper.get().beanForType(OAuthHandler.class);
        CentralConfigDescriptor descriptor = ContextHelper.get().getCentralConfig().getDescriptor();
        OAuthSettings oauthSettings = descriptor.getSecurity().getOauthSettings();
        String defaultProvider = oauthSettings.getDefaultNpm();
        // try to get token from provider
        return oAuthHandler.getCreateToken(defaultProvider, username, header);
    }

    @Override
    public String[] extractAndDecodeHeader(String header) throws IOException {
        byte[] base64Token = header.substring(6).getBytes("UTF-8");
        byte[] decoded;
        try {
            decoded = org.springframework.security.crypto.codec.Base64.decode(base64Token);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Failed to decode basic authentication token");
        }
        String token = new String(decoded, "UTF-8");

        int delim = token.indexOf(":");

        if (delim == -1) {
            throw new BadCredentialsException("Invalid basic authentication token");
        }
        return new String[]{token.substring(0, delim), token.substring(delim + 1)};
    }
}
