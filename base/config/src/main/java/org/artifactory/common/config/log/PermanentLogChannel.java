package org.artifactory.common.config.log;

import org.slf4j.Logger;

/**
 * @author gidis
 */
public class PermanentLogChannel implements LogChannel {
    private Logger logger;

    public PermanentLogChannel(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(String msg, Exception e) {
        logger.debug(msg, e);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    @Override
    public void error(String msg, Exception e) {
        logger.error(msg, e);
    }
}
