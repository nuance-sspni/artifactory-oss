package org.artifactory.common.config.log;

/**
 * @author gidis
 */
public interface LogChannel {

    void debug(String msg);

    void debug(String msg, Exception e);

    void info(String msg);

    void warn(String msg);

    void error(String msg);

    void error(String msg, Exception e);
}
