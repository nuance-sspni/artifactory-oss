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

package org.artifactory.storage.db.binstore.service;

import org.artifactory.storage.binstore.service.BinaryInfo;

/**
 * @author Gidi Shabat
 */
public class BinaryInfoImpl implements BinaryInfo {
    private long length;
    private String md5;
    private String sha1;


    @Deprecated
    public BinaryInfoImpl(long length, String md5, String sha1) {
        this.length = length;
        this.md5 = md5;
        this.sha1 = sha1;
    }


    public BinaryInfoImpl(String sha1, String md5, long length) {
        this.sha1 = sha1;
        this.md5 = md5;
        this.length = length;
    }

    @Override
    public String getSha1() {
        return sha1;
    }

    @Override
    public String getMd5() {
        return md5;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return sha1.equals(((BinaryInfoImpl) o).sha1);
    }

    @Override
    public int hashCode() {
        return sha1.hashCode();
    }

    @Override
    public String toString() {
        return "{" + sha1 + ',' + md5 + ',' + length + '}';
    }
}
