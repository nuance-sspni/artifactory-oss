package org.artifactory.environment.converter.shared.version;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.environment.converter.shared.version.v1.*;
import org.artifactory.mime.version.converter.MimeTypeConverter;
import org.artifactory.version.CompoundVersionDetails;

/**
 * @author Gidi Shabat
 */
public enum SharedEnvironmentVersion {

    //v5.0.0
    v1(new NoNfsDbConfigsTableConverter(),
            new NoNfsPluginsConverter(),
            new NoNfsUIConverter(),
            new NoNfsEnvironmentMimeTypeConverter(),
            new NoNfsMisEtcConverter(),
            new NoNfsBinaryStoreConverter(),
            new MimeTypeConverter(),
            new NoNfsSshConverter());

    private final BasicEnvironmentConverter[] converters;

    SharedEnvironmentVersion(BasicEnvironmentConverter... converter) {
        this.converters = converter;
    }

    public static SharedEnvironmentVersion getCurrent() {
        return values()[values().length - 1];
    }

    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        for (SharedEnvironmentVersion environmentVersion : values()) {
            for (BasicEnvironmentConverter basicEnvironmentConverter : environmentVersion.converters) {
                if (basicEnvironmentConverter.isInterested(artifactoryHome, source, target)) {
                    basicEnvironmentConverter.convert(artifactoryHome, source, target);
                }
            }
        }
    }
}
