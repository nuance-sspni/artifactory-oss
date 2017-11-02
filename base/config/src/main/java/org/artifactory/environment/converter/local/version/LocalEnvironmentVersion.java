package org.artifactory.environment.converter.local.version;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.environment.converter.local.version.v1.*;
import org.artifactory.environment.converter.local.version.v2.NoNfsArtifactoryPropertiesConverter;
import org.artifactory.version.CompoundVersionDetails;

/**
 * Local environment converters ALWAYS RUN - If you happen to write one make it smart so it knows if it should run!
 *
 * @author Dan Feldman
 */
public enum LocalEnvironmentVersion {

    //v5.0.0
    v1(new DeployBootstrapBundleConverter(),
            new NoNfsNewDbPropertiesConverter(),
            new NoNfsHaCommunicationKeyConverter(),
            new NoNfsArtifactorySystemPropertiesConverter(),
            new NoNfsMasterEncryptionKeysConverter()),
    v2(new NoNfsArtifactoryPropertiesConverter());

    private final BasicEnvironmentConverter[] converters;

    /**
     * @param converters the converters to run in order to bring the environment to the expected state of this
     *                   environment version
     */
    LocalEnvironmentVersion(BasicEnvironmentConverter... converters) {
        this.converters = converters;
    }

    public static void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        for (LocalEnvironmentVersion environmentVersion : values()) {
            for (BasicEnvironmentConverter basicEnvironmentConverter : environmentVersion.converters) {
                if (basicEnvironmentConverter.isInterested(artifactoryHome, source, target)) {
                    basicEnvironmentConverter.convert(artifactoryHome, source, target);
                }
            }
        }
    }
}