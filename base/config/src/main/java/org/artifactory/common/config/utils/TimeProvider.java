package org.artifactory.common.config.utils;

/**
 * @author gidis
 */
public interface TimeProvider {
    long getNormalizedTime(long timestamp);
}
