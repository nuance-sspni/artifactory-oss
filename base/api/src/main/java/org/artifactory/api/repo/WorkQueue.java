/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.api.repo;

/**
 * @author gidis
 */
public interface WorkQueue<T extends WorkItem> {

    /**
     * Offer a new work to the queue. If the work is accepted and there's no other worker thread, the offering thread
     * continues as the worker.
     *
     * @param workItem The work to perform
     * @return true if added to the work queue, false if an identical item already in queue
     */
    boolean offerWork(T workItem);

    int availablePermits();

    void doJobs();

    void stopQueue();

    String getName();

    void waitForItemDone(T workItem);
}

