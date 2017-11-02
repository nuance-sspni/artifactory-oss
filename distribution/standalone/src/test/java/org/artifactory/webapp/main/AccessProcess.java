package org.artifactory.webapp.main;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.DbType;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.http.AccessHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.SocketUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.StringUtils.*;
import static org.jfrog.access.util.AccessCredsFileHelper.saveAccessCreds;
import static org.jfrog.security.file.SecurityFolderHelper.PERMISSIONS_MODE_600;
import static org.jfrog.security.file.SecurityFolderHelper.setPermissionsOnSecurityFile;

/**
 * This class spawns Access in a new process or re-uses an existing Access server.
 *
 * @author Yossi Shaul
 */
public class AccessProcess {
    private static final Logger log = LoggerFactory.getLogger(AccessProcess.class);

    private final File accessJarFile;
    private final File homeDir;
    private final boolean bundled;
    private final boolean tlsEnabled;
    private final int port;
    private final String contextPath;
    private final String serverUrl;
    private final String[] initialAdminCreds;
    private final DebugConfig debugConfig;
    private final SystemProperties systemProperties = new SystemProperties();

    private StartedProcess process;

    /**
     * Constructs a new Access Process. To start the process call {@link AccessProcess#start()} or
     * {@link AccessProcess#startAndWait()}.
     *
     * @param config the configuration for the access server process
     */
    public AccessProcess(AccessProcessConfig config) {
        this.accessJarFile = resolveAccessJarFle();
        this.homeDir = config.homeDir;
        this.bundled = config.bundled;
        this.tlsEnabled = config.tlsEnabled;
        this.port = resolvePort(config);
        this.contextPath = resolveContextPath(config);
        this.serverUrl = buildServerUrl();
        this.initialAdminCreds = config.initialAdminCreds;
        this.debugConfig = config.debugConfig;
    }

    private File resolveAccessJarFle() {
        try {
            File accessJarFile = WebappUtils.getAccessStandaloneJar();
            if (!accessJarFile.exists()) {
                throw new RuntimeException("Access jar file not found: " + accessJarFile);
            }
            return accessJarFile;
        } catch (IOException e) {
            throw new RuntimeException("Could not locate access standalone jar", e);
        }
    }

    private int resolvePort(AccessProcessConfig config) {
        return config.port == 0 ? SocketUtils.findAvailableTcpPort() : config.port;
    }

    private String resolveContextPath(AccessProcessConfig config) {
        return isBlank(config.contextPath) ? "" : "/" + removeStart(config.contextPath, "/");
    }

    private String buildServerUrl() {
        String scheme = this.tlsEnabled ? "https" : "http";
        return scheme + "://localhost:" + this.port + this.contextPath;
    }

    /**
     * Starts a new Access server in a new process if no other Access service already listens at the give port.
     * If a new process is spawned, this will also register a shutdown hook to shut it down when the JVM exists.
     *
     * @return Reference to this
     */
    public AccessProcess start() {
        try {
            if (isAccessAlive()) {
                log.info("Access process found listening on: {}", getAccessUrl());
                return this;
            }
            log.info("Starting Access process on: {} (home dir: {})", getAccessUrl(), homeDir);
            systemProperties.put(ConstantValues.accessClientServerUrlOverride.getPropertyName(), getAccessUrl());
            if (initialAdminCreds != null) {
                createBootstrapCreds();
            }
            List<String> cmd = buildCommand();
            process = new ProcessExecutor()
                    .command(cmd)
                    .environment("jfrog.access.home", homeDir == null ? "" : homeDir.getAbsolutePath())
                    .redirectOutput(System.out)//Slf4jStream.of(LoggerFactory.getLogger("AccessProcess")).asInfo())
                    .redirectError(System.err)//Slf4jStream.of(LoggerFactory.getLogger("AccessProcess")).asError())
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop()));
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createBootstrapCreds() {
        File bootstrapCredsFile = new File(homeDir, "etc/bootstrap.creds");
        try {
            FileUtils.forceMkdir(bootstrapCredsFile.getParentFile());
            saveAccessCreds(bootstrapCredsFile, initialAdminCreds[0], initialAdminCreds[1]);
            setPermissionsOnSecurityFile(bootstrapCredsFile.toPath(), PERMISSIONS_MODE_600);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save initial admin credentials to file: " + bootstrapCredsFile, e);
        }
    }

    private List<String> buildCommand() {
        List<String> cmd = Lists.newArrayList("java", "-Xmx64m");
        cmd.add("-Djfrog.access.bundled=" + bundled);
        cmd.add("-Djfrog.access.bundled.use.own.derbydb.home=true");
        cmd.add("-Djfrog.access.http.tls.enabled=" + tlsEnabled);
        cmd.add("-Djfrog.access.http.port=" + port);
        if (isNotBlank(contextPath)) {
            cmd.add("-Dserver.contextPath=" + contextPath);
        }
        if (debugConfig != null) {
            cmd.add(debugConfig.toVmArgument());
        } else {
            String javaOpts = System.getenv("ACCESS_PROCESS_DEBUG_OPTS");
            if (javaOpts != null) {
                cmd.add(javaOpts);
            }
        }
        List<String> additionalJdbcDrivers = Stream.of(DbType.values())
                .map(dbType -> new File(accessJarFile.getParentFile(), "jdbc_" + dbType + ".jar"))
                .filter(File::exists)
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        if (!additionalJdbcDrivers.isEmpty()) {
            cmd.add("-Dloader.path=" + String.join(",", additionalJdbcDrivers));
        }
        cmd.addAll(asList("-jar", accessJarFile.getAbsolutePath()));
        return cmd;
    }

    /**
     * Starts the Access process and waits until Access server is ready
     */
    public AccessProcess startAndWait() {
        start();
        waitForStartup();
        return this;
    }

    /**
     * @return URL of the Access server
     */
    public String getAccessUrl() {
        return serverUrl;
    }

    /**
     * Block for 30 seconds or until Access responds.
     *
     * @throws RuntimeException if interrupted or if Access is not ready in 30 seconds
     */
    public void waitForStartup() {
        waitForStartup(30000);
    }

    /**
     * Block for specified time in millis or until Access responds.
     *
     * @param maxTime Max time in milliseconds to wait for Access to start
     * @throws RuntimeException if interrupted or if Access is not ready in the specified time
     */
    public void waitForStartup(int maxTime) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxTime) {
            if (isAccessAlive()) {
                log.info("Finished waiting for Access process after {} millis", System.currentTimeMillis() - start);
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
        }
        throw new RuntimeException("Access is not responding after " + maxTime + " millis");
    }

    /**
     * Stops Access server if it was started by this instance.
     */
    public void stop() {
        systemProperties.restoreOriginal();
        if (process != null) {
            process.getFuture().cancel(true);
        }
    }

    private boolean isAccessAlive() {
        try {
            new AccessHttpClient(getAccessUrl()).ping();
            return true;
        } catch (AccessClientException e) {
            // log and continue retry
            log.debug("Communication to Access failed: " + e.getMessage());
        }
        return false;
    }

    public static class AccessProcessConfig {
        private final File homeDir;
        private boolean tlsEnabled = true;
        private String contextPath = null;
        private int port = 8340;
        private boolean bundled = false;
        private String[] initialAdminCreds = new String[] {"admin", "password"};
        private DebugConfig debugConfig = null;

        public AccessProcessConfig(@Nonnull File homeDir) {
            this.homeDir = homeDir;
        }

        public AccessProcessConfig tlsEnabled(boolean enabled) {
            this.tlsEnabled = enabled;
            return this;
        }

        public AccessProcessConfig contextPath(String path) {
            this.contextPath = path;
            return this;
        }

        public AccessProcessConfig port(int port) {
            this.port = port;
            return this;
        }

        public AccessProcessConfig randomPort() {
            this.port = 0;
            return this;
        }

        public AccessProcessConfig bundled(boolean bundled) {
            this.bundled = bundled;
            return this;
        }

        public AccessProcessConfig initialAdminCredentials(String user, String password) {
            this.initialAdminCreds = new String[] {user, password};
            return this;
        }

        public AccessProcessConfig generatedAdminCredentials() {
            this.initialAdminCreds = null;
            return this;
        }

        public AccessProcessConfig debug(int port, boolean suspend) {
            this.debugConfig = new DebugConfig(port, suspend);
            return this;
        }
    }

    private static class DebugConfig {
        private final int port;
        private final boolean suspend;

        private DebugConfig(int port, boolean suspend) {
            this.port = port;
            this.suspend = suspend;
        }

        public String toVmArgument() {
            return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (suspend ? "y" : "n") + ",address=" + port;
        }
    }

    private static class SystemProperties {
        private final Map<String, String> originalProperties = Maps.newHashMap();
        private final Map<String, String> currentProperties = Maps.newHashMap();

        void put(String key, String value) {
            init(key);
            currentProperties.put(key, value);
            System.setProperty(key, value);
        }

        void clear(String key) {
            init(key);
            System.clearProperty(key);
        }

        String get(String key) {
            return System.getProperty(key);
        }

        void restoreOriginal() {
            originalProperties.entrySet().forEach(entry -> {
                //restore original value only if the current system property was set by this instance.
                if (Objects.equals(System.getProperty(entry.getKey()), currentProperties.get(entry.getKey()))) {
                    if (entry.getValue() == null) {
                        System.clearProperty(entry.getKey());
                    } else {
                        System.setProperty(entry.getKey(), entry.getValue());
                    }
                }
            });
        }

        private void init(String key) {
            if (!originalProperties.containsKey(key)) {
                originalProperties.put(key, System.getProperty(key));
            }
        }
    }
}
