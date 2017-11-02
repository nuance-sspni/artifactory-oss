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

package org.artifactory.common;

import java.util.concurrent.TimeUnit;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * @author freds
 * Oct 10, 2008
 */
@SuppressWarnings({"EnumeratedConstantNamingConvention"})
public enum ConstantValues {
    test("runMode.test", FALSE), //Use and set only in specific itests - has serious performance implications
    qa("runMode.qa", FALSE),
    dev("runMode.dev", FALSE),
    devHa("runMode.devHa", FALSE),
    artifactoryVersion("version"),
    artifactoryRevision("revision"),
    artifactoryTimestamp("timestamp"),
    supportUrlSessionTracking("servlet.supportUrlSessionTracking", FALSE),
    disabledAddons("addons.disabled", ""),
    addonsInfoUrl("addons.info.url", "http://service.jfrog.org/artifactory/addons/info/%s"),
    addonsConfigureUrl("addons.info.url", "http://www.jfrog.com/confluence/display/RTF/%s"),
    springConfigDir("spring.configDir"),
    asyncCorePoolSize("async.corePoolSize", 4 * Runtime.getRuntime().availableProcessors()),
    asyncPoolTtlSecs("async.poolTtlSecs", 60),
    asyncPoolMaxQueueSize("async.poolMaxQueueSize", 10000),
    versioningQueryIntervalSecs("versioningQueryIntervalSecs", Seconds.HOUR * 2),
    logsViewRefreshRateSecs("logs.viewRefreshRateSecs", 10),
    locksTimeoutSecs("locks.timeoutSecs", 120),
    locksDebugTimeouts("locks.debugTimeouts", FALSE),
    taskCompletionLockTimeoutRetries("task.completionLockTimeoutRetries", 100),
    substituteRepoKeys("repo.key.subst."),
    repoConcurrentDownloadSyncTimeoutSecs("repo.concurrentDownloadSyncTimeoutSecs", Seconds.MINUTE * 15),
    downloadStatsEnabled("repo.downloadStatsEnabled", TRUE),
    disableGlobalRepoAccess("repo.global.disabled", TRUE),
    fsItemCacheIdleTimeSecs("fsitem.cache.idleTimeSecs", Seconds.MINUTE * 20),
    dockerTokensCacheIdleTimeSecs("docker.tokens.cache.idleTimeSecs", Seconds.MINUTE * 4),
    genericTokensCacheIdleTimeSecs("artifactory.tokens.cache.idleTimeSecs", Seconds.MINUTE * 10),
    cacheFSSyncquietPeriodSecs("cacheFS.sync.quietPeriodSecs", Seconds.MINUTE * 15),
    searchMaxResults("search.maxResults", 500),
    searchUserQueryLimit("search.userQueryLimit", 1000),
    searchMaxFragments("search.content.maxFragments", 500),
    searchMaxFragmentsSize("search.content.maxFragmentsSize", 5000),
    searchArchiveMinQueryLength("search.archive.minQueryLength", 3),
    searchPatternTimeoutSecs("search.pattern.timeoutSecs", 30),
    gcUseIndex("gc.useIndex", FALSE),
    gcIntervalSecs("gc.intervalSecs", Seconds.DAY),
    gcDelaySecs("gc.delaySecs", Seconds.HOUR * 2),
    gcSleepBetweenNodesMillis("gc.sleepBetweenNodesMillis", 20),
    gcScanStartSleepingThresholdMillis("gc.scanStartSleepingThresholdMillis", 20000),
    gcScanSleepBetweenIterationsMillis("gc.scanSleepBetweenIterationsMillis", 200),
    gcFileScanSleepIterationMillis("gc.fileScanSleepIterationMillis", 1000),
    gcFileScanSleepMillis("gc.fileScanSleepMillis", 250),
    gcMaxCacheEntries("gc.maxCacheEntries", 10000),
    gcReadersMaxTimeSecs("gc.readersMaxTimeSecs", Seconds.HOUR * 3),
    gcFailCountThreshold("gc.failCount.threshold", 3),
    trafficCollectionActive("traffic.collectionActive", FALSE),
    securityAuthenticationCacheInitSize("security.authentication.cache.initSize", 100),
    securityAuthenticationCacheIdleTimeSecs("security.authentication.cache.idleTimeSecs", Seconds.MINUTE * 5),
    userLastAccessUpdatesResolutionSecs("security.userLastAccessUpdatesResolutionSecs", 5),
    securityMasterKeyLocation("security.master.key", "security/artifactory.key"),
    securityMasterKeyNumOfFallbackKeys("security.master.key.numOfFallbackKeys", 3),
    securityDisableRememberMe("security.disableRememberMe", FALSE),
    ldapForceGroupMemberAttFullDN("security.ldap.forceGroupMemberAttFullDN", FALSE),
    ldapDisableGroupSearchAttributesLimitation("security.ldap.disable.group.search.attributes.limitation", FALSE),
    ldapGroupNamesSearchFilterThreshold("security.ldap.group.search.filterThreshold", 0),
    enableAqlReadCommitted("enable.aql.read.committed", FALSE),
    mvnCentralHostPattern("mvn.central.hostPattern", ".maven.org"),
    mvnCentralIndexerMaxQueryIntervalSecs("mvn.central.indexerMaxQueryIntervalSecs", Seconds.DAY),
    mvnMetadataPluginCalculationWorkers("mvn.metadata.plugin.calculation.workers", 2),
    mvnMetadataCalculationWorkers("mvn.metadata.calculation.workers", 8),
    mvnMetadataVersionsComparator("mvn.metadataVersionsComparatorFqn"),
    mvnMetadataSnapshotComparator("mvn.metadataSnapshotComparatorFqn"),
    mvnDynamicMetadataCacheRetentionSecs("mvn.dynamicMetadata.cacheRetentionSecs", 10),
    mvnMetadataVersion3Enabled("mvn.metadata.version3.enabled", TRUE),
    mvnCustomTypes("mvn.custom.types", "tar.gz,tar.bz2"),
    requestDisableVersionTokens("request.disableVersionTokens", FALSE),
    requestSearchLatestReleaseByDateCreated("request.searchLatestReleaseByDateCreated", FALSE),
    npmTagLatestByPublish("npm.tag.tagLatestByPublish", FALSE),
    buildMaxFoldersToScanForDeletionWarnings("build.maxFoldersToScanForDeletionWarnings", 2),
    missingBuildChecksumCacheIdeTimeSecs("build.checksum.cache.idleTimeSecs", Seconds.MINUTE * 5),
    artifactoryUpdatesRefreshIntervalSecs("updates.refreshIntervalSecs", Seconds.HOUR * 4),
    artifactoryUpdatesUrl("updates.url", "http://service.jfrog.org/artifactory/updates"),
    artifactoryRequestsToGlobalCanRetrieveRemoteArtifacts("artifactoryRequestsToGlobalCanRetrieveRemoteArtifacts", FALSE),
    uiSyntaxColoringMaxTextSizeBytes("ui.syntaxColoringMaxTextSizeBytes", 512000),
    pluginScriptsRefreshIntervalSecs("plugin.scripts.refreshIntervalSecs", 0),
    aolPluginSupport("plugin.aol.support", FALSE),
    aolDedicatedServer("aol.dedicated.server", FALSE),
    aolDisplayAccountManagementLink("aol.displayAccountManagementLink", TRUE),
    aolSecurityHttpSsoEnabled("aol.security.http.sso.enable",FALSE),
    uiChroot("ui.chroot"),
    artifactoryLicenseDir("licenseDir"),
    fileRollerMaxFilesToRetain("file.roller.maxFileToRetain", 10),
    backupFileExportSleepIterationMillis("backup.fileExportSleepIterationMillis", 2000),
    backupFileExportSleepMillis("backup.fileExportSleepMillis", 250),
    s3backupBucket("backup.s3.bucket"),
    s3backupFolder("backup.s3.folder"),
    s3backupAccountId("backup.s3.accountId"),
    s3existsCheckAfterAddingStream("s3.existsCheckAfterAddingStream", true),
    s3backupAccountSecretKey("backup.s3.accountSecretKey"),
    httpAcceptEncodingGzip("http.acceptEncoding.gzip", true),
    httpUseExpectContinue("http.useExpectContinue", false),
    httpForceForbiddenResponse("http.forceForbiddenResponse", FALSE),
    httpConnectionPoolTimeToLive("http.connectionPool.timeToLive", 30),
    enableCookieManagement("http.enableCookieManagement", false),
    filteringResourceSizeKb("filtering.resourceSizeKb", 64),
    searchForExistingResourceOnRemoteRequest("repo.remote.checkForExistingResourceOnRequest", TRUE),
    versionQueryEnabled("version.query.enabled", true),
    hostId("host.id"),
    responseDisableContentDispositionFilename("response.disableContentDispositionFilename", FALSE),
    composerMetadataExtractorWorkers("composer.metadata.extractor.workers", 20),
    composerMetadataIndexWorkers("composer.metadata.index.workers", 10),
    chefMetadataIndexWorkers("chef.metadata.index.workers", 10),
    yumVirtualMetadataCalculationWorkers("yum.virtual.metadata.calculation.workers", 5),
    yumCalculationRequestAggregationTimeWindowSecs("yum.calculationRequest.aggregationTimeWindowSecs", 60),
    yumCalculationRequestAggregationCycleSecs("yum.calculationRequest.aggregationCycleSecs", 60),
    debianMetadataCalculationWorkers("debian.metadata.calculation.workers", 8),
    debianRemoteETagSupport("debian.remote.etag", TRUE),
    debianMetadataValidation("debian.metadata.validation", true),
    debianMetadataMd5InPackages("debian.metadata.calculateMd5InPackagesFiles", false),
    globalExcludes("repo.includeExclude.globalExcludes"),
    archiveLicenseFileNames("archive.licenseFile.names", "license,LICENSE,license.txt,LICENSE.txt,LICENSE.TXT"),
    uiSearchMaxRowsPerPage("ui.search.maxRowsPerPage", 20),
    replicationChecksumDeployMinSizeKb("replication.checksumDeploy.minSizeKb", 10),
    replicationConsumerQueueSize("replication.consumer.queueSize", 1),
    replicationLocalIterationSleepThresholdMillis("replication.local.iteration.sleepThresholdMillis", 1000),
    replicationLocalIterationSleepMillis("replication.local.iteration.sleepMillis", 100),
    replicationEventQueueSize("replication.event.queue.size", 50000),
    replicationPropertiesMaxLength("replication.properties.max.length", 100000),
    replicationStatisticsMaxLength("replication.statistics.max.length", 5000),
    replicationInitContextTaskIntervalSecs("replication.initContext.task.intervalSecs", 60),
    requestExplodedArchiveExtensions("request.explodedArchiveExtensions", "zip,tar,tar.gz,tgz"),
    jCenterUrl("bintray.jcenter.url", "http://jcenter.bintray.com"),
    bintrayUrl("bintray.url", "https://bintray.com"),
    bintrayApiUrl("bintray.api.url", "https://api.bintray.com"),
    bintrayOAuthTokenExpirySeconds("bintray.token.expirySecs", 3600),
    bintrayDistributionRegexTimeoutMillis("bintray.distributionRegex.timeoutMillis", 180000),
    bintrayUIHideUploads("bintray.ui.hideUploads", FALSE),
    bintrayUIHideInfo("bintray.ui.hideInfo", FALSE),
    bintrayUIHideRemoteSearch("bintray.ui.hideRemoteSearch", FALSE),
    bintraySystemUser("bintray.system.user"),
    bintraySystemUserApiKey("bintray.system.api.key"),
    bintrayClientThreadPoolSize("bintray.client.threadPool.size", 5),
    enableUiPagesInIframe("enable.ui.pages.in.Iframe", false),
    bintrayClientRequestTimeout("bintray.client.requestTimeoutMS",150000),
    bintrayClientDistributionRequestTimeout("bintray.client.distribution.requestTimeoutMS", 30000),
    bintrayClientSignRequestTimeout("bintray.client.signRequestTimeoutMS", 45000),
    useUserNameAutoCompleteOnLogin("useUserNameAutoCompleteOnLogin", "on"),
    uiHideEncryptedPassword("ui.hideEncryptedPassword", FALSE),
    statsFlushIntervalSecs("stats.flushIntervalSecs", 30),
    statsRemoteFlushIntervalSecs("stats.remote.flushIntervalSecs", 35),
    statsFlushTimeoutSecs("stats.flushTimeoutSecs", 120),
    integrationCleanupIntervalSecs("integrationCleanup.intervalSecs", 300),
    integrationCleanupQuietPeriodSecs("integrationCleanup.quietPeriodSecs", 60),
    folderPruningIntervalSecs("folderPruning.intervalSecs", 300),
    folderPruningQuietPeriodSecs("folderPruning.quietPeriodSecs", 60),
    virtualCleanupMaxAgeHours("repo.virtualCacheCleanup.maxAgeHours", 168),
    virtualCleanupNamePattern("repo.virtualCacheCleanup.pattern", "*.pom"),
    defaultSaltValue("security.authentication.password.salt", "CAFEBABEEBABEFAC"),
    dbIdGeneratorFetchAmount("db.idGenerator.fetch.amount", 2000),
    dbIdGeneratorMaxUpdateRetries("db.idGenerator.max.update.retries", 50),
    gemsLocalIndexTaskIntervalSecs("gems.localIndexTaskIntervalSecs", 30),
    gemsVirtualIndexTaskIntervalSecs("gems.virtualIndexTaskIntervalSecs", 300),
    gemsIndexTaskQueueLimit("gems.gemsIndexTaskQueueLimit", 20000),
    gemsAfterRepoInitHack("gems.gemsAfterRepoInitHack", true),
    securityCrowdGroupStartIndex("security.authentication.crowd.group.startIndex", 0),
    securityCrowdMaxGroupResults("security.authentication.crowd.group.maxResults", 9999),
    uiHideChecksums("ui.hideChecksums", FALSE),
    archiveIndexerTaskIntervalSecs("archive.indexer.intervalSecs", 60),
    xrayIndexerTaskIntervalSecs("xray.indexer.intervalSecs", 60),
    xrayForceReindex("xray.force.reindex", false),
    xrayScanBuildMaxConnections("xray.scanBuild.httpClient.max.connections", 30),
    inMemoryNuGetRemoteCaches("nuget.inMemoryRemoteCaches", TRUE),
    nuGetRequireAuthentication("nuget.forceAuthentication", FALSE),
    nuGetAllowRootGetWithAnon("nuget.allowRootGetWithAnon", FALSE),
    haHeartbeatIntervalSecs("ha.heartbeat.intervalSecs", 5),
    haHeartbeatStaleIntervalSecs("ha.heartbeat.staleSecs", 30),
    haHeartbeatRecentlyWorkedTriggerDays("ha.heartbeat.recently.worked.trigger.days", 7),
    binaryStoreErrorNotificationsIntervalSecs("binary.store.error.notification.intervalSecs", 30),
    binaryStoreErrorNotificationsStaleIntervalSecs("binary.store.error.notification.staleSecs", 30),
    haMembersIntroductionIntervalSecs("ha.membersIntroduction.intervalSecs", 30),
    haMembersIntroductionStaleIntervalSecs("ha.membersIntroduction.staleSecs", 30),
    npmIndexQuietPeriodSecs("npm.index.quietPeriodSecs", 60),
    npmIndexCycleSecs("npm.index.cycleSecs", 60),
    importMaxParallelRepos("import.max.parallelRepos", Runtime.getRuntime().availableProcessors() - 1),
    debianDistributionPath("debian.distribution.path", "dists"),
    opkgIndexQuietPeriodSecs("opkg.index.quietPeriodSecs", 60),
    opkgIndexCycleSecs("opkg.index.cycleSecs", 2),
    debianDefaultArchitectures("debian.default.architectures", "i386,amd64"),
    pypiIndexQuietPeriodSecs("pypi.index.quietPeriodSecs", 60),
    pypiIndexSleepSecs("pypi.index.sleepMilliSecs", 60),
    dockerCleanupMaxAgeMillis("docker.cleanup.maxAgeMillis", Seconds.DAY * 1000),
    dockerTagsCleanupIntervalSecs("docker.tags.cleanup.intervalSecs", 300),
    dockerTagsCleanupQuietPeriodSecs("docker.tags.cleanup.quietPeriodSecs", 60),
    httpRangeSupport("http.range.support", true),
    aclDirtyReadsTimeout("acl.dirty.read.timeout", 20000),
    centralConfigDirtyReadsTimeoutMillis("central.config.dirty.read.timeout.millis", 2000),
    repositoriesDirtyReadsTimeoutMillis("repositories.dirty.read.timeout.millis", 5000),
    allowUnauthenticatedPing("ping.allowUnauthenticated", FALSE), // in milliseconds
    idleConnectionMonitorInterval("repo.http.idleConnectionMonitorInterval", 10),
    disableIdleConnectionMonitoring("repo.http.disableIdleConnectionMonitoring", FALSE),
    contentCollectionAwaitTimeout("support.core.bundle.contentCollectionAwaitTimeout", 60),
    waitForSlotBeforeWithdraw("support.core.bundle.waitForSlotBeforeWithdraw", 600),
    maxBundles("support.core.bundle.maxBundles", 5),
    testCallHomeCron("post.jobs.callHome.cron", null),
    //periodicReportJob("periodic.report.cron", TimeUnit.DAYS.toMillis(1)),
    binaryProviderZones("binary.provider.zones","a,b,c"),
    binaryProviderPruneChunkSize("binary.provider.prune.chunk.size", 500),
    propertiesSearchChunkSize("properties.search.chunk.size", 500),
    useFrontCacheForBlockedUsers("security.useFrontCacheForBlockedUsers", true),
    loginBlockDelay("security.loginBlockDelay", 500),
    passwordExpireNotificationJobIntervalSecs("security.password.expiry.passwordExpireNotificationJobIntervalSecs", 86400),
    passwordExpireJobIntervalSecs("security.password.expiry.passwordExpireJobIntervalSecs", 43200),
    passwordDaysToNotifyBeforeExpiry("security.password.expiry.daysToNotifyBefore", 5),
    httpClientMaxTotalConnections("http.client.max.total.connections", 50),
    httpClientMaxConnectionsPerRoute("http.client.max.connections.per.route", 50),
    hazelcastMaxLockLeaseTime("hazelcast.max.lock.lease.time.minutes", 30),
    blockedMismatchingMimeTypes("repo.remote.blockedMismatchingMimeTypes","text/html,application/xhtml+xml"),
    mvnMetadataCalculationSkipDeleteEvent("mvn.metadata.calculation.skip.delete.event", false),
    remoteBrowsingContentLengthLimitKB("repo.remote.browsing.content.length.limit.KB", 1024),
    hazelcastManagement("hazelcast.management",false),
    hazelcastManagementUrl("hazelcast.management.url", null),
    sumoLogicApiUrl("sumologic.api.url", "https://auth.sumologic.com"),
    moveCopyMaxFoldersCacheSize("move.copy.max.folder.cache.size", 1000000),
    moveCopyDefaultTransactionSize("move.copy.default.transaction.size", 1000),
    nodePropertiesReplaceAll("node.properties.replace.all", false),
    nodePropertiesLogPerformance("node.properties.log.performance", false),
    workQueueSyncExecutionTimeoutMillis("workQueue.execution.syncExecutionTimeoutMillis", 120000),
    workItemMaxLockLeaseTime("workitem.max.lock.lease.time.minutes", 30),
    securityCommunicationConstant("security.communication.constant", "ArtifactorySecurityCommunicationConstant"),
    disablePermissionCheckOnNuGetSearch("nuget.disablePermissionCheck",FALSE),
    orderTreeBrowserRepositoriesByType("treebrowser.sortRepositories.sortByType","virtual,distribution,local,remote"),
    publishMavenMetadataModelVersion("maven.metadata.publishModelVersion", true),
    accessTokenExpiresInDefault("access.token.expiresIn.default", TimeUnit.HOURS.toSeconds(1)),
    accessClientTokenVerifyResultCacheSize("access.client.token.verify.result.cache.size", -1), //-1: use client default
    accessClientTokenVerifyResultCacheExpiry("access.client.token.verify.result.cache.expiry", -1), //-1: use client default
    accessClientServerUrlOverride("access.client.serverUrl.override", null), //if has value - overrides any other config
    accessClientWaitForServer("access.client.waitForServer", 60), //time in seconds to wait for access server when Artifactory starts
    accessServerBundled("access.server.bundled", null), //If has value - dictates the state (default otherwise: false)
    puppetMetadataCalculationWorkers("puppet.metadata.calculation.workers", 5),
    puppetRepoMetadataCalculationWorkers("puppet.repo.metadata.calculation.workers", 5),
    puppetReindexPeriodInSeconds("puppet.reindex.period", TimeUnit.MINUTES.toSeconds(30)),
    puppetAdditionalModuleGroups("puppet.additional.modulegroup", ""),
    puppetAdditionalEndorsements("puppet.additional.endorsement", ""),
    skipOnboardingWizard("onboarding.skipWizard", FALSE),
    configurationManagerRetryAmount("configuration.manager.retry.amount", 3),
    watchAggregationTimeWindowSecs("aggregation.time.window.secs", 60),
    mostDownloadedCacheIdleTimeSecs("most.downloaded.cache.idleTimeSecs", TimeUnit.MINUTES.toSeconds(15)),
    buildRetentionWorkers("build.retention.workers", 10),
    buildRetentionAlwaysAsync("build.retention.always.async", false);

    public static final String SYS_PROP_PREFIX = "artifactory.";

    private final String propertyName;
    private final String defValue;

    ConstantValues(String propertyName) {
        this(propertyName, null);
    }

    ConstantValues(String propertyName, Object defValue) {
        this.propertyName = SYS_PROP_PREFIX + propertyName;
        this.defValue = defValue == null ? null : defValue.toString();
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getDefValue() {
        return defValue;
    }

    public String getString() {
        return ArtifactoryHome.get().getArtifactoryProperties().getProperty(this);
    }

    public int getInt() {
        return (int) getLong();
    }

    public long getLong() {
        return ArtifactoryHome.get().getArtifactoryProperties().getLongProperty(this);
    }

    public boolean getBoolean() {
        return ArtifactoryHome.get().getArtifactoryProperties().getBooleanProperty(this);
    }

    public boolean isSet() {
        return ArtifactoryHome.get().getArtifactoryProperties().hasProperty(this);
    }

    /** * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * The following are used for when we need to get values but home is not bound (i.e. encryption operations in     *
     * post context ready state)                                                                                      *
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

    public String getString(ArtifactoryHome home) {
        return home.getArtifactoryProperties().getProperty(this);
    }

    public int getInt(ArtifactoryHome home) {
        return (int) getLong(home);
    }

    public long getLong(ArtifactoryHome home) {
        return home.getArtifactoryProperties().getLongProperty(this);
    }

    public boolean getBoolean(ArtifactoryHome home) {
        return home.getArtifactoryProperties().getBooleanProperty(this);
    }

    public boolean isSet(ArtifactoryHome home) {
        return home.getArtifactoryProperties().hasProperty(this);
    }

    private static class Seconds {
        private static final int MINUTE = 60;
        private static final int HOUR = MINUTE * 60;
        private static final int DAY = HOUR * 24;
    }
}
