package org.artifactory.common.config.log;


import org.jfrog.common.logging.BootstrapLogger;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author gidis
 */
public class TemporaryLogChannel implements LogChannel {

    @Override
    public void debug(String msg) {
        //Did you expect this to magically be something else?
        BootstrapLogger.info(msg);
    }

    @Override
    public void debug(String msg, Exception e) {
        try (StringWriter stack = new StringWriter()) {
            e.printStackTrace(new PrintWriter(stack));
            debug(msg + ": " + stack);
        } catch (Exception ex) {
            //meh.
        }
    }

    @Override
    public void info(String msg) {
        BootstrapLogger.info(msg);
    }

    @Override
    public void warn(String msg) {
        BootstrapLogger.warn(msg);
    }

    @Override
    public void error(String msg) {
        BootstrapLogger.error(msg);
    }

    @Override
    public void error(String msg, Exception e) {
        try (StringWriter stack = new StringWriter()) {
            e.printStackTrace(new PrintWriter(stack));
            error(msg + ": " + stack);
        } catch (Exception ex) {
            //meh.
        }
    }
}
