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

import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.util.ListeningPortDetector;
import org.artifactory.webapp.WebappUtils;
import org.artifactory.webapp.main.AccessProcess.AccessProcessConfig;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.jfrog.access.version.AccessVersion;

import java.io.File;
import java.io.IOException;

/**
 * @author yoavl
 */
public class StartArtifactoryDev {

    static final String ACCESS_HOME_SYS_PROP = "jfrog.access.home";

    /**
     * Main function, starts the jetty server.
     */
    public static void main(String... args) throws IOException {
        System.setProperty(ConstantValues.dev.getPropertyName(), "true");
        File devArtHome = getArtifactoryDevHome();

        File devEtcDir = WebappUtils.populateAndGetEtcFolder(devArtHome);

        setSystemProperties(devArtHome);

        startAccessProcess();

        //Manually set the selector (needed explicitly here before any logger kicks in)
        // create the logger only after artifactory.home is set
        Server server = null;
        try {
            File jettyXml = new File(devEtcDir, "jetty.xml");
            if (!jettyXml.exists()) {
                throw new IllegalStateException("The Artifactory etc folder '" + devEtcDir.getAbsolutePath() +
                        "' should contain a jetty.xml and webdefault.xml files from " + devEtcDir.getAbsolutePath());
            }
            XmlConfiguration xmlConfiguration = new XmlConfiguration(jettyXml.toURL());

            server = new Server();
            xmlConfiguration.configure(server);

            server.start();
        } catch (Exception e) {
            System.err.println("Could not start the Jetty server: " + e);
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception e1) {
                    System.err.println("Unable to stop the jetty server: " + e1);
                }
            }
        }
    }

    private static void setSystemProperties(File devArtHome) {
        System.setProperty("java.net.preferIPv4Stack", "true");

        // set the logback.xml
        System.setProperty("logback.configurationFile", new File(devArtHome + "/etc/logback.xml").getAbsolutePath());

        // set default artifactory port
        if (System.getProperty(ListeningPortDetector.SYS_ARTIFACTORY_PORT) == null) {
            System.setProperty(ListeningPortDetector.SYS_ARTIFACTORY_PORT, "8080");
        }

        //Override access version so we don't need the maven plugin to write it's version file
        System.setProperty(AccessVersion.VERSION_OVERRIDE_SYSTEM_PROP, "artifactory-devenv");
    }

    private static File getArtifactoryDevHome() throws IOException {
        String homeProperty = System.getProperty("artifactory.home");
        File devArtHome;
        if (homeProperty != null) {
            devArtHome = new File(homeProperty).getAbsoluteFile();
        } else {
            devArtHome = new File(WebappUtils.getArtifactoryDevenv(), ".artifactory");
        }
        if (!devArtHome.exists() && !devArtHome.mkdirs()) {
            throw new RuntimeException("Failed to create home dir: " + devArtHome.getAbsolutePath());
        }
        System.setProperty(ArtifactoryHome.SYS_PROP, devArtHome.getAbsolutePath());
        return devArtHome;
    }

    static void startAccessProcess() throws IOException {
        if (!Boolean.getBoolean("access.process.skip")) {
            // start Access server. re-use existing service is detected on the same port
            // the process will register a shutdown hook so we will let the JVM kill it
            File accessDevHome = getAccessDevHome();
            new AccessProcess(new AccessProcessConfig(accessDevHome)).start();
        }
    }

    private static File getAccessDevHome() throws IOException {
        String homeProperty = System.getProperty(ACCESS_HOME_SYS_PROP);
        File devHome;
        if (homeProperty != null) {
            devHome = new File(homeProperty).getAbsoluteFile();
        } else {
            String artHomeProperty = System.getProperty(ArtifactoryHome.SYS_PROP);
            if (artHomeProperty != null) {
                devHome = new File(new File(artHomeProperty), "access"); //embedded home
            } else {
                devHome = new File(WebappUtils.getArtifactoryDevenv(), ".jfrog-access");
            }
        }
        if (!devHome.exists() && !devHome.mkdirs()) {
            throw new RuntimeException("Failed to create home dir: " + devHome.getAbsolutePath());
        }
        System.setProperty(ACCESS_HOME_SYS_PROP, devHome.getAbsolutePath());
        return devHome;
    }
}
