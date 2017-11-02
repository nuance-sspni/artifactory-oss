/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.work.queue;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.AsyncWorkQueueProviderService;
import org.artifactory.api.repo.WorkItem;
import org.artifactory.api.repo.WorkQueue;
import org.artifactory.common.ConstantValues;
import org.artifactory.mbean.MBeanRegistrationService;
import org.artifactory.work.queue.mbean.WorkQueueMBean;
import org.artifactory.work.queue.mbean.buildEvent.BuildRetentionWorkQueue;
import org.artifactory.work.queue.mbean.chef.ChefMetadataWorkQueue;
import org.artifactory.work.queue.mbean.composer.ComposerExtractorWorkQueue;
import org.artifactory.work.queue.mbean.composer.ComposerIndexerWorkQueue;
import org.artifactory.work.queue.mbean.debian.DebianWorkQueue;
import org.artifactory.work.queue.mbean.maven.MavenMetadataWorkQueue;
import org.artifactory.work.queue.mbean.maven.MavenPluginMetadataWorkQueue;
import org.artifactory.work.queue.mbean.puppet.PuppetRepoWorkQueue;
import org.artifactory.work.queue.mbean.puppet.PuppetWorkQueue;
import org.artifactory.work.queue.mbean.yum.YumVirtualWorkQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author gidis
 */
@Service
public class AsyncWorkQueueServiceImpl implements AsyncWorkQueueProviderService {
    private static final Logger log = LoggerFactory.getLogger(AsyncWorkQueueServiceImpl.class);
    private final Map<String, WorkQueue<WorkItem>> workQueueMap = new ConcurrentHashMap<>();

    @Override
    public void closeAllQueues() {
        for (Map.Entry<String, WorkQueue<WorkItem>> queueEntry : workQueueMap.entrySet()) {
            WorkQueue<WorkItem> workQueue = queueEntry.getValue();
            try {
                workQueue.stopQueue();
            } catch (Exception e) {
                log.error("Failed to close queue " + queueEntry.getKey(), e);
            }
        }
    }

    @Override
    public WorkQueue<WorkItem> getWorkQueue(Method workQueueCallback, Object target) {
        String name = workQueueCallback.getName();
        WorkQueue<WorkItem> workQueue = workQueueMap.get(name);
        if (workQueue == null) {
            synchronized (workQueueMap) {
                workQueue = workQueueMap.get(name);
                if (workQueue == null) {
                    try {
                        WorkQueueInfo info = getWorkerInfo(workQueueCallback);
                        workQueue = new WorkQueueImpl<>(
                                info.getDisplayName(), info.getMaxNumberOfThreads(), workItem -> {
                            try {
                                workQueueCallback.invoke(target, workItem);
                            } catch (Exception e) {
                                String origMsg = e.getMessage();
                                if (e instanceof InvocationTargetException && e.getCause() != null) {
                                    origMsg = e.getCause().getMessage();
                                }
                                String msg =
                                        "Failed to call work queue '" + name + "' callback due to :" + origMsg;
                                log.error(msg, e);
                                throw new RuntimeException(msg, e);
                            }
                        });
                        workQueueMap.put(name, workQueue);
                        WorkQueueMBean mBean = createMBean(workQueue, info);
                        ContextHelper.get().beanForType(MBeanRegistrationService.class)
                                .register(mBean, "Work queue", info.displayName);
                    } catch (Exception e) {
                        log.error("Failed to initialize work queue {}", name, e);
                    }
                }

            }
        }
        return workQueue;
    }

    private WorkQueueMBean createMBean(WorkQueue<WorkItem> workQueue, WorkQueueInfo info)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        try {
            Constructor constructor = info.getmBeanClass().getConstructor(WorkQueueMBean.class);
            return (WorkQueueMBean) constructor.newInstance(workQueue);
        } catch (Exception e) {
            log.error("Failed to create mbean for work queue {}", workQueue.getName());
            throw e;
        }
    }

    private WorkQueueInfo getWorkerInfo(Method workQueueCallback) {
        String name = workQueueCallback.getName();
        String className = workQueueCallback.getDeclaringClass().getName();
        switch (name) {
            case "calculateMavenMetadataAsync": {
                int maxNumberOfThreads = ConstantValues.mvnMetadataCalculationWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Maven Metadata", MavenMetadataWorkQueue.class);
            }
            case "calculateMavenPluginsMetadataAsync": {
                int maxNumberOfThreads = ConstantValues.mvnMetadataPluginCalculationWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Plugin Maven Metadata", MavenPluginMetadataWorkQueue.class);
            }
            case "calculateYumVirtualMetadataAsync":
            case "calculateYumVirtualMetadata": {
                int maxNumberOfThreads = ConstantValues.yumVirtualMetadataCalculationWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Yum Virtual Metadata", YumVirtualWorkQueue.class);
            }
            case "handlePackageDeployment":
            case "handlePackageDeletion": {
                int maxNumberOfThreads = ConstantValues.composerMetadataExtractorWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Composer Metadata Extraction", ComposerExtractorWorkQueue.class);
            }
            case "extractAndIndexAllComposerPackages":
            case "indexComposerPackageAndRepo": {
                int maxNumberOfThreads = ConstantValues.composerMetadataIndexWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Composer Metadata", ComposerIndexerWorkQueue.class);
            }
            case "calculateMetadataInternalAsync":
            case "calculateMetadataInternalSync":
            case "notifyHaOnDebianCalculateMetadataAsyncAfterCommit": {
                int maxNumberOfThreads = ConstantValues.debianMetadataCalculationWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Debian Metadata", DebianWorkQueue.class);
            }
            case "calculatePuppetMetadataAsync": {
                int maxNumberOfThreads = ConstantValues.puppetMetadataCalculationWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Puppet Metadata", PuppetWorkQueue.class);
            }
            case "calculatePuppetRepoMetadataAsync": {
                int maxNumberOfThreads = ConstantValues.puppetRepoMetadataCalculationWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Puppet Repository Metadata", PuppetRepoWorkQueue.class);
            }
            case "extractAndIndexChefCookbooks":
            case "extractAndIndexSingleChefCookbook":
            case "calculateVirtualRepoMetadata":{
                int maxNumberOfThreads = ConstantValues.chefMetadataIndexWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Chef Metadata", ChefMetadataWorkQueue.class);
            }
            case "deleteBuildAsync":{
                int maxNumberOfThreads = ConstantValues.buildRetentionWorkers.getInt();
                return new WorkQueueInfo(maxNumberOfThreads, "Build Retention Job", BuildRetentionWorkQueue.class);
            }
            default: {
                throw new RuntimeException(
                        "Unsupported work queue: the work queue: class" + className + " method: " + name +
                                " is not supported by this service");
            }
        }
    }

    private class WorkQueueInfo {
        private int maxNumberOfThreads;
        private String displayName;
        private Class mBeanClass;

        public WorkQueueInfo(int maxNumberOfThreads, String displayName, Class mBeanClass) {
            this.maxNumberOfThreads = maxNumberOfThreads;
            this.displayName = displayName;
            this.mBeanClass = mBeanClass;
        }

        public int getMaxNumberOfThreads() {
            return maxNumberOfThreads;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Class getmBeanClass() {
            return mBeanClass;
        }
    }
}
