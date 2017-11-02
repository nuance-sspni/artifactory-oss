package org.artifactory.config.bootstrap;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.file.Files;

import static org.artifactory.util.BootstrapBundleHelper.resolveClusterEtcDir;

/**
 * @author Yinon Avraham.
 */
@Service
@Reloadable(beanClass = BootstrapBundleService.class, initAfter = AccessService.class)
public class BootstrapBundleServiceImpl implements BootstrapBundleService {
    private static final Logger log = LoggerFactory.getLogger(BootstrapBundleServiceImpl.class);

    @Autowired
    private AccessService accessService;

    @Override
    public void init() {
        if (System.getProperty("artifactory.generate.bootstrap.bundle") != null) {
            log.info("Creating bootstrap bundle file (requested by system property)...");
            createBootstrapBundle();
        }
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor) {
        //do nothing
    }

    @Nonnull
    @Override
    public File createBootstrapBundle() {
        log.info("Creating bootstrap bundle.");
        File file = accessService.createBootstrapBundle();
        log.info("Bootstrap bundle was saved to file: {}", file);
        return file;
    }

    @Override
    public void destroy() {
        //do nothing
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        if (shouldConvert(source, target)) {
            log.info("Upgrade detected, creating Bootstrap Bundle.");
            File bootstrapBundle = createBootstrapBundle();
            //Existence already verified in should() method
            //Bundle file is created in local etc dir, copy it to cluster etc dir
            File bundleFileFinalLocation = new File(resolveClusterEtcDir(ArtifactoryHome.get()), bootstrapBundle.getName());
            log.info("Attempting to move Bootstrap Bundle file from {} to {}", bootstrapBundle.getAbsolutePath(),
                    bundleFileFinalLocation.getAbsolutePath());
            try {
                Files.move(bootstrapBundle.toPath(), bundleFileFinalLocation.toPath());
                log.info("Bootstrap Bundle file moved to {}", bundleFileFinalLocation.getAbsolutePath());
                log.warn("**** The bootstrap bundle is needed when upgrading or adding new nodes to your cluster. Since"
                        + " it contains sensitive data, we recommend backing it up to a safe location once the upgrade"
                        + " process is complete *****");
            } catch (Exception e) {
                log.error("Cannot move Bootstrap Bundle file into location at {} : {}.  Attempting to delete it.",
                        bundleFileFinalLocation.getAbsolutePath(), e.getMessage());
                log.debug("", e);
                FileUtils.deleteQuietly(bootstrapBundle);
                // Just in case
                FileUtils.deleteQuietly(bundleFileFinalLocation);
            }
        }
    }

    //Primary node upgrading 4.x -> 5.x generates the bundle automatically into CLUSTER_HOME/ha-etc if location exists
    private boolean shouldConvert(CompoundVersionDetails source, CompoundVersionDetails target) {
        // Not an upgrade, nothing to do.
        if (!NoNfsBasicEnvironmentConverter.isUpgradeTo5x(source, target)) {
            return false;
        }
        ArtifactoryHome home = ArtifactoryHome.get();
        if (!home.isHaConfigured() || home.getHaNodeProperties() == null || !home.getHaNodeProperties().isPrimary()) {
            log.debug("Not HA or no node.properties found, skipping automatic bootstrap bundle creation.");
            return false;
        }
        String msg = "Detected an upgrade but could not resolve the 'ha-etc' dir, the Bootstrap Bundle file will not "
                + "be created automatically.";
        try {
            File clusterEtcDir = resolveClusterEtcDir(home);
            if (clusterEtcDir == null) {
                log.warn(msg);
                return false;
            }
        } catch (Exception e) {
            log.warn(msg + ": ", e.getMessage());
            log.debug("", e);
            return false;
        }
        return true;
    }
}
