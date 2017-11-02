/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.work.queue;

import org.artifactory.api.repo.WorkItem;
import org.artifactory.api.repo.WorkQueue;
import org.artifactory.common.ConstantValues;
import org.artifactory.work.queue.mbean.WorkQueueMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * A work queue that eliminates jobs based on a passed criteria.<p>
 * The thread offering work for this queue might start working on the queue, and may work on all the queued items.
 *
 * @author Yossi Shaul
 */
public class WorkQueueImpl<T extends WorkItem> implements WorkQueue<T>, WorkQueueMBean {
    private static final Logger log = LoggerFactory.getLogger(WorkQueueImpl.class);

    private final NonBlockingOnWriteQueue<T> queue;
    private final String name;
    private final int workers;
    private final Consumer<T> workExecutor;
    private final Semaphore semaphore;

    /**
     * Creates a new work queue with the given max workers.<p>
     * If the max workers is greater than one, the provider work executor must be thread safe.
     *
     * @param name         Symbolic name for the work queue
     * @param workers      Maximum workers allowed to work on this queue
     * @param workExecutor The work to perform for each element in the queue
     */
    public WorkQueueImpl(String name, int workers, Consumer<T> workExecutor) {
        this.name = name;
        this.workers = workers;
        this.workExecutor = workExecutor;
        this.semaphore = new Semaphore(workers);
        this.queue = new NonBlockingOnWriteQueue<>(name);
    }

    @Override
    public boolean offerWork(T workItem) {
        log.trace("adding {}: to '{}'", workItem, name);
        boolean added = queue.addToPending(workItem);
        if (!added) {
            log.trace("{}: already contains '{}'", name, workItem);
        } else {
            log.trace("{}: successfully added to '{}'", workItem, name);
        }
        return added;
    }

    @Override
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    @Override
    public void doJobs() {
        // Limit number of workers by semaphore
        if (!semaphore.tryAcquire()) {
            log.debug("{}: max workers already processing ({})", name, workers);
            return;
        }
        try {
            debugStep("start processing");
            WorkQueuePromotedItem<T> promotedWorkItem;
            while ((promotedWorkItem = queue.promote()) != null) {
                T workItem = promotedWorkItem.workItem;
                traceStep("started working", workItem);
                try {
                    workExecutor.accept(workItem);
                    traceStep("finished working", workItem);
                } catch (Exception e) {
                    log.error("{}: failed to process {}", name, workItem, e);
                } finally {
                    boolean removed = queue.remove(promotedWorkItem);
                    if (removed) {
                        traceStep("successfully removed", workItem);
                    } else {
                        log.error("unexpected state: failed to remove {} from {}. Queue size pending={} running={}",
                                workItem, name,
                                queue.getQueueSize(), queue.getRunningSize());
                    }
                }
            }
        } finally {
            semaphore.release();
        }
    }

    private void debugStep(String stepName) {
        // queue size is expensive, and returns current running tasks from other nodes as well in HA.
        if (log.isDebugEnabled()) {
            log.debug("{}: {}. Queue size pending={} running={}", name, stepName, queue.getQueueSize(),
                    queue.getRunningSize());
        }
    }

    private void traceStep(String stepName, T workItem) {
        // queue size is expensive, and returns current running tasks from other nodes as well in HA.
        if (log.isTraceEnabled()) {
            log.trace("{}: {} on {}. Queue size pending={} running={}", name, stepName, workItem, queue.getQueueSize(),
                    queue.getRunningSize());
        }
    }

    @Override
    public void stopQueue() {
        queue.stop();
    }

    @Override
    public int getQueueSize() {
        return queue.getQueueSize();
    }

    @Override
    public int getNumberOfWorkers() {
        return workers - semaphore.availablePermits();
    }

    @Override
    public int getMaxNumberOfWorkers() {
        return workers;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void waitForItemDone(T workItem) {
        if (queue.contains(workItem)) {
            long timeout = ConstantValues.workQueueSyncExecutionTimeoutMillis.getLong();
            // Pending or running => Wait on work item
            synchronized (workItem) {
                if (queue.contains(workItem)) {
                    try {
                        workItem.wait(timeout);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(
                                "Work Item " + workItem + " did not finished completion in " + timeout + "ms", e);
                    }
                }
            }
        }
    }
}
