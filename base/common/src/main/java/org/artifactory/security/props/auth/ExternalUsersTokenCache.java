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

import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * This cache is used to store users and their tokens. One can get a token for a user (used by getToken request) or
 * can identify a user according to his token (used for validating the token). <p>
 * Used for external users given that we cannot store their tokens in the DB. <p>
 * The implementation should make sure these 2 mappings are inserted together and expire together.
 *
 * @author Yinon Avraham
 */
public interface ExternalUsersTokenCache {

    Integer getExpirationPeriodInSeconds();

    void put(TokenKeyValue tokenKeyValue, UserDetails principal);

    UserDetails getUser(TokenKeyValue tokenKeyValue);

    TokenKeyValue getToken(UserDetails user);

    TokenKeyValue getToken(String userName);

    void invalidateToken(String userName);

    void invalidateAllTokens();
}
