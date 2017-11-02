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

package org.artifactory.storage.db.upgrades.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.version.ArtifactoryVersion;
import org.testng.annotations.BeforeClass;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.convert;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v100;

/**
 * Author: gidis
 */
public abstract class ArtifactoryDBVersionTest extends UpgradeBaseTest {

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        super.springTestContextPrepareTestInstance();

        rollBackTo300Version();
        convert(getFromVersion(v100), jdbcHelper, dbProperties.getDbType());
    }

    private ArtifactoryVersion getFromVersion(ArtifactoryDBVersion version) {
        return version.getComparator().getFrom();
    }
}
