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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.vcs.VcsGitProvider;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.PropertySetNameModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.AdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.BasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.distribution.DistributionAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.distribution.DistributionBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.distribution.DistributionRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.*;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.*;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.exception.RepoConfigException;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.UiRequestUtils;
import org.artifactory.util.UrlValidator;
import org.jdom2.Verifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.apache.http.HttpStatus.*;
import static org.artifactory.repo.config.RepoConfigDefaultValues.*;

/**
 * Service validates values in the model and sets default values as needed.
 *
 * @author Dan Feldman
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RepoConfigValidator {

    private static final int REPO_KEY_MAX_LENGTH = 64;
    private static final List<Character> forbiddenChars = Lists
            .newArrayList('/', '\\', ':', '|', '?', '*', '"', '<', '>');
    private final transient UrlValidator urlValidator = new UrlValidator("http", "https");

    @Autowired
    private CentralConfigService centralConfig;

    @Autowired
    private AddonsManager addonsManager;

    public void validateLocal(LocalRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);
        LocalBasicRepositoryConfigModel basic = model.getBasic();
        LocalAdvancedRepositoryConfigModel advanced = model.getAdvanced();
        TypeSpecificConfigModel typeSpecific = model.getTypeSpecific();

        //basic
        validateSharedBasic(basic);

        //advanced
        validateSharedAdvanced(advanced);

        //type specific
        validateSharedTypeSpecific(typeSpecific);
        validateLocalTypeSpecific(typeSpecific);
    }

    public void validateRemote(RemoteRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);
        RemoteBasicRepositoryConfigModel basic = model.getBasic();
        RemoteAdvancedRepositoryConfigModel advanced = model.getAdvanced();
        TypeSpecificConfigModel typeSpecific = model.getTypeSpecific();

        //basic
        validateSharedBasic(basic);
        if (StringUtils.isBlank(basic.getUrl())) {
            throw new RepoConfigException("URL cannot be empty", SC_BAD_REQUEST);
        }
        try {
            urlValidator.validate(basic.getUrl());
        } catch (UrlValidator.UrlValidationException e) {
            throw new RepoConfigException("Invalid URL: " + e.getMessage(), SC_BAD_REQUEST, e);
        }
        if (basic.getRemoteLayoutMapping() != null
                && centralConfig.getDescriptor().getRepoLayout(basic.getLayout()) == null) {
            throw new RepoConfigException("Invalid remote repository layout", SC_BAD_REQUEST);
        }
        basic.setOffline(Optional.ofNullable(basic.isOffline()).orElse(DEFAULT_OFFLINE));

        //advanced
        validateSharedAdvanced(advanced);
        advanced.setHardFail(Optional.ofNullable(advanced.getHardFail()).orElse(DEFAULT_HARD_FAIL));
        advanced.setStoreArtifactsLocally(
                Optional.ofNullable(advanced.isStoreArtifactsLocally()).orElse(DEFAULT_STORE_ARTIFACTS_LOCALLY));
        advanced.setSynchronizeArtifactProperties(
                Optional.ofNullable(advanced.getSynchronizeArtifactProperties()).orElse(DEFAULT_SYNC_PROPERTIES));
        advanced.setShareConfiguration(
                Optional.ofNullable(advanced.isShareConfiguration()).orElse(DEFAULT_SHARE_CONFIG));
        advanced.setBlockMismatchingMimeTypes(
                Optional.ofNullable(advanced.isBlockMismatchingMimeTypes())
                        .orElse(DEFAULT_BLOCK_MISMATCHING_MIME_TYPES));

        //network
        RemoteNetworkRepositoryConfigModel network = advanced.getNetwork();
        if (network != null) {
            if (StringUtils.isNotBlank(network.getProxy()) &&
                    centralConfig.getDescriptor().getProxy(network.getProxy()) == null) {
                throw new RepoConfigException("Invalid proxy configuration", SC_BAD_REQUEST);
            }
            network.setSocketTimeout(Optional.ofNullable(network.getSocketTimeout()).orElse(DEFAULT_SOCKET_TIMEOUT));
            network.setSyncProperties(Optional.ofNullable(network.isSyncProperties()).orElse(DEFAULT_SYNC_PROPERTIES));
            network.setCookieManagement(
                    Optional.ofNullable(network.getCookieManagement()).orElse(DEFAULT_COOKIE_MANAGEMENT));
            network.setLenientHostAuth(
                    Optional.ofNullable(network.getLenientHostAuth()).orElse(DEFAULT_LENIENENT_HOST_AUTH));
        }

        //cache
        RemoteCacheRepositoryConfigModel cache = advanced.getCache();
        if (cache != null) {

            cache.setKeepUnusedArtifactsHours(
                    Optional.ofNullable(cache.getKeepUnusedArtifactsHours()).orElse(DEFAULT_KEEP_UNUSED_ARTIFACTS));
            cache.setRetrievalCachePeriodSecs(
                    Optional.ofNullable(cache.getRetrievalCachePeriodSecs()).orElse(DEFAULT_RETRIEVAL_CACHE_PERIOD));
            cache.setAssumedOfflineLimitSecs(
                    Optional.ofNullable(cache.getAssumedOfflineLimitSecs()).orElse(DEFAULT_ASSUMED_OFFLINE));
            cache.setMissedRetrievalCachePeriodSecs(
                    Optional.ofNullable(cache.getMissedRetrievalCachePeriodSecs()).orElse(
                            DEFAULT_MISSED_RETRIEVAL_PERIOD));
        }
        //type specific
        validateSharedTypeSpecific(typeSpecific);
        validateRemoteTypeSpecific(typeSpecific);
    }

    public void validateVirtual(VirtualRepositoryConfigModel model, MutableCentralConfigDescriptor configDescriptor)
            throws RepoConfigException {
        //Sections and aggregated repos validation
        verifyAllSectionsExist(model);
        validateAggregatedReposExistAndTypesMatch(model, configDescriptor);

        //basic
        VirtualBasicRepositoryConfigModel basic = model.getBasic();
        basic.setIncludesPattern(Optional.ofNullable(model.getBasic().getIncludesPattern())
                .orElse(DEFAULT_INCLUDES_PATTERN));

        //advanced
        model.getAdvanced().setRetrieveRemoteArtifacts(Optional.ofNullable(model.getAdvanced()
                .getRetrieveRemoteArtifacts()).orElse(DEFAULT_VIRTUAL_CAN_RETRIEVE_FROM_REMOTE));

        //type specific
        validateVirtualTypeSpecific(model.getTypeSpecific());
    }

    public void validateDistribution(DistributionRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);
        DistributionBasicRepositoryConfigModel basic = model.getBasic();
        DistributionAdvancedRepositoryConfigModel advanced = model.getAdvanced();
        DistRepoTypeSpecificConfigModel typeSpecific = model.getTypeSpecific();

        //basic
        validateSharedBasic(basic);
        basic.setDefaultNewRepoPrivate(Optional.ofNullable(basic.getDefaultNewRepoPrivate())
                .orElse(DEFAULT_NEW_BINTRAY_REPO_PRIVATE));
        basic.setDefaultNewRepoPremium(Optional.ofNullable(basic.getDefaultNewRepoPremium())
                .orElse(DEFAULT_NEW_BINTRAY_REPO_PREMIUM));

        //advanced
        validateSharedAdvanced(advanced);
        advanced.setDistributionRules(
                Optional.ofNullable(advanced.getDistributionRules()).orElse(Lists.newArrayList()));
        //type specific
        validateDistConfig(typeSpecific);
    }

    /**
     * Validates all given repo keys exist - throws an error for the first not found one.
     *
     * @param repoKeys - Keys to check if existing
     */
    public void validateSelectedReposInVirtualExist(List<VirtualSelectedRepository> repoKeys,
            MutableCentralConfigDescriptor configDescriptor)
            throws RepoConfigException {
        String nonExistentKey = repoKeys.stream()
                .map(VirtualSelectedRepository::getRepoName)
                .filter(repoKey -> !configDescriptor.getLocalRepositoriesMap().containsKey(repoKey) &&
                        !configDescriptor.getRemoteRepositoriesMap().containsKey(repoKey) &&
                        !configDescriptor.getVirtualRepositoriesMap().containsKey(repoKey))
                .findAny()
                .orElse(null);
        if (StringUtils.isNotBlank(nonExistentKey)) {
            throw new RepoConfigException("Repository '" + nonExistentKey + "' does not exist", SC_NOT_FOUND);
        }
    }

    private void validateAggregatedReposExistAndTypesMatch(VirtualRepositoryConfigModel model,
            MutableCentralConfigDescriptor configDescriptor)
            throws RepoConfigException {
        List<VirtualSelectedRepository> repoKeys = Optional.ofNullable(model.getBasic().getSelectedRepositories())
                .orElse(Lists.newArrayList());
        if (CollectionUtils.isNullOrEmpty(repoKeys)) {
            model.getBasic().setSelectedRepositories(repoKeys);
        } else {

            validateSelectedReposInVirtualExist(repoKeys, configDescriptor);
            RepoDescriptor invalidTypeDescriptor = repoKeys.stream()
                    .map(repoKey -> mapRepoKeyToDescriptor(repoKey,configDescriptor))
                    .filter(repoDescriptor -> !filterByType(model.getTypeSpecific().getRepoType(), repoDescriptor))
                    .findAny().orElse(null);
            if (invalidTypeDescriptor != null) {
                throw new RepoConfigException("Repository '" + model.getGeneral().getRepoKey()
                        + "' aggregates another repository '" + invalidTypeDescriptor.getKey() + "' that has a "
                        + "mismatching package type " + invalidTypeDescriptor.getType().name(), SC_FORBIDDEN);
            }

        }
    }

    private boolean filterByType(RepoType type, RepoDescriptor repo) {
        return repo != null && (type.equals(RepoType.Generic) || type.equals(RepoType.P2) ||
                (type.isMavenGroup() ? repo.getType().isMavenGroup() : repo.getType().equals(type)));
    }

    public RepoDescriptor mapRepoKeyToDescriptor(VirtualSelectedRepository repository,
            MutableCentralConfigDescriptor configDescriptor) {
        String repoKey = repository.getRepoName();
        RepoDescriptor descriptor = configDescriptor.getLocalRepositoriesMap().get(repoKey);
        if (descriptor == null) {
            descriptor = configDescriptor.getRemoteRepositoriesMap().get(repoKey);
        }
        if (descriptor == null) {
            descriptor = configDescriptor.getVirtualRepositoriesMap().get(repoKey);
        }
        return descriptor;
    }


    private void validateSharedBasic(BasicRepositoryConfigModel basic) throws RepoConfigException {
        basic.setIncludesPattern(Optional.ofNullable(basic.getIncludesPattern()).orElse(DEFAULT_INCLUDES_PATTERN));
        if (basic.getLayout() == null || centralConfig.getDescriptor().getRepoLayout(basic.getLayout()) == null) {
            throw new RepoConfigException("Invalid repository layout", SC_BAD_REQUEST);
        }
    }

    private void validateSharedAdvanced(AdvancedRepositoryConfigModel model) throws RepoConfigException {
        if (model.getPropertySets() == null) {
            return;
        }
        String invalidPropSet = model.getPropertySets().stream()
                .map(PropertySetNameModel::getName)
                .filter(propSetName -> !centralConfig.getMutableDescriptor().isPropertySetExists(propSetName))
                .findAny().orElse(null);
        if (StringUtils.isNotBlank(invalidPropSet)) {
            throw new RepoConfigException("Property set " + invalidPropSet + " doesn't exist", SC_NOT_FOUND);
        }
        model.setAllowContentBrowsing(
                Optional.ofNullable(model.getAllowContentBrowsing()).orElse(DEFAULT_ALLOW_CONTENT_BROWSING));
        model.setBlackedOut(Optional.ofNullable(model.isBlackedOut()).orElse(DEFAULT_BLACKED_OUT));
    }

    private void validateSharedTypeSpecific(TypeSpecificConfigModel model) {
        switch (model.getRepoType()) {
            case Gradle:
            case Ivy:
            case SBT:
                //Maven types suppress pom checks by default, maven does not
                MavenTypeSpecificConfigModel mavenTypes = (MavenTypeSpecificConfigModel) model;
                mavenTypes.setSuppressPomConsistencyChecks(
                        Optional.ofNullable(mavenTypes.getSuppressPomConsistencyChecks())
                                .orElse(DEFAULT_SUPPRESS_POM_CHECKS));
            case Maven:
                MavenTypeSpecificConfigModel maven = (MavenTypeSpecificConfigModel) model;
                maven.setMaxUniqueSnapshots(
                        Optional.ofNullable(maven.getMaxUniqueSnapshots()).orElse(DEFAULT_MAX_UNIQUE_SNAPSHOTS));
                maven.setHandleReleases(Optional.ofNullable(maven.getHandleReleases()).orElse(DEFAULT_HANDLE_RELEASES));
                maven.setHandleSnapshots(
                        Optional.ofNullable(maven.getHandleSnapshots()).orElse(DEFAULT_HANDLE_SNAPSHOTS));
                maven.setSuppressPomConsistencyChecks(Optional.ofNullable(maven.getSuppressPomConsistencyChecks())
                        .orElse(DEFAULT_SUPPRESS_POM_CHECKS));
                break;
            case NuGet:
                NugetTypeSpecificConfigModel nuget = ((NugetTypeSpecificConfigModel) model);
                nuget.setForceNugetAuthentication(
                        Optional.ofNullable(nuget.isForceNugetAuthentication()).orElse(DEFAULT_FORCE_NUGET_AUTH));
                break;
        }
    }

    private void validateLocalTypeSpecific(TypeSpecificConfigModel model) throws RepoConfigException {
        validateSharedTypeSpecific(model);
        switch (model.getRepoType()) {
            case YUM:
                YumTypeSpecificConfigModel yum = (YumTypeSpecificConfigModel) model;
                yum.setGroupFileNames(Optional.ofNullable(yum.getGroupFileNames()).orElse(DEFAULT_YUM_GROUPFILE_NAME));
                yum.setMetadataFolderDepth(
                        Optional.ofNullable(yum.getMetadataFolderDepth()).orElse(DEFAULT_YUM_METADATA_DEPTH));
                yum.setAutoCalculateYumMetadata(
                        Optional.ofNullable(yum.isAutoCalculateYumMetadata()).orElse(DEFAULT_YUM_AUTO_CALCULATE));
                yum.setEnableFileListsIndexing(
                        Optional.ofNullable(yum.isEnableFileListsIndexing()).orElse(DEFAULT_ENABLE_FILELIST_INDEXING));
                break;
            case Debian:
                DebTypeSpecificConfigModel deb = (DebTypeSpecificConfigModel) model;
                deb.setListRemoteFolderItems(
                        Optional.ofNullable(deb.isListRemoteFolderItems()).orElse(
                                DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                deb.setTrivialLayout(Optional.ofNullable(deb.getTrivialLayout()).orElse(DEFAULT_DEB_TRIVIAL_LAYOUT));
                break;
            case Opkg:
                OpkgTypeSpecificConfigModel opkg = (OpkgTypeSpecificConfigModel) model;
                opkg.setListRemoteFolderItems(Optional.ofNullable(opkg.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Docker:
                DockerTypeSpecificConfigModel docker = (DockerTypeSpecificConfigModel) model;
                docker.setDockerApiVersion(
                        Optional.ofNullable(docker.getDockerApiVersion()).orElse(DEFAULT_DOCKER_API_VER));
                docker.setMaxUniqueTags(
                        Optional.ofNullable(docker.getMaxUniqueTags()).orElse(DEFAULT_MAX_UNIQUE_TAGS));
                break;
            case NuGet:
                NugetTypeSpecificConfigModel nuget = (NugetTypeSpecificConfigModel) model;
                nuget.setMaxUniqueSnapshots(
                        Optional.ofNullable(nuget.getMaxUniqueSnapshots()).orElse(DEFAULT_MAX_UNIQUE_SNAPSHOTS));
                break;
            case VCS:
                //Don't fail on bower or pods local
                if (model.getRepoType().equals(RepoType.Bower) || model.getRepoType().equals(RepoType.CocoaPods)) {
                    break;
                }
            case Distribution:
            case P2:
                throw new RepoConfigException("Package type " + model.getRepoType().name()
                        + " is unsupported in local repositories", SC_BAD_REQUEST);
        }
    }

    private void validateRemoteTypeSpecific(TypeSpecificConfigModel model) throws RepoConfigException {
        validateSharedTypeSpecific(model);
        switch (model.getRepoType()) {
            case P2:
                P2TypeSpecificConfigModel p2 = (P2TypeSpecificConfigModel) model;
                p2.setSuppressPomConsistencyChecks(Optional.ofNullable(p2.getSuppressPomConsistencyChecks())
                        .orElse(DEFAULT_SUPPRESS_POM_CHECKS));
            case Maven:
            case Gradle:
            case Ivy:
            case SBT:
                MavenTypeSpecificConfigModel maven = (MavenTypeSpecificConfigModel) model;
                maven.setEagerlyFetchJars(
                        Optional.ofNullable(maven.getEagerlyFetchJars()).orElse(DEFAULT_EAGERLY_FETCH_JARS));
                maven.setEagerlyFetchSources(
                        Optional.ofNullable(maven.getEagerlyFetchSources()).orElse(DEFAULT_EAGERLY_FETCH_SOURCES));
                maven.setListRemoteFolderItems(Optional.ofNullable(maven.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Docker:
                DockerTypeSpecificConfigModel docker = (DockerTypeSpecificConfigModel) model;
                docker.setEnableTokenAuthentication(
                        Optional.ofNullable(docker.isEnableTokenAuthentication()).orElse(DEFAULT_TOKEN_AUTH));
                docker.setListRemoteFolderItems(Optional.ofNullable(docker.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE));
                break;
            case Bower:
                BowerTypeSpecificConfigModel bower = (BowerTypeSpecificConfigModel) model;
                bower.setRegistryUrl(Optional.ofNullable(bower.getRegistryUrl()).orElse(DEFAULT_BOWER_REGISTRY));
                validateVcsConfig(bower);
                break;
            case CocoaPods:
                CocoaPodsTypeSpecificConfigModel pods = (CocoaPodsTypeSpecificConfigModel) model;
                pods.setSpecsRepoUrl(Optional.ofNullable(pods.getSpecsRepoUrl()).orElse(DEFAULT_PODS_SPECS_REPO));
                pods.setSpecsRepoProvider(
                        Optional.ofNullable(pods.getSpecsRepoProvider()).orElse(DEFAULT_VCS_GIT_CONFIG));
                validateVcsConfig(pods);
            case VCS:
                VcsTypeSpecificConfigModel vcs = (VcsTypeSpecificConfigModel) model;
                validateVcsConfig(vcs);
                break;
            case NuGet:
                NugetTypeSpecificConfigModel nuGet = (NugetTypeSpecificConfigModel) model;
                nuGet.setDownloadContextPath(
                        Optional.ofNullable(nuGet.getDownloadContextPath()).orElse(DEFAULT_NUGET_DOWNLOAD_PATH));
                nuGet.setFeedContextPath(
                        Optional.ofNullable(nuGet.getFeedContextPath()).orElse(DEFAULT_NUGET_FEED_PATH));
                nuGet.setListRemoteFolderItems(Optional.ofNullable(nuGet.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE));
                break;
            case Debian:
                DebTypeSpecificConfigModel deb = (DebTypeSpecificConfigModel) model;
                deb.setListRemoteFolderItems(Optional.ofNullable(deb.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Opkg:
                OpkgTypeSpecificConfigModel opkg = (OpkgTypeSpecificConfigModel) model;
                opkg.setListRemoteFolderItems(Optional.ofNullable(opkg.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case YUM:
                YumTypeSpecificConfigModel yum = (YumTypeSpecificConfigModel) model;
                yum.setListRemoteFolderItems(Optional.ofNullable(yum.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Npm:
                NpmTypeSpecificConfigModel npm = (NpmTypeSpecificConfigModel) model;
                npm.setListRemoteFolderItems(Optional.ofNullable(npm.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Generic:
                GenericTypeSpecificConfigModel generic = (GenericTypeSpecificConfigModel) model;
                generic.setListRemoteFolderItems(Optional.ofNullable(generic.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Gems:
                GemsTypeSpecificConfigModel gems = (GemsTypeSpecificConfigModel) model;
                gems.setListRemoteFolderItems(Optional.ofNullable(gems.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Pypi:
                PypiTypeSpecificConfigModel pypi = (PypiTypeSpecificConfigModel) model;
                pypi.setListRemoteFolderItems(Optional.ofNullable(pypi.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case GitLfs:
                GitLfsTypeSpecificConfigModel gitlfs = (GitLfsTypeSpecificConfigModel) model;
                gitlfs.setListRemoteFolderItems(Optional.ofNullable(gitlfs.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case Composer:
                ComposerTypeSpecificConfigModel composer = (ComposerTypeSpecificConfigModel) model;
                composer.setRegistryUrl(
                        Optional.ofNullable(composer.getRegistryUrl()).orElse(DEFAULT_COMPOSER_REGISTRY));
                validateVcsConfig(composer);
                break;
            case Vagrant:
            case Distribution:
                throw new RepoConfigException("Package type " + model.getRepoType().name()
                        + " is unsupported in remote repositories", SC_BAD_REQUEST);
        }
    }

    private void validateVirtualTypeSpecific(TypeSpecificConfigModel model) throws RepoConfigException {
        switch (model.getRepoType()) {
            case P2:
                P2TypeSpecificConfigModel p2 = (P2TypeSpecificConfigModel) model;
                p2.setP2Repos(Optional.ofNullable(p2.getP2Repos()).orElse(Lists.newArrayList()));
            case Maven:
            case Gradle:
            case Ivy:
            case SBT:
                MavenTypeSpecificConfigModel maven = (MavenTypeSpecificConfigModel) model;
                maven.setPomCleanupPolicy(
                        Optional.ofNullable(maven.getPomCleanupPolicy()).orElse(DEFAULT_POM_CLEANUP_POLICY));
                if (maven.getKeyPair() != null && !addonsManager.addonByType(ArtifactWebstartAddon.class)
                        .getKeyPairNames().contains(maven.getKeyPair())) {
                    throw new RepoConfigException("Keypair '" + maven.getKeyPair() + "' doesn't exist", SC_NOT_FOUND);
                }
                break;
            case VCS:
                //Don't fail on bower and pods virtual
                if (model.getRepoType().equals(RepoType.Bower) || model.getRepoType().equals(RepoType.CocoaPods)) {
                    break;
                }
            case GitLfs:
                GitLfsTypeSpecificConfigModel gitlfs = (GitLfsTypeSpecificConfigModel) model;
                gitlfs.setListRemoteFolderItems(Optional.ofNullable(gitlfs.isListRemoteFolderItems())
                        .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
                break;
            case YUM:
                YumTypeSpecificConfigModel yum = (YumTypeSpecificConfigModel) model;
                yum.setVirtualRetrievalCachePeriodSecs(Optional.ofNullable(yum.getVirtualRetrievalCachePeriodSecs())
                        .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
                break;
            case Chef:
                ChefTypeSpecificConfigModel chef = (ChefTypeSpecificConfigModel) model;
                chef.setVirtualRetrievalCachePeriodSecs(Optional.ofNullable(chef.getVirtualRetrievalCachePeriodSecs())
                        .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
                break;
            case CocoaPods:
            case Debian:
            case Opkg:
            case Vagrant:
            case Distribution:
                throw new RepoConfigException("Package type " + model.getRepoType().name()
                        + " is unsupported in virtual repositories", SC_BAD_REQUEST);
        }
    }

    private void verifyAllSectionsExist(RepositoryConfigModel model) throws RepoConfigException {
        if (model.getGeneral() == null) {
            throw new RepoConfigException("Repository Key cannot be empty", SC_BAD_REQUEST);
        } else if (model.getBasic() == null) {
            throw new RepoConfigException("Basic configuration cannot be empty", SC_BAD_REQUEST);
        }
        if (model.getAdvanced() == null) {
            throw new RepoConfigException("Advanced configuration cannot be empty", SC_BAD_REQUEST);
        }
        if (model.getTypeSpecific() == null) {
            throw new RepoConfigException("Package type configuration cannot be empty", SC_BAD_REQUEST);
        }
    }

    public void validateRepoName(String repoKey) throws RepoConfigException {
        if (StringUtils.isBlank(repoKey)) {
            throw new RepoConfigException("Repository key cannot be empty", SC_BAD_REQUEST);
        }
        if (StringUtils.length(repoKey) > REPO_KEY_MAX_LENGTH) {
            throw new RepoConfigException("Repository key exceed maximum length", SC_BAD_REQUEST);
        }
        if (UiRequestUtils.isReservedName(repoKey)) {
            throw new RepoConfigException("Repository key '" + repoKey + "' is a reserved name", SC_BAD_REQUEST);
        }
        if (repoKey.equals(".") || repoKey.equals("..") || repoKey.equals("&")) {
            throw new RepoConfigException("Invalid Repository key", SC_BAD_REQUEST);
        }
        int illegalChar = repoKey.chars()
                .filter(forbiddenChars::contains)
                .findAny()
                .orElse(-1);

        if (illegalChar > -1) {
            throw new RepoConfigException("Repository key contains illegal character '" + (char) illegalChar + "'",
                    SC_BAD_REQUEST);
        }
        String error = Verifier.checkXMLName(repoKey);
        if (StringUtils.isNotBlank(error)) {
            throw new RepoConfigException("Repository key contains illegal character", SC_BAD_REQUEST);
        }

        if (!centralConfig.getMutableDescriptor().isKeyAvailable(repoKey)) {
            throw new RepoConfigException("Repository key already exist", SC_BAD_REQUEST);
        }
    }

    private void validateVcsConfig(VcsTypeSpecificConfigModel vcs) throws RepoConfigException {
        vcs.setVcsType(Optional.ofNullable(vcs.getVcsType()).orElse(DEFAULT_VCS_TYPE));
        vcs.setGitProvider(Optional.ofNullable(vcs.getGitProvider()).orElse(DEFAULT_GIT_PROVIDER));
        if (vcs.getGitProvider().equals(VcsGitProvider.CUSTOM)) {
            if (StringUtils.isBlank(vcs.getDownloadUrl())) {
                throw new RepoConfigException("Git Download URL is required for custom Git providers", SC_BAD_REQUEST);
            }
        } else if (StringUtils.isNotBlank(vcs.getDownloadUrl())) {
            //custom url is sent in model but not for custom provider
            vcs.setDownloadUrl(null);
        }
        vcs.setMaxUniqueSnapshots(
                Optional.ofNullable(vcs.getMaxUniqueSnapshots()).orElse(DEFAULT_MAX_UNIQUE_SNAPSHOTS));
        vcs.setListRemoteFolderItems(
                Optional.ofNullable(vcs.isListRemoteFolderItems()).orElse(DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE));
    }

    private void validateDistConfig(DistRepoTypeSpecificConfigModel model) throws RepoConfigException {
        //New repo without authentication string
        if (StringUtils.isBlank(model.getBintrayAppConfig()) && StringUtils.isBlank(model.getBintrayAuthString())) {
            throw new RepoConfigException("Bintray OAuth authentication string cannot be empty",
                    HttpStatus.SC_BAD_REQUEST);
        }
    }
}
