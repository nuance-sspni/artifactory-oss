package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.debianview;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.debian.DebianAddon;
import org.artifactory.addon.debian.DebianMetadataInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.debian.DebianArtifactMetadataInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;

/**
 * Service for fetching the Debian\Opkg metadata for the Info tab in the UI
 *
 * @author Yuval Reches
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DebianViewService implements RestService {

    private static final Logger log = LoggerFactory.getLogger(DebianViewService.class);

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        fetchDebianMetaData(request, response);
    }

    /**
     * fetch debian metadata
     *
     * @param artifactoryRequest  - encapsulate data relate to request
     * @param artifactoryResponse - encapsulate data require for response
     */
    private void fetchDebianMetaData(ArtifactoryRestRequest artifactoryRequest, RestResponse artifactoryResponse) {
        DebianArtifactMetadataInfo debianArtifactInfo = (DebianArtifactMetadataInfo) artifactoryRequest.getImodel();
        String repoKey = debianArtifactInfo.getRepoKey();
        String path = debianArtifactInfo.getPath();
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        // read permission checks
        if (!authorizationService.canRead(repoPath)) {
            artifactoryResponse.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            log.error("Forbidden UI REST call from user " + authorizationService.currentUsername());
            return;
        }
        // Get debian addon
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        DebianAddon debianAddon = addonsManager.addonByType(DebianAddon.class);
        if (debianAddon != null) {
            // Get debian metadata
            DebianMetadataInfo debianMetadataInfo = debianAddon.getDebianMetaDataInfo(repoPath);
            // Populating our model with the data
            debianArtifactInfo.setDebianInfo(debianMetadataInfo.getDebianInfo());
            debianArtifactInfo.setDebianDependencies(debianMetadataInfo.getDebianDependencies());
            debianArtifactInfo.setIndexFailureReason(debianMetadataInfo.getFailReason());
            debianArtifactInfo.clearRepoData();
            artifactoryResponse.iModel(debianArtifactInfo);
        }
    }
}
