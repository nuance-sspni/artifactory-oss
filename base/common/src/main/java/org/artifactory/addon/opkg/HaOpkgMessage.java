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

package org.artifactory.addon.opkg;

import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.repo.RepoPath;

import java.util.Collection;
import java.util.Set;

/**
 * @author Dan Feldman
 */
public class HaOpkgMessage implements HaMessage {

    public static final String HA_FAILED_MSG = "Failed to send Opkg calculation message to server";

    private Set<OpkgCalculationEvent> newEvents;
    private Collection<RepoPath> propertyWriterEntries;
    private boolean async;

    public HaOpkgMessage(Set<OpkgCalculationEvent> newEvents, Collection<RepoPath> propertyWriterEntries, boolean async) {
        this.newEvents = newEvents;
        this.propertyWriterEntries = propertyWriterEntries;
        this.async = async;
    }

    public Set<OpkgCalculationEvent> getNewEvents() {
        return newEvents;
    }

    public Collection<RepoPath> getPropertyWriterEntries() {
        return propertyWriterEntries;
    }

    public boolean isAsync() {
        return async;
    }
}
