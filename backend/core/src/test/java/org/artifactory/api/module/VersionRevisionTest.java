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

package org.artifactory.api.module;

import com.google.common.base.Strings;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

/**
 * Tests that the environment version/revision matches the real Artifactory version/revision
 * This test prevents entering wrong revision/version numbers when publishing Artifactory
 *
 * @author nadavy
 */
@Test
public class VersionRevisionTest extends ArtifactoryHomeBoundTest {

    public void versionRevisionMatchTest() throws IOException {
        String envRevision = System.getProperty("buildNumber.prop");
        String envVersion = System.getProperty("project.version.prop");
        if (Strings.isNullOrEmpty(envRevision) || Strings.isNullOrEmpty(envVersion)) {
            return;
        }
        ArtifactorySystemProperties artifactoryProperties = getBound().getArtifactoryProperties();
        String artifactoryRevision = artifactoryProperties.getProperty(ConstantValues.artifactoryRevision);
        String artifactoryVersion = artifactoryProperties.getProperty(ConstantValues.artifactoryVersion);
        if (artifactoryRevision.equals("${buildNumber.prop}") || artifactoryVersion.endsWith("-SNAPSHOT")) {
            return;
        }
        assertEquals(envRevision, artifactoryRevision, "Artifactory revision number doesn't match");
        assertEquals(envVersion, artifactoryVersion, "Artifactory version doesn't match");
    }
}
