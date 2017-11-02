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

package org.artifactory.security.props.auth;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.props.auth.CacheWrapper.CacheConfig;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * See ExternalUsersTokenCache for documentation. This implementation uses 2 caches to achieve 2 ways mapping between
 * a user and his token. The tokens are cached in-memory and auto expire according to the configuration.<p>
 *
 * @author Yinon Avraham
 */
@Component
public class ExternalUsersTokenCacheImpl implements ExternalUsersTokenCache {

    private static final String PROPS_TOKEN_CACHE_NAME = ExternalUsersTokenCacheImpl.class.getName();

    @Autowired
    private AddonsManager addonsManager;

    // For get token. Cached locally (worst case is the user will get a new token)
    private CacheWrapper<UserDetails, TokenKeyValue> usersToTokensCache;

    // For validate token. Cached either locally or with hazelcast for HA because the token should be recognized in all nodes (e.g. so that the download won't fail in the middle)
    private CacheWrapper<TokenKeyValue, UserDetails> tokensToUsersCache;

    private final ReentrantLock cacheLock = new ReentrantLock();

    private void init() {
        if (tokensToUsersCache == null) {
            cacheLock.lock();
            if (tokensToUsersCache == null) {
                try {
                    initCache();
                } finally {
                    cacheLock.unlock();
                }
            }
        }
    }

    private void initCache() {
        CacheConfig cacheConfig = CacheConfig.newConfig()
                .expireAfterWrite(getExpirationPeriodInSeconds(), TimeUnit.SECONDS)
                .build();
        this.usersToTokensCache = new SimpleCacheWrapper<>(cacheConfig);
        this.tokensToUsersCache = addonsManager.addonByType(HaCommonAddon.class).getCache(PROPS_TOKEN_CACHE_NAME, cacheConfig);
    }

    @Override
    public Integer getExpirationPeriodInSeconds() {
        return ConstantValues.genericTokensCacheIdleTimeSecs.getInt();
    }

    @Override
    public void put(TokenKeyValue token, UserDetails user) {
        init();
        usersToTokensCache.put(user, token);
        tokensToUsersCache.put(token, user);
    }

    @Override
    public UserDetails getUser(TokenKeyValue token) {
        init();
        return tokensToUsersCache.get(token);
    }

    @Override
    public TokenKeyValue getToken(UserDetails user) {
        init();
        return usersToTokensCache.get(user);
    }

    @Override
    public TokenKeyValue getToken(String userName) {
        return getToken(new UserKey(userName));
    }

    @Override
    public void invalidateToken(String userName) {
        if (usersToTokensCache != null) {
            UserDetails user = new UserKey(userName);
            TokenKeyValue token = usersToTokensCache.get(user);
            if (token != null) {
                usersToTokensCache.invalidate(user);
                tokensToUsersCache.invalidate(token);
            }
        }
    }

    @Override
    public void invalidateAllTokens() {
        if (usersToTokensCache != null) {
            usersToTokensCache.invalidateAll();
            tokensToUsersCache.invalidateAll();
        }
    }


    /**
     * This class is used for getToken to ensure the comparison is done according to username only, and not according to
     * the type (the cache actually holds instances of SimpleUser, which we cannot create from here)
     */
    private class UserKey extends User {
        UserKey(String username) {
            super(username, "", Collections.emptySet());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UserDetails && this.getUsername().equals(((UserDetails)other).getUsername());
        }
    }
}
