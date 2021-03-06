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

package org.artifactory.ui.rest.model.admin.advanced.systemlogs;

import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.model.BaseModel;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * @author Lior Hasson
 */
public class SystemLogsInitialize extends BaseModel {
    //TODO [by dan]: audit log disabled for 4.9.0 [RTFACT-9016]
    private final List<String> logs = asList("artifactory.log", "access.log", "import.export.log", "request.log"/*, "audit.log"*/);

    private int refreshRateSecs = ConstantValues.logsViewRefreshRateSecs.getInt();

    public List<String> getLogs() {
        return logs;
    }

    public int getRefreshRateSecs() {
        return refreshRateSecs;
    }
}
