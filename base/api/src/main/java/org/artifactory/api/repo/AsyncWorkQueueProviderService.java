/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.api.repo;

import java.lang.reflect.Method;

/**
 * @author gidis
 */
public interface AsyncWorkQueueProviderService {

    WorkQueue<WorkItem> getWorkQueue(Method workQueueCallback, Object target);

    void closeAllQueues();
}