/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.work.queue;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaLocking;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.WorkItem;
import org.artifactory.common.ConstantValues;
import org.jfrog.storage.common.LockingMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Gidi Shabat
 * @author Dan Feldman
 */
public class NonBlockingOnWriteQueue<T extends WorkItem> {
    private static final Logger log = LoggerFactory.getLogger(NonBlockingOnWriteQueue.class);

    private final LockingMap lockingMap;
    private final ConcurrentLinkedQueue<T> pendingQueue;
    private final Set<WorkQueuePromotedItem<T>> promotedWorkItems;
    private boolean running = true;

    /**
     * Constructor package limited
     *
     * @param name used to name the HA map, if it will be created
     */
    NonBlockingOnWriteQueue(String name) {
        lockingMap = ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaAddon.class)
                .getLockingMapFactory().getLockingMap(name);
        pendingQueue = new ConcurrentLinkedQueue<>();
        promotedWorkItems = new CopyOnWriteArraySet<>();
    }

    /**
     * Adds new Item to queue. (pending)
     */
    boolean addToPending(T workItem) {
        if (!running) {
            log.warn("Adding work item {} while queue is stopped", workItem);
            return false;
        }
        log.trace("adding {}: to queue", workItem);
        boolean result = pendingQueue.add(workItem);
        log.trace("added {} to queue, success: {}", workItem, result);
        return result;
    }

    /**
     * Changes the workItem state from pending to running
     */
    public WorkQueuePromotedItem<T> promote() {
        if (!running) {
            log.debug("Trying to promote a queue item when queue being stopped");
            return null;
        }
        T workItem = acquireLockOnKey();
        //TODO [by dan]: propagate event to all HA nodes to clear the workItem from their own queue
        //TODO or if lock couldn't be acquired clear current work item from queue (+ notify)?
        if (workItem != null) {
            log.trace("promoting  {}: workItem", workItem);
            WorkQueuePromotedItem<T> promotedItem = new WorkQueuePromotedItem<>(workItem,
                    pendingQueue.stream().filter(pendingItem -> pendingItem.equals(workItem))
                            .collect(Collectors.toList()));
            if (!promotedWorkItems.add(promotedItem)) {
                // There should be only one promoted item with the same key
                // Removing lock to make sure it does not leak
                lockingMap.removeAndUnlock(workItem.getUniqueKey());
                throw new IllegalStateException("There can be only one process running work for " + workItem);
            }
            pendingQueue.removeIf(t -> {
                if (t.equals(workItem)) {
                    // Be aware that ref equality is what's needed here
                    for (T t1 : promotedItem.pendingWorkItemsAssociated) {
                        if (t == t1) {
                            return true;
                        }
                    }
                }
                return false;
            });
            return promotedItem;
        }
        return null;
    }

    /**
     * The method tries to acquire lock on one of the queue workItems
     */
    private T acquireLockOnKey() {
        if (!running) {
            log.debug("Trying to acquire lock on a queue item when queue being stopped");
            return null;
        }
        // Some of the workItems might be temporary locked by other workers
        // Try to acquire lock on one of the workItems in pendingQueue
        for (T workItem : pendingQueue) {
            boolean acquired = false;
            try {
                long leasetTimeout = ConstantValues.workItemMaxLockLeaseTime.getLong();
                if (lockingMap instanceof HaLocking) {
                    // For HA env, we have lease timeout.
                    acquired = lockingMap.tryAddAndLock(workItem.getUniqueKey(), 0, SECONDS, leasetTimeout, MINUTES);
                } else {
                    acquired = lockingMap.tryAddAndLock(workItem.getUniqueKey(), 0, SECONDS);
                }
            } catch (InterruptedException e) {
                log.error("Failed to acquire lock for workItem {}", workItem);
            }
            // Successfully acquire lock on workItem the return the workItem else try another workItem
            if (acquired) {
                return workItem;
            }
        }
        return null;
    }

    /**
     * Remove workItem from queue
     */
    public boolean remove(WorkQueuePromotedItem<T> promotedItem) {
        try {
            promotedWorkItems.remove(promotedItem);
            finishedWithPromotedWorkItem(promotedItem);
        } catch (Exception e) {
            log.info("Exception while notifying work item  waiters " + promotedItem.workItem + " on remove: " +
                    e.getMessage(), e);
        }
        try {
            log.trace("removing workItem {}.", promotedItem.workItem);
            lockingMap.removeAndUnlock(promotedItem.workItem.getUniqueKey());
            log.trace("removed workItem {}.", promotedItem.workItem);
            return true;
        } catch (Exception e) {
            log.info("Exception while unlocking work item " + promotedItem.workItem + " on remove: " + e.getMessage(),
                    e);
            return false;
        }
    }

    /**
     * {@param promotedWorkItem} may contain identical work items, each of them may have threads polling on them to
     * complete.
     * This method notifies all polling threads on any work item that is marked as being polled on.
     */
    private void finishedWithPromotedWorkItem(WorkQueuePromotedItem<T> promotedWorkItem) {
        promotedWorkItem.pendingWorkItemsAssociated
                .forEach(polledWorkItem -> {
                    synchronized (polledWorkItem) {
                        polledWorkItem.notifyAll();
                    }
                });
    }

    /**
     * Stop and clean the queue
     */
    public void stop() {
        running = false;
        pendingQueue.clear();
        List<WorkQueuePromotedItem<T>> list = new ArrayList<>(promotedWorkItems);
        for (WorkQueuePromotedItem<T> promotedItem : list) {
            remove(promotedItem);
        }
    }

    /**
     * Returns the running size of the queue
     *
     * BEWARE: This method calls non-constant time operations on the queue and the locking map, in HA scenarios the
     * results from the locking map reflect tasks running on this node and others as well as well as the pending tasks
     * on this node alone - refrain from using this outside of tests!
     */
    public int getRunningSize() {
        int result;
        log.trace("getting the size of the queue");
        result = lockingMap.size();
        log.trace("successfully got the size of the queue.");
        return result;
    }

    /**
     * Returns the pending size of the queue
     *
     * BEWARE: This method calls non-constant time operations on the queue and the locking map, in HA scenarios the
     * results from the locking map reflect tasks running on this node and others as well as well as the pending tasks
     * on this node alone - refrain from using this outside of tests!
     */
    public int getQueueSize() {
        return pendingQueue.size();
    }

    /**
     * Check if a specific work item is in pending or working queue
     *
     * @param workItem the exact work item by Java reference
     * @return true if pending, false otherwise
     */
    public boolean contains(T workItem) {
        if (!running) {
            log.debug("Trying to check for contains a queue item when queue being stopped");
            return false;
        }
        for (T item : pendingQueue) {
            // Be aware that ref equality is what's needed here
            if (item == workItem) {
                return true;
            }
        }
        // The lock is used to make sure a promotion is not half in process
        if (lockingMap.isLocked(workItem.getUniqueKey())) {
            PromotedFoundItems promotedFoundItems = new PromotedFoundItems(workItem).invoke();
            if (!promotedFoundItems.isFoundCurrentWork()) {
                // Lock but no promoted entry, means still not inserted in promoted map
                Thread.yield();
                if (lockingMap.isLocked(workItem.getUniqueKey())) {
                    // Try one more time only
                    promotedFoundItems = new PromotedFoundItems(workItem).invoke();
                    return promotedFoundItems.isFoundExactWorkItem();
                } else {
                    // Work done
                    return false;
                }
            }
            if (promotedFoundItems.isFoundExactWorkItem()) {
                return true;
            }
        }
        return false;
    }

    private class PromotedFoundItems {
        private boolean foundCurrentWork = false;
        private boolean foundExactWorkItem = false;

        public PromotedFoundItems(T workItem) {
            for (WorkQueuePromotedItem<T> promotedItem : promotedWorkItems) {
                // Be aware that here it's unique key equality. There should be only one here
                if (promotedItem.workItem.equals(workItem)) {
                    foundCurrentWork = true;
                    // Be aware that ref equality is what's needed here
                    if (promotedItem.workItem == workItem) {
                        foundExactWorkItem = true;
                    } else {
                        // Find in associated work items
                        for (T item : promotedItem.pendingWorkItemsAssociated) {
                            if (item == workItem) {
                                foundExactWorkItem = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        public boolean isFoundCurrentWork() {
            return foundCurrentWork;
        }

        public boolean isFoundExactWorkItem() {
            return foundExactWorkItem;
        }

        public PromotedFoundItems invoke() {
            foundCurrentWork = false;
            foundExactWorkItem = false;
            return this;
        }
    }
}
