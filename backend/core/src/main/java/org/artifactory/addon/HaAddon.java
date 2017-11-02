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

package org.artifactory.addon;

import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.schedule.Task;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.fs.lock.FsItemsVault;

/**
 * @author mamo
 */
public interface HaAddon extends HaCommonAddon {

    String DEBIAN_RECALCULATE_ALL_NOW = "debianRecalculateAllNow";
    String DEBIAN_RECALCULATE_ALL_ASYNC = "debianRecalculateAllAsync";
    String DEBIAN_CACHE_UPDATE = "debianCacheUpdate";
    String OPKG_RECALCULATE_ALL_NOW = "opkgRecalculateAllNow";
    String OPKG_RECALCULATE_ALL_ASYNC = "opkgRecalculateAllAsync";
    String TRAFFIC_COLLECTOR = "trafficCollector";
    String PROPAGATE_TASK_EVENT = "propagateTask";
    String UI_DEPLOY = "uiDeploy";
    String SUPPORT_BUNDLE_DOWNLOAD = "supportBundleDownload";
    String SUPPORT_BUNDLE_DELETE = "supportBundleDelete";
    String LICENSE_CHANGED = "licenseChanged";

    void updateArtifactoryServerRole();

    void propagateLicenseChanges();

    boolean deleteArtifactoryServer(String serverId);

    void propagateTaskToPrimary(Task task);

    void initConfigBroadcast(ArtifactoryApplicationContext context);

    FsItemsVault getFsItemVault();

    /**
     * @return True if the current authenticated user is another HA node in the cluster
     */
    boolean isHaAuthentication();

    void propagateConfigReload();

    void init();
}
