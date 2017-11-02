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

package org.artifactory.addon.ha;

import org.artifactory.addon.Addon;
import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.addon.ha.message.HaMessageTopic;
import org.artifactory.addon.ha.propagation.StringContentPropagationResult;
import org.artifactory.addon.ha.propagation.uideploy.UIDeployPropagationResult;
import org.artifactory.addon.ha.semaphore.SemaphoreWrapper;
import org.artifactory.repo.RepoPath;
import org.artifactory.security.props.auth.CacheWrapper;
import org.artifactory.security.props.auth.CacheWrapper.CacheConfig;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.jfrog.storage.common.LockingMapFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mamo, gidi, fred
 */
public interface HaCommonAddon extends Addon {

    /**
     * Response header for an HA Artifactory Node
     */
    String ARTIFACTORY_NODE_ID = "X-Artifactory-Node-Id";

    /**
     * Server ID in case of a non HA Artifactory node
     */
    String ARTIFACTORY_PRO = "Artifactory";

    String SUPPORT_BUNDLE_SEMAPHORE_NAME = "supportBundleSemaphore";
    String STATS_SEMAPHORE_NAME = "flushStatsSemaphore";
    String STATS_REMOTE_SEMAPHORE_NAME = "flushRemoteStatsSemaphore";
    String INDEXING_SEMAPHORE_NAME = "indexingSemaphore";
    String INDEX_MARKED_ARCHIVES_SEMAPHORE_NAME = "indexMarkedArchivesSemaphore";
    String XRAY_EVENTS_SEMAPHORE_NAME = "xrayEventsSemaphore";
    String CREATE_BOOTSTRAP_BUNDLE_EVENT = "createBootstrapBundle";

    int DEFAULT_SEMAPHORE_PERMITS = 1;

    // we block concurrent executions to prevent race condition
    // between delete(async) and list(),
    //
    // also there is no justification to perform concurrent execution
    // as we support taking several thread dumps with or without interval
    int SUPPORT_BUNDLE_SEMAPHORE_INITIAL_PERMITS = 1;

    /**
     * Determines if HA is enabled,
     * <p>that is {@link org.artifactory.common.ArtifactoryHome#ARTIFACTORY_HA_NODE_PROPERTIES_FILE} exists.
     *
     * @return {@code true} if the current Artifactory instance is HA enabled
     */
    boolean isHaEnabled();

    /**
     * @return {@code true} if HA is enabled and activated, and current Artifactory instance is the primary.
     *         <p>is HA is <b>not enabled</b>, return true
     */
    boolean isPrimary();

    /**
     * @return {@code true} if HA is configured.
     */
    boolean isHaConfigured();

    //todo move into HaAddon, should not be a common interface
    void notify(HaMessageTopic haMessageTopic, HaMessage haMessage);

    /**
     * @return A unique hostId for a Pro/OSS installation, a hashed token for an HA node
     */
    String getHostId();

    SemaphoreWrapper getSemaphore(String semaphoreName);

    <K, V> CacheWrapper<K, V> getCache(String cacheName, CacheConfig cacheConfig);

    <K, V> Map<K, V> getConcurrentMap(String mapName);

    void shutdown();

    List<ArtifactoryServer> getAllArtifactoryServers();

    boolean deleteArtifactoryServer(String id);

    boolean artifactoryServerHasHeartbeat(ArtifactoryServer artifactoryServer);

    String getCurrentMemberServerId();

    void propagateDebianReindexAll(ArtifactoryServer server, String repoKey, boolean async, boolean writeProps);

    /**
     * Send a request to the primary node in order to keep the cache always up-to-date (RTFACT-13432)
     * @param server the primary node
     * @param path path to the file recently changed\deleted
     */
    void propagateDebianUpdateCache(ArtifactoryServer server, RepoPath path);

    void propagateOpkgReindexAll(ArtifactoryServer server, String repoKey, boolean async, boolean writeProps);

    /**
     * In case where a UI upload request was started on another node (which means the file is in its local temp dir)
     * this call will propagate the second deploy request (when user presses the 'upload' button) to the relevant
     * {@param nodeId}.
     * @param payload       - The deploy request
     */
    UIDeployPropagationResult propagateUiUploadRequest(String nodeId, String payload);

    StringContentPropagationResult propagateCreateBootstrapBundleRequestToPrimary();

    void propagateMasterEncryptionKeyChanged();

    void propagateActivateLicense(ArtifactoryServer server, Set<String> skipLicense);

    void propagatePluginReload();

    /**
     * Propagate request to retrieve the list of previously created bundles from the HA cluster members
     */
    List<String> propagateSupportBundleListRequest();

    boolean propagateDeleteSupportBundle(String bundleName);

    /**
     * Propagate request to download a support bundle archive to another HA cluster specific member
     *
     * @param nodeId target nodeId
     * @return inputstream of the support bundle archive
     */
    InputStream propagateSupportBundleDownloadRequest(String bundleName, String nodeId);

    <T> List<T> propagateTrafficCollector(long startLong, long endLong, List<String> ipsToFilter,
            List<ArtifactoryServer> servers, Class<T> clazz) ;

    <T> List<T> propagateTasksList(List<ArtifactoryServer> servers, Class<T> clazz);

    void forceOptimizationOnce();

    LockingMapFactory getLockingMapFactory();

    void propagateConfigReload();

    void updateArtifactoryServerRole();

    //TODO [by dan]: all such propagation calls that respond to changes in the local filesystem should register as
    //TODO [by dan]: listeners on the ConfigWrapper instead!
    /**
     * Used to inform other nodes that the artifactory.cluster.licenses file has changed and a reload of the license
     * cache is required
     */
    void propagateLicenseChanges();
}
