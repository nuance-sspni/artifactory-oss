package org.artifactory.common.config.watch;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.*;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author gidis
 */
public class FileWatchingManager {

    private final WatchService watcher;
    private final Thread watchThread;
    private boolean runWatch = true;

    private Map<WatchKey, ConfigInfo> configInfos;
    private FileChangedListener listener;
    private Logger log;

    public FileWatchingManager(FileChangedListener listener) {
        this.listener = listener;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            configInfos = Maps.newHashMap();
            watchThread = new Thread(this::doWatch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerDirectoryListener(File file, String configPrefix) {
        if (!runWatch) {
            log.error("Registering folder '" + configPrefix + "' while shutting down!");
            return;
        }
        try {
            if (!watchThread.isAlive()) {
                watchThread.start();
            }
            Path path = Paths.get(file.getAbsolutePath());
            ConfigInfo configInfo = configInfos.get(path);
            if (configInfo == null) {
                WatchKey key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                configInfos.put(key, new ConfigInfo(path, configPrefix));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerDirectoryListener(File file) {
        registerDirectoryListener(file, null);
    }

    private void doWatch() {
        Logger log = getLogger();
        log.info("Starting watch of folder configurations");
        while (runWatch) {
            try {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException ex) {
                    return;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (!runWatch) {
                        break;
                    }
                    if (event == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent.Kind<Path> kind = (WatchEvent.Kind<Path>) event.kind();
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path name = ev.context();
                    ConfigInfo configInfo = configInfos.get(key);
                    long now = System.nanoTime();
                    Path targetPath = configInfo.path.resolve(name);
                    File target = targetPath.toFile();
                    listener.fileChanged(target, configInfo.configPrefix, kind, now);
                }
                if (!runWatch) {
                    break;
                }
                boolean valid = key.reset();
                if (!valid) {
                    log.error("Fatal error can't synchronize between Artifactory Config files");
                    return;
                }
            } catch (ClosedWatchServiceException e) {
                if (runWatch) {
                    log.error("Watch service was closed for synchronize between Artifactory Config files due to: " +
                            e.getMessage(), e);
                    // TODO: [by fsi] restart the service
                } else {
                    // All good just shutting down Artifactory
                    log.info("Watch service ended on destroy");
                }
            } catch (Exception e) {
                log.error("Unknown exception while watching for file changes: " + e.getMessage(), e);
            }
        }
        log.info("End watch of folder configurations");
        if (watchThread != null) {
            synchronized (watchThread) {
                watchThread.notifyAll();
            }
        }
    }

    public void destroy() {
        try {
            runWatch = false;
            watcher.close();
            synchronized (watchThread) {
                watchThread.wait(1000L);
            }
        } catch (Exception e) {
            String msg = "Watch service could not be closed smoothly due to: " + e.getMessage();
            if (log != null) {
                log.error(msg, e);
            } else {
                System.err.println(msg);
                e.printStackTrace();
            }
        }
    }

    private Logger getLogger() {
        if (log == null) {
            log = LoggerFactory.getLogger(FileWatchingManager.class);
        }
        return log;
    }

    static class ConfigInfo {
        final Path path;
        final String configPrefix;

        public ConfigInfo(Path path, String configPrefix) {
            this.path = path;
            this.configPrefix = configPrefix;
        }
    }
}
