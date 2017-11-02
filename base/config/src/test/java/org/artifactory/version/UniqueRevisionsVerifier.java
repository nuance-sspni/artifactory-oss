package org.artifactory.version;

import org.testng.annotations.Test;

import java.util.stream.Stream;

/**
 * This test is supposed to fail the build if more than one version holds the same revision - revisions must be unique
 * for all versions.
 *
 * @author Dan Feldman
 */
@Test
public class UniqueRevisionsVerifier {

    public void verifyRevisions() {
        if (getUniqueRevisionCount() < ArtifactoryVersion.values().length) {
            throw new IllegalStateException("Each Artifactory version must hold a unique build revision!");
        }
    }

    private long getUniqueRevisionCount() {
        return Stream.of(ArtifactoryVersion.values())
                .map(ArtifactoryVersion::getRevision)
                .distinct()
                .count();
    }
}
