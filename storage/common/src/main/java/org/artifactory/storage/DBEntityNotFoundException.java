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

package org.artifactory.storage;

/**
 * Exception for DB entities which are not found
 * @author Shay Bagants
 */
public class DBEntityNotFoundException extends StorageException {
    public DBEntityNotFoundException(String message) {
        super(message);
    }

    public DBEntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public DBEntityNotFoundException(Throwable cause) {
        super(cause);
    }
}
