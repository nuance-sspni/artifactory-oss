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

import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
public class ApiKeyManager extends PropsTokenManager {

    public static final String API_KEY = "apiKey";

    public static final String API_KEY_HEADER = "X-JFrog-Art-Api";

    public static final String OLD_API_KEY_HEADER = "X-Api-Key";

    @Override
    public String getPropKey() {
        return API_KEY;
    }
}
