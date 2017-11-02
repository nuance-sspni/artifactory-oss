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

package org.artifactory.storage.db.aql.service;

import org.artifactory.aql.result.AqlEagerResult;
import org.testng.annotations.Test;

/**
 * @author Gidi Shabat
 */
public class AqlArchiveTest  extends AqlAbstractServiceTest {

    @Test
    public void archiveWithBuilds() {
        AqlEagerResult queryResult = aqlService.executeQueryEager(
                "archive.entries.find({\"archive.item.repo\" : \"repo1\"," +
                        "\"archive.item.artifact.module.build.name\" : {\"$match\":\"*\"}})");
        assertSize(queryResult, 1);
        assertArchive(queryResult,  "path","file.file");
    }

    @Test
    public void buildsWithArchive() {
        AqlEagerResult queryResult = aqlService.executeQueryEager(
                "builds.find({\"module.artifact.item.archive.entry.name\" :{\"$match\":\"*\"}})");
        assertSize(queryResult, 3);
        assertBuild(queryResult, "ba", "1");
        assertBuild(queryResult, "bb", "1");
        assertBuild(queryResult, "ba", "2");
    }

    @Test
    public void archiveWithBuildsWithInclude() {
        AqlEagerResult queryResult = aqlService.executeQueryEager(
                "archive.entries.find({\"archive.item.repo\" : \"repo1\"," +
                        "\"archive.item.artifact.module.build.name\" : {\"$match\":\"*\"}})" +
                        ".include(\"archive.item\")");
        assertSize(queryResult, 1);
        assertArchive(queryResult,  "path","file.file");
    }

}