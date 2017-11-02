package org.artifactory.converter;

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.ConfigurationManager;

/**
 * Decide whether to block the converter or not
 *
 * @author Shay Bagants
 */
public interface ConverterBlocker {

    boolean shouldBlockConvert(ArtifactoryHome artifactoryHome, ConfigurationManager configurationManager);
}
