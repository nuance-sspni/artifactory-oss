package org.artifactory.environment.converter.local.version.v1;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter;
import org.artifactory.util.BootstrapBundleHelper;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.logging.BootstrapLogger;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Waits for the bootstrap bundle to be deployed.
 * The deployment itself is done by the bundled Access server.
 *
 * @author Dan Feldman
 * @author Yinon Avraham
 */
public class DeployBootstrapBundleConverter extends NoNfsBasicEnvironmentConverter {

    /**
     * In any case the existence of the bundle file in either the local etc of CLUSTER_HOME/ha-etc is mandatory for the
     * converter to trigger.
     */
    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        File bootstrapBundleFile = getBootstrapBundleFile(home);
        //No bundle file - nothing to do. (get method also checks for existence)
        if (bootstrapBundleFile == null) {
            return false;
        }
        String msg = "Found bootstrap bundle file in location: " + bootstrapBundleFile.getAbsolutePath() + " - using it to ";
        if (isUpgradeTo5x(source, target)) {
            BootstrapLogger.info(msg + "upgrade Artifactory.");
        }
        BootstrapLogger.info(msg + "setup Artifactory.");
        return true;
    }

    @Override
    protected void doConvert(ArtifactoryHome home, File clusterHomeDir) {
        File bootstrapBundleFile = getBootstrapBundleFile(home);
        long start = System.currentTimeMillis();
        long timeout = 60000L; //TODO [YA] Extract to constant values
        long interval = 1000L; //TODO [YA] Extract to constant values
        boolean bundleDeployed = isBootstrapBundleDeployed(bootstrapBundleFile, home);
        if (!bundleDeployed) {
            BootstrapLogger.info("Waiting for the bootstrap bundle to be deployed...");
        }
        while (!bundleDeployed && System.currentTimeMillis() - start < timeout) {
            sleep(interval);
            bundleDeployed = isBootstrapBundleDeployed(bootstrapBundleFile, home);
        }
        if (!bundleDeployed) {
            throw new IllegalStateException("Timeout reached, bootstrap bundled was not yet deployed.");
        }
    }

    private boolean isBootstrapBundleDeployed(File bootstrapBundleFile, ArtifactoryHome home) {
        //If the bootstrap bundle file is a local file (not in the NFS) - wait until it is deleted.
        File localBundleFile = home.getBootstrapBundleFile();
        if (bootstrapBundleFile.equals(localBundleFile)) {
            return !localBundleFile.exists();
        }
        //Otherwise - wait for specific files to exist
        return home.getCommunicationKeyFile().exists() && home.getDBPropertiesFile().exists();
    }

    private void sleep(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    //Gets bundle file either from home or CLUSTER_HOME/ha-etc if it exists.
    private File getBootstrapBundleFile(ArtifactoryHome home) {
        File bootstrapBundleFile = home.getBootstrapBundleFile();
        if (bootstrapBundleFile.exists()) {
            return bootstrapBundleFile;
        } else {
            // Need this special treatment to support new-format ha-node.properties (that doesn't have cluster.home)
            // for installations of new nodes that still use the bundle the upgrade put in ha-etc
            File clusterEtcDir = BootstrapBundleHelper.resolveClusterEtcDir(home);
            // if not null it also exists
            if (clusterEtcDir != null) {
                bootstrapBundleFile = new File(clusterEtcDir, ArtifactoryHome.BOOTSTRAP_BUNDLE_FILENAME);
                return bootstrapBundleFile.exists() ? bootstrapBundleFile : null;
            }
        }
        return null;
    }
}
