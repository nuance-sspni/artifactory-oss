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

import org.apache.commons.lang.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.DbVersionDataAccessObject;
import org.artifactory.common.config.db.TemporaryDBChannel;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.version.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

import static java.lang.String.valueOf;

/**
 * @author Gidi Shabat
 */
public class VersionProviderImpl implements VersionProvider {
    private static final Logger log = LoggerFactory.getLogger(VersionProviderImpl.class);

    // Test use only
    private static final String FROM_VERSION_OVERRIDE_SYSTEM_PROP = "artifactory.debug.fromVersion";

    /**
     * The current running version, discovered during runtime.
     */
    private final CompoundVersionDetails runningVersion;

    /**
     * Denotes the original version this instance is coming from, if there was one, null if no previous version data
     * was available.
     */
    private CompoundVersionDetails originalDbVersion;

    private ArtifactoryHome artifactoryHome;
    private CompoundVersionDetails originalHomeVersion;

    public VersionProviderImpl(ArtifactoryHome artifactoryHome) {
        this.artifactoryHome = artifactoryHome;
        this.runningVersion = artifactoryHome.getRunningArtifactoryVersion();
    }

    @Override
    public void initOriginalHomeVersion() {
        resolveOriginalVersionFromOverridePropertyForTest();
        if (originalHomeVersion != null) {
            //Original version resolved from debug property - good to go.
            return;
        }
        try {
            loadOriginalHomeVersion();
            verifyOriginalHomeVersion();
            log.debug("Last Artifactory database version is: {}", originalHomeVersion == null ? "New Installation" :
                    originalHomeVersion.getVersion().name());
        } catch (Exception e) {
            log.error("Failed to resolve version information: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Loads the original version from the database.
     */
    @Override
    public void init() {
        // Used for test only!
        resolveOriginalVersionFromOverridePropertyForTest();
        if (originalDbVersion != null) {
            //Original version resolved from debug property - good to go.
            return;
        }
        try {
            loadOriginalDbVersion();
            verifyOriginalDbVersion();
            log.debug("Last Artifactory database version is: {}", originalDbVersion == null ? "New Installation" :
                    originalDbVersion.getVersion().name());
        } catch (Exception e) {
            log.error("Failed to resolve version information: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void loadOriginalDbVersion() {
        DbVersionInfo dbVersionInfo = getVersionInfoFromDbIfExists();
        // Handling the version is based on the existence of the db_properties table
        if (dbVersionInfo != null) {
            populateExistingVersionFromDb(dbVersionInfo);
        } else {
            log.debug("Resolved original db version '{}' from existing local artifactory.properties file",
                    originalHomeVersion.getVersion().name());
            originalDbVersion = originalHomeVersion;
        }
    }

    /**
     *  Populates version information from the db version information if it exists.
     */
    private void populateExistingVersionFromDb(DbVersionInfo dbProperties) {
        if (dbProperties == null) {
            // Special case for test - use latest version
            if (Boolean.valueOf(System.getProperty(ConstantValues.test.getPropertyName()))) {
                //TODO [by dan]: might need to revert this to whatever was here before if tests break
                // If test get the version before the latest one to force most current conversion to run on the tests
                ArtifactoryVersion[] versions = ArtifactoryVersion.values();
                ArtifactoryVersion testVersionForNextConversion = versions[versions.length - 1];
                String version = testVersionForNextConversion.name();
                String revision = valueOf(testVersionForNextConversion.getRevision());
                //TODO [by dan]: do we care about the timestamp for tests?
                originalDbVersion = new CompoundVersionDetails(ArtifactoryVersion.getCurrent(), version, revision, 0L);
                log.debug("Resolved original db version '{}' from db", originalHomeVersion.getVersion().name());
            } else {
                log.warn("db_properties table exists in database but contains no information - version information will"
                        + " be resolved based on the local filesystem if such information exists.");
            }
        } else {
            originalDbVersion = getDbCompoundVersionDetails(dbProperties);
        }
    }

    /**
     * Provides an access interface to the db_properties table, depending on the state of the application's context
     * returns null if no version information is stored in the db or if the table does not exist
     */
    private DbVersionInfo getVersionInfoFromDbIfExists() {
        DbVersionInfo versionInfo = null;
        // Context is null --> still in conversion phase, need to use temp db channel
        if (ContextHelper.get() == null) {
            versionInfo = tryToResolveFromDbUsingTempChannel();
        } else {
            try {
                ArtifactoryCommonDbPropertiesService dbPropsService =
                        ContextHelper.get().beanForType(ArtifactoryCommonDbPropertiesService.class);
                if (dbPropsService.isDbPropertiesTableExists()) {
                    versionInfo = dbPropsService.getDbVersionInfo();
                }
            } catch (Exception e) {
                //TODO [by dan]: this is a hack to stabilize tests only, don't release with this here
                log.warn("Failed to retrieve version information from DB using DbPropertiesService");
                if (Boolean.valueOf(System.getProperty(ConstantValues.test.getPropertyName()))) {
                    versionInfo = tryToResolveFromDbUsingTempChannel();
                } else {
                    throw e;
                }
            }
        }
        return versionInfo;
    }

    private void loadOriginalHomeVersion() {
        File artifactoryPropertiesFile = artifactoryHome.getArtifactoryPropertiesFile();
        if (!artifactoryPropertiesFile.exists()) {
            // Conversion was probably not done yet, try to get the file from the previous location
            artifactoryPropertiesFile = artifactoryHome.getArtifactoryOldPropertiesFile();
            if (!artifactoryPropertiesFile.exists()) {
                // If the properties file doesn't exists, then create it in the new location
                artifactoryPropertiesFile = artifactoryHome.getArtifactoryPropertiesFile();
                artifactoryHome.writeArtifactoryProperties();
            }
        }
        // Load the original home version
        originalHomeVersion = ArtifactoryVersionReader.read(artifactoryPropertiesFile);
    }

    private DbVersionInfo tryToResolveFromDbUsingTempChannel() {
        DbVersionDataAccessObject versionInfoDao = null;
        DbVersionInfo versionInfo = null;
        // This will run after local environment conversion for sure so we should have db.properties by now
        // if this was an upgrade, null means this is a new installation.
        ArtifactoryDbProperties dbConfig = artifactoryHome.getDBProperties();
        if (dbConfig != null) {
            try {
                TemporaryDBChannel dbChannel = new TemporaryDBChannel(dbConfig);
                versionInfoDao = new DbVersionDataAccessObject(dbChannel);
                if (versionInfoDao.isDbPropertiesTableExists()) {
                    versionInfo = versionInfoDao.getDbVersion();
                }
            } finally {
                if (versionInfoDao != null) {
                    versionInfoDao.destroy();
                }
            }
        }
        return versionInfo;
    }

    @Override
    public CompoundVersionDetails getRunning() {
        return runningVersion;
    }

    /**
     * The originalServiceVersion value is null until access to db is allowed
     */
    public CompoundVersionDetails getOriginalDbVersion() {
        return originalDbVersion;
    }

    @Nullable
    @Override
    public CompoundVersionDetails getOriginalHomeVersion() {
        return originalHomeVersion;
    }


    /**
     * <b>Used for test purposes</b>, gives you the ability to control the original version (and thus what conversions will
     * run) by using the {@link this#FROM_VERSION_OVERRIDE_SYSTEM_PROP} property that you can pass to the jvm when
     * starting the instance.
     */
    private void resolveOriginalVersionFromOverridePropertyForTest() {
        String versionOverride = System.getProperty(FROM_VERSION_OVERRIDE_SYSTEM_PROP);
        if (StringUtils.isNotBlank(versionOverride)) {
            log.warn("Version override property detected - 'From Version' will be set to {}", versionOverride);
            ArtifactoryVersion resolvedFromVersion;
            try {
                resolvedFromVersion = ArtifactoryVersion.valueOf(versionOverride);
                originalDbVersion = new CompoundVersionDetails(resolvedFromVersion,
                        resolvedFromVersion.getValue(), Long.toString(resolvedFromVersion.getRevision()), 0L);
                originalHomeVersion = originalDbVersion;
            } catch (IllegalArgumentException iae) {
                log.error("Bad version value {} !", versionOverride);
            }
        }
    }

    private CompoundVersionDetails getDbCompoundVersionDetails(DbVersionInfo dbProperties) {
        return ArtifactoryVersionReader.getCompoundVersionDetails(
                dbProperties.getArtifactoryVersion(), getRevisionStringFromInt(dbProperties.getArtifactoryRevision()),
                "" + dbProperties.getArtifactoryRelease());
    }

    private String getRevisionStringFromInt(int rev) {
        if (rev <= 0 || rev == Integer.MAX_VALUE) {
            return "" + Integer.MAX_VALUE;
        }
        return "" + rev;
    }

    private void verifyOriginalHomeVersion() {
        if (originalHomeVersion == null) {
            //New installation, nothing to verify
            return;
        }
        ArtifactoryVersion original = originalHomeVersion.getVersion();
        ArtifactoryVersion running = runningVersion.getVersion();
        doVerification(original, running);
    }

    private void verifyOriginalDbVersion() {
        if (originalDbVersion == null) {
            //New installation, nothing to verify
            return;
        }
        ArtifactoryVersion original = originalDbVersion.getVersion();
        ArtifactoryVersion running = runningVersion.getVersion();
        doVerification(original, running);
    }

    private void doVerification(ArtifactoryVersion original, ArtifactoryVersion running) {
        if (!running.equals(original)) {
            // the version written in the jar and the version read from the data directory/DB are different
            // make sure the version from the data directory/DB is supported by the current deployed artifactory
            ConfigVersion actualConfigVersion = ConfigVersion.findCompatibleVersion(original);
            //No compatible version -> conversion needed, but supported only from v4 onward
            if (!actualConfigVersion.isCurrent()) {
                String msg = "The stored version for (" + original.getValue() + ") " +
                        "is not up-to-date with the currently deployed Artifactory (" + running + ")";
                if (!actualConfigVersion.isAutoUpdateCapable()) {
                    //Cannot convert
                    msg += ": no automatic conversion is possible. Exiting now...";
                    throw new IllegalStateException(msg);
                }
            }
        }
    }


}