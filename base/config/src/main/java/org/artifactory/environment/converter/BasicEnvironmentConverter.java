package org.artifactory.environment.converter;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.version.CompoundVersionDetails;

/**
 * @author Gidi Shabat
 */
public interface BasicEnvironmentConverter {

    static String BACKUP_FILE_EXT = ".back";

    boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target);

    void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target);
}
