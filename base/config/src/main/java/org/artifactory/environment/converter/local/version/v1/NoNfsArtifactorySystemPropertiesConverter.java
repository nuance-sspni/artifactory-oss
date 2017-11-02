package org.artifactory.environment.converter.local.version.v1;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.CompoundVersionDetails;

import java.io.File;

import static org.artifactory.environment.converter.shared.version.v1.NoNfsBasicEnvironmentConverter.*;

/**
 * @author Gidi Shabat
 * @author Dan Feldman
 */
public class NoNfsArtifactorySystemPropertiesConverter implements BasicEnvironmentConverter {

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        File clusterHomeDir = resolveClusterHomeDir(artifactoryHome);
        if (clusterHomeDir != null) {
            safeCopyRelativeFile(clusterHomeDir, artifactoryHome.getArtifactorySystemPropertiesFile());
        }
    }

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return isUpgradeTo5x(source, target);
    }
}
