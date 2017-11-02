/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.webapp.main;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.access.version.AccessVersion;

import java.io.File;
import java.io.IOException;

import static org.artifactory.webapp.main.StartArtifactoryDev.startAccessProcess;

/**
 * @author yoavl
 */
public class StartArtifactoryTomcatDev {

    public static final String DEFAULT_PREFIX = "..";

    /**
     * Main function, starts the Tomcat server.
     */
    public static void main(String... args) throws IOException {
        File devArtHome = getArtifactoryDevHome(args);

        File devEtcDir = WebappUtils.getTestEtcFolder();
        WebappUtils.updateMimetypes(devEtcDir);
        WebappUtils.copyNewerDevResources(devEtcDir, devArtHome, true);

        setSystemProperties(devArtHome);

        startAccessProcess();

        //Manually set the selector (needed explicitly here before any logger kicks in)
        // create the logger only after artifactory.home is set
        Tomcat tomcat = null;
        try {
            tomcat = new Tomcat();
            tomcat.setBaseDir(devArtHome + "/work");
            tomcat.addWebapp("/artifactory", WebappUtils.getWebappRoot(devArtHome, false).getAbsolutePath());
            tomcat.setPort(Integer.parseInt(System.getProperty("server.port", "8080")));
            RemoteIpValve valve = new RemoteIpValve();
            valve.setProtocolHeader("X-Forwarded-Proto");
            tomcat.getEngine().getPipeline().addValve(valve);
            tomcat.start();
            tomcat.getServer().await();
        } catch (Exception e) {
            System.err.println("Could not start the Tomcat server: " + e);
            if (tomcat != null) {
                try {
                    tomcat.stop();
                } catch (Exception e1) {
                    System.err.println("Unable to stop the Tomcat server: " + e1);
                }
            }
        }
    }

    private static void setSystemProperties(File devArtHome) {
        System.setProperty(ConstantValues.dev.getPropertyName(), "true");
        System.setProperty("java.net.preferIPv4Stack", "true");

        // set home dir - dev mode only!
        // System.setProperty(ConstantValues.dev.getPropertyName(), "true");
        //In dev mod check for plugin updates frequently
        System.setProperty(ConstantValues.pluginScriptsRefreshIntervalSecs.getPropertyName(), "20");

        // set the logback.xml
        System.setProperty("logback.configurationFile", new File(devArtHome + "/etc/logback.xml").getAbsolutePath());

        //Override access version so we don't need the maven plugin to write it's version file
        System.setProperty(AccessVersion.VERSION_OVERRIDE_SYSTEM_PROP, "artifactory-devenv");
    }

    private static File getArtifactoryDevHome(String[] args) throws IOException {
        String homeProperty = System.getProperty("artifactory.home");
        String prefix = args.length == 0 ? DEFAULT_PREFIX : args[0];
        File devArtHome = new File(
                homeProperty != null ? homeProperty : prefix + "/devenv/.artifactory").getCanonicalFile();
        if (!devArtHome.exists() && !devArtHome.mkdirs()) {
            throw new RuntimeException("Failed to create home dir: " + devArtHome.getAbsolutePath());
        }
        System.setProperty(ArtifactoryHome.SYS_PROP, devArtHome.getAbsolutePath());
        return devArtHome;
    }
}
