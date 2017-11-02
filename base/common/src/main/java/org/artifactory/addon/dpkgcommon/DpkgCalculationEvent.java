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

package org.artifactory.addon.dpkgcommon;

import java.io.Serializable;

/**
 * @author Dan Feldman
 */
public abstract class DpkgCalculationEvent implements Serializable {

    protected final String repoKey;
    protected String passphrase = null;
    protected long timestamp;
    protected final boolean isIndexEntireRepo;

    public DpkgCalculationEvent(String repoKey, boolean isIndexEntireRepo) {
        this.repoKey = repoKey;
        this.timestamp = System.currentTimeMillis();
        this.isIndexEntireRepo = isIndexEntireRepo;
    }

    public String getRepoKey() {
        return repoKey;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isIndexEntireRepo() {
        return isIndexEntireRepo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DpkgCalculationEvent)) return false;

        DpkgCalculationEvent that = (DpkgCalculationEvent) o;
        return getRepoKey() != null ? getRepoKey().equals(that.getRepoKey()) : that.getRepoKey() == null;
    }

    @Override
    public int hashCode() {
        return getRepoKey() != null ? getRepoKey().hashCode() : 0;
    }
}
