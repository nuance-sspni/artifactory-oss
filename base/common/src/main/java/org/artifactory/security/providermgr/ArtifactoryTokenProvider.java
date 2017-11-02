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

package org.artifactory.security.providermgr;

import org.artifactory.security.props.auth.ExternalUsersTokenCache;
import org.artifactory.security.props.auth.model.*;
import org.artifactory.util.dateUtils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;

/**
 * Artifactory specific token cache implementation
 *
 * @author Chen Keinan
 */
@Component
public class ArtifactoryTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryTokenProvider.class);

    @Autowired
    private ExternalUsersTokenCache externalUsersTokenCache;


    public OauthModel getToken(ArtifactoryCacheKey artifactoryCacheKey) {
        OauthModel oauthModel = null;
        try {
            log.trace("Getting token for " + artifactoryCacheKey.getUser());
            TokenKeyValue token = externalUsersTokenCache.getToken(artifactoryCacheKey.getUser());
            if (token != null) {
                oauthModel = new AuthenticationModel(token.getToken(), getCurrentDate(), externalUsersTokenCache.getExpirationPeriodInSeconds());
            } else {
                oauthModel = artifactoryCacheKey.getProviderMgr().fetchAndStoreTokenFromProvider(); // This also caches the token for externals
            }
            return oauthModel;
        } finally {
            if (oauthModel != null && (oauthModel instanceof OauthErrorModel || oauthModel instanceof OauthDockerErrorModel)) {
                externalUsersTokenCache.invalidateToken(artifactoryCacheKey.getUser());
            }
        }
    }

    /**
     * Used when expiring user credentials or revoking api keys - will remove the user's tokens from the cache
     * to force re-authentication.
     * @param userName  user to invalidate cache entries for
     */
    public void invalidateUserCacheEntries(String userName) {
        externalUsersTokenCache.invalidateToken(userName);
    }

    public void invalidateCacheEntriesForAllUsers() {
        externalUsersTokenCache.invalidateAllTokens();
    }

    private String getCurrentDate() {
        try {
            return DateUtils.formatBuildDate(System.currentTimeMillis());
        } catch (ParseException e) {
            return "";
        }
    }
}
