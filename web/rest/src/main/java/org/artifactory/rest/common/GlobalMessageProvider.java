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

package org.artifactory.rest.common;

import com.google.common.collect.Lists;
import com.sun.jersey.spi.container.ContainerResponse;
import edu.emory.mathcs.backport.java.util.concurrent.locks.ReentrantLock;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.FooterMessage;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.storage.StorageService;
import org.artifactory.util.BootstrapBundleHelper;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.addon.FooterMessage.FooterMessageVisibility.admin;
import static org.artifactory.addon.FooterMessage.FooterMessageVisibility.isVisible;

/**
 * @author Gidi Shabat
 */
public class GlobalMessageProvider {
    private static final Logger log = LoggerFactory.getLogger(GlobalMessageProvider.class);

    private long lastUpdateTime = 0;
    private List<FooterMessage> cache = Lists.newArrayList();
    private volatile ReentrantLock lock = new ReentrantLock();

    void decorateWithGlobalMessages(ContainerResponse response, AddonsManager addonsManager,
            StorageService storageService, AuthorizationService authenticationService) {
        try {
            boolean admin = authenticationService.isAdmin();
            boolean notAnonymous = !authenticationService.isAnonymous();
            // Try to update the cache if needed
            triggerCacheUpdateProcessIfNeeded(addonsManager, storageService);
            // update response header with message in cache
            List<FooterMessage> collect = cache.stream()
                    .filter(message -> isVisible(message.getVisibility(), admin, notAnonymous))
                    .collect(Collectors.toList());
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(collect);
            response.getResponse().getMetadata().add("Artifactory-UI-messages", json);
        } catch (Exception e) {
            log.error("Fail to attache global message to response header", e);
        }
    }

    private void triggerCacheUpdateProcessIfNeeded(AddonsManager addonsManager, StorageService storageService) {
        long currentTime = System.currentTimeMillis();
        // update the cache every 10 seconds
        if (currentTime - lastUpdateTime > TimeUnit.SECONDS.toMillis(10)) {
            // Only one thread is allowed to update the cache
            // all the other requests will use the old cache value
            boolean acquireLock = lock.tryLock();
            try {
                if (acquireLock) {
                    List<FooterMessage> list = Lists.newArrayList();
                    decorateHeadersWithLicenseNotInstalled(list, addonsManager);
                    decorateHeaderWithQuotaMessage(list, storageService);
                    decorateWithBootstrapBundleMessage(list);
                    // update the cache and the last update time
                    lastUpdateTime = currentTime;
                    cache = list;
                }
            } finally {
                if (acquireLock) {
                    lock.unlock();
                }
            }
        }
    }

    private void decorateHeadersWithLicenseNotInstalled(List<FooterMessage> list, AddonsManager addonsManager) {
        FooterMessage licenseMessage = addonsManager.getLicenseFooterMessage();
        if (licenseMessage != null) {
            list.add(licenseMessage);
        }
    }

    private void decorateHeaderWithQuotaMessage(List<FooterMessage> list, StorageService storageService) {
        StorageQuotaInfo storageQuotaInfo = storageService.getStorageQuotaInfo(0);
        if (storageQuotaInfo != null) {
            boolean limitReached = storageQuotaInfo.isLimitReached();
            boolean warningReached = storageQuotaInfo.isWarningLimitReached();
            if (limitReached) {
                String errorMessage = storageQuotaInfo.getErrorMessage();
                list.add(FooterMessage.createError(errorMessage, admin));
            } else if (warningReached) {
                String warningMessage = storageQuotaInfo.getWarningMessage();
                list.add(FooterMessage.createWarning(warningMessage, admin));
            }
        }
    }

    private void decorateWithBootstrapBundleMessage(List<FooterMessage> list) {
        // Only admins can see the bundle warning
        File clusterEtcDir = BootstrapBundleHelper.resolveClusterEtcDir(ArtifactoryHome.get());
        File upgradeBundleFile = new File(clusterEtcDir, ArtifactoryHome.BOOTSTRAP_BUNDLE_FILENAME);
        // not null means ha-etc exists
        if (clusterEtcDir != null && upgradeBundleFile.exists()) {
            // Bundle file exists in CLUSTER_HOME/ha-etc - show warning
            list.add(FooterMessage.createWarning(
                    "Artifactory detected a " +
                            "<a href=\"https://www.jfrog.com/confluence/display/RTF/HA+Installation+and+Setup#HAInstallationandSetup-TheBootstrapBundle\" target=\"_blank\">Bootstrap Bundle file</a>" +
                            " at $CLUSTER_HOME/ha-etc. <br>" +
                            "The bootstrap bundle is needed when upgrading or adding new nodes to your cluster. Since it" +
                            " contains sensitive data, we recommend backing it up to a safe location once the upgrade" +
                            " process is complete. <DISMISSIBLE><MSGID:BOOTSTRAP_BUNDLE_FILE_PATH_HA>", admin)
            );
        }
    }
}
