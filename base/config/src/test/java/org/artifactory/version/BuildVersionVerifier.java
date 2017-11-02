package org.artifactory.version;

import org.testng.annotations.Test;

/**
 * When the (release) maven build runs with '-Dproject.version.prop' it must match the latest version
 * as its what's being built
 *
 * @author Dan Feldman
 */
@Test
public class BuildVersionVerifier {

    public void verifyBuildPropAndVersionMatch() {
        String buildVersion = System.getProperty("project.version.prop");
        if (buildVersion != null) {
            if (!ArtifactoryVersion.getCurrent().getValue().equals(buildVersion)) {
                throw new IllegalStateException("The version property for a release build must match the current latest"
                        + " Artifactory version!");
            }
        }
    }
}
