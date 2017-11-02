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

package org.artifactory.storage.db.itest;

import com.google.common.collect.Maps;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.addon.ha.message.HaMessageTopic;
import org.artifactory.addon.ha.propagation.StringContentPropagationResult;
import org.artifactory.addon.ha.propagation.uideploy.UIDeployPropagationResult;
import org.artifactory.addon.ha.semaphore.JVMSemaphoreWrapper;
import org.artifactory.addon.ha.semaphore.SemaphoreWrapper;
import org.artifactory.addon.smartrepo.SmartRepoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.ConfigurationManager;
import org.artifactory.converter.ConverterManager;
import org.artifactory.fs.StatsInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.props.auth.CacheWrapper;
import org.artifactory.security.props.auth.SimpleCacheWrapper;
import org.artifactory.spring.SpringConfigPaths;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.version.VersionProvider;
import org.jfrog.storage.common.LockingMapFactory;
import org.jfrog.storage.common.core.JvmLockingMapFactory;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @author mamo
 */
public class DummyArtifactoryContext implements ArtifactoryContext {
    private ApplicationContext applicationContext;
    private Map<Class<?>, Object> beans = Maps.newHashMap();

    public DummyArtifactoryContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void addBean(Object bean, Class<?>... types) {
        for (Class<?> type : types) {
            beans.put(type, bean);
        }
    }
    @Override
    public CentralConfigService getCentralConfig() {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T beanForType(Class<T> type) {
        //Let overridden beans go first
        if (beans.containsKey(type)) {
            return (T) beans.get(type);
        }
        if (AddonsManager.class.equals(type)) {
            return (T) new DummyOssAddonsManager();
        }
        if (type.equals(HaCommonAddon.class)) {
            return (T) new DummyHaCommonAddon();
        }
        if (type.equals(RepositoryService.class)) {
            return (T) Mockito.mock(RepositoryService.class);
        }
        if (type.equals(SmartRepoAddon.class)) {
            return (T) new SmartRepoAddon(){

                @Override
                public boolean isDefault() {
                    return false;
                }

                @Override
                public boolean supportRemoteStats() {
                    return true;
                }

                @Override
                public void fileDownloadedRemotely(StatsInfo statsInfo, String origin, RepoPath repoPath) {

                }
            };
        }
        return applicationContext.getBean(type);
    }

    @Override
    public <T> T beanForType(String name, Class<T> type) {
        return null;
    }

    @Override
    public <T> Map<String, T> beansForType(Class<T> type) {
        return null;
    }

    @Override
    public Object getBean(String name) {
        return applicationContext.getBean(name);
    }

    @Override
    public RepositoryService getRepositoryService() {
        return beanForType(RepositoryService.class);
    }

    @Override
    public AuthorizationService getAuthorizationService() {
        return null;
    }

    @Override
    public long getUptime() {
        return 0;
    }

    @Override
    public ArtifactoryHome getArtifactoryHome() {
        return null;
    }

    @Override
    public String getContextId() {
        return null;
    }

    @Override
    public SpringConfigPaths getConfigPaths() {
        return null;
    }

    @Override
    public String getServerId() {
        return null;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isOffline() {
        return false;
    }

    @Override
    public void setOffline() {
    }

    @Override
    public ConfigurationManager getConfigurationManager() {
        return null;
    }

    @Override
    public ConverterManager getConverterManager() {
        return null;
    }

    @Override
    public VersionProvider getVersionProvider() {
        return null;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void exportTo(ExportSettings settings) {
    }

    @Override
    public void importFrom(ImportSettings settings) {
    }

    private static class DummyHaCommonAddon implements HaCommonAddon {
        private LockingMapFactory lockingMapFactory = new JvmLockingMapFactory();

        @Override
        public boolean isHaEnabled() {
            return false;
        }

        @Override
        public boolean isPrimary() {
            return false;
        }

        @Override
        public boolean isHaConfigured() {
            return false;
        }

        @Override
        public void notify(HaMessageTopic haMessageTopic, HaMessage haMessage) {
        }

        @Override
        public String getHostId() {
            return null;
        }

        @Override
        public LockingMapFactory getLockingMapFactory() {
            return lockingMapFactory;
        }

        @Override
        public void propagateConfigReload() {}

        @Override
        public void updateArtifactoryServerRole() {}

        @Override
        public void propagateLicenseChanges() {

        }

        @Override
        public SemaphoreWrapper getSemaphore(String semaphoreName) {
            Semaphore semaphore = new Semaphore(HaCommonAddon.DEFAULT_SEMAPHORE_PERMITS);
            return new JVMSemaphoreWrapper(semaphore);
        }

        @Override
        public <K, V> CacheWrapper<K, V> getCache(String cacheName, CacheWrapper.CacheConfig cacheConfig) {
            return new SimpleCacheWrapper<>(cacheConfig);
        }

        @Override
        public <K, V> Map<K, V> getConcurrentMap(String mapName) {
            return new ConcurrentHashMap<>();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<ArtifactoryServer> getAllArtifactoryServers() {
            return new ArrayList<>();
        }

        @Override
        public boolean deleteArtifactoryServer(String id) {
            return false;
        }

        @Override
        public boolean artifactoryServerHasHeartbeat(ArtifactoryServer artifactoryServer) {
            return false;
        }

        @Override
        public String getCurrentMemberServerId() {
            return null;
        }

        @Override
        public void propagateDebianReindexAll(ArtifactoryServer server, String repoKey, boolean async, boolean writeProps) {}

        @Override
        public void propagateDebianUpdateCache(ArtifactoryServer server, RepoPath path) { }

        @Override
        public void propagateOpkgReindexAll(ArtifactoryServer server, String repoKey, boolean async, boolean writeProps) {}

        @Override
        public void propagateActivateLicense(ArtifactoryServer server, Set<String> skipLicense) {

        }

        @Override
        public void propagatePluginReload() {

        }

        @Override
        public List<String> propagateSupportBundleListRequest() {
            return Collections.emptyList();
        }

        @Override
        public boolean propagateDeleteSupportBundle(String bundleName) {
            return false;
        }

        @Override
        public InputStream propagateSupportBundleDownloadRequest(String bundleName, String nodeId) {
            return null;
        }

        @Override
        public UIDeployPropagationResult propagateUiUploadRequest(String nodeId, String payload) {
            return null;
        }

        @Override
        public StringContentPropagationResult propagateCreateBootstrapBundleRequestToPrimary() {
            return null;
        }

        @Override
        public void propagateMasterEncryptionKeyChanged() {

        }

        @Override
        public <T> List<T> propagateTrafficCollector(long startLong, long endLong, List<String> ipsToFilter,
                List<ArtifactoryServer> servers, Class<T> clazz) {
            return null;
        }

        @Override
        public <T> List<T> propagateTasksList(List<ArtifactoryServer> servers, Class<T> clazz) {
            return null;
        }

        @Override
        public void forceOptimizationOnce() {
        }

        @Override
        public boolean isDefault() {
            return false;
        }
    }

    private static class DummyOssAddonsManager extends OssAddonsManager {

        private DummyOssAddonsManager() {
            context = ContextHelper.get();
        }

        @Override
        public boolean isAddonSupported(AddonType addonType) {
            return false;
        }

        @Override
        public boolean isTrialLicense() {
            return false;
        }

        @Override
        public boolean isProLicensed(String licenseKeyHash) {
            return false;
        }

        @Override
        public ArtifactoryRunningMode getArtifactoryRunningMode() {
            return ArtifactoryRunningMode.OSS;
        }

        @Override
        public boolean isPartnerLicense() {
            return false;
        }

        @Override
        public void resetLicenseCache() {

        }
    }
}
