package org.artifactory.common.config.utils;

import java.io.InputStream;

/**
 * @author gidis
 */
public interface ConfigWithTimestamp {

    InputStream getBinaryStream();

    long getTimestamp();

    long getSize();

    boolean isBefore(ConfigWithTimestamp config);

    boolean isBefore(Long timestamp);

    boolean isAfter(ConfigWithTimestamp config);

    boolean isAfter(Long timestamp);
}
