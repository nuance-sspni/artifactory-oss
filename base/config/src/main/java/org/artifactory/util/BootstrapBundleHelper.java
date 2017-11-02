package org.artifactory.util;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Utility to resolve ha-etc folder (unused since 5.0.0) which might still be used in Bootstrap Bundle upgrade scenarios.
 *
 * @author Dan Feldman
 */
public class BootstrapBundleHelper {
    private static final Logger log = LoggerFactory.getLogger(BootstrapBundleHelper.class);

    private static final String HA_ETC = "ha-etc";

    /**
     * Tries to acquire ha-etc folder from either the cluster.home in ha-node.properties if it's an older format,
     * else tries to resolve it from ha-data which is still considered present during upgrades and new installs that
     * didn't switch to cluster-filesystem.
     *
     * @return the ha-etc folder if resolved successfully and exists, null otherwise
     */
    @Nullable
    public static File resolveClusterEtcDir(ArtifactoryHome home) {
        // First try the old cluster home location
        File clusterHomeDir = null;
        try {
            clusterHomeDir = NoNfsBasicEnvironmentConverter.resolveClusterHomeDir(home);
        } catch (Exception e) {
            log.debug("Failed to resolve $CLUSTER_HOME: ", e);
        }
        if (clusterHomeDir != null && clusterHomeDir.exists()) {
            File haEtc = new File(clusterHomeDir, HA_ETC);
            if (haEtc.exists()) {
                return haEtc;
            }
        }
        // Fallback - resolve etc from data dir, since we probably ran conversion on node.properties file at this point.
        return resolveHaEtcFromHaData(home);
    }

    @Nullable
    private static File resolveHaEtcFromHaData(ArtifactoryHome home) {
        File clusterDataDir = null;
        try {
            clusterDataDir = NoNfsBasicEnvironmentConverter.resolveClusterDataDir(home);
        } catch (Exception e) {
            log.debug("Failed to resolve $CLUSTER_HOME/ha-data: ", e);
        }
        if (clusterDataDir == null || !clusterDataDir.exists()) {
            return null;
        }
        File clusterHomeDir = clusterDataDir.getParentFile();
        if (clusterHomeDir == null || !clusterHomeDir.exists()) {
            return null;
        }
        File clusterEtcDir = new File(clusterHomeDir, HA_ETC);
        return clusterEtcDir.exists() ? clusterEtcDir : null;
    }
}
