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

package org.artifactory.converter;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.test.TestUtils;
import org.artifactory.version.ArtifactoryVersion;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.artifactory.converter.helpers.ConvertersManagerTestHelper.*;
import static org.artifactory.version.ArtifactoryVersion.*;

/**
 * @author Gidi Shabat
 */
@Test
public class ConvertersManagerTest {

    @AfterMethod
    public void cleanAfterCreateEnv() {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    /**
     * Convert artifactory which home is version 3.0.1  DBProperties is 3.0.1 and cluster version is 3.0.1
     */
    public void convertAll301() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, v301, v301);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertTrue(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is version 3.0.4  DBProperties is 3.0.4 and cluster version is 3.0.4
     */
    public void convertAll304() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, v304, v304);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertTrue(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is version current  DBProperties is current and cluster version is current
     */
    public void convertAllCurrent() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryVersion current = ArtifactoryVersion.getCurrent();
        createEnvironment(home, current, current);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        Assert.assertFalse(isArtifactorySharedHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertFalse(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is version null  DBProperties is null and cluster version is null
     */
    public void convertNoVersions() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        createEnvironment(home, null, null);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertFalse(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * Convert artifactory which home is current  DBProperties is 3.0.1 and cluster version is current
     * <p/>
     * In case that the db_properties doesn't exist then take the version from home.
     * This is dangerous: in case that the home version is current no db conversion will run
     */
    public void convertCurrentHomeDb301Cluster() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryVersion current = ArtifactoryVersion.getCurrent();
        createEnvironment(home, current, v301);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        Assert.assertFalse(isArtifactorySharedHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }

    /**
     * * Convert artifactory which home is version current  DBProperties is 3.1.0 and cluster version is current
     */
    public void convertCurrentHomeOldDbCurrentCluster() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryVersion current = ArtifactoryVersion.getCurrent();
        createEnvironment(home, current, v310);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertFalse(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertTrue(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }


    /**
     * Convert artifactory which home is version 3.0.1  DBProperties is current and cluster version is current
     */
    public void convertOldHomeCurrentDbCurrentCluster() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        ArtifactoryVersion current = ArtifactoryVersion.getCurrent();
        createEnvironment(home, v301, current);
        // Make sure that the artifactory local home properties.file has been updated
        Assert.assertTrue(isArtifactoryLocalHomePropertiesHasBeenUpdated(home));
        // Make sure that the artifactory db properties has been updated
        Assert.assertFalse(isServiceConvertWasRun(home));
        // Make sure that the artifactory.properties exist in home
        Assert.assertTrue(isArtifactoryPropertiesHasBeenUpdated(home));
    }
}






