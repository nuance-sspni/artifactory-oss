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

package org.artifactory.addon.debian;

import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.repo.RepoPath;

import java.util.Collection;
import java.util.Set;

/**
 * @author Gidi Shabat
 */
public class HaDebianMessage implements HaMessage {
    private Set<DebianCalculationEvent> newEvents;
    private Collection<RepoPath> propertyWriterEntries;
    private boolean async;

    public HaDebianMessage(Set<DebianCalculationEvent> newEvents, Collection<RepoPath> propertyWriterEntries, boolean async) {
        this.newEvents = newEvents;
        this.propertyWriterEntries = propertyWriterEntries;
        this.async =async;
    }

    public Set<DebianCalculationEvent> getNewEvents() {
        return newEvents;
    }

    public Collection<RepoPath> getPropertyWriterEntries() {
        return propertyWriterEntries;
    }

    public void setNewEvents(Set<DebianCalculationEvent> newEvents) {
        this.newEvents = newEvents;
    }

    public boolean isAsync() {
        return async;
    }
}
