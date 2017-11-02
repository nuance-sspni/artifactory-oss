/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.work.queue;

import com.google.common.collect.Lists;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Unit tests for the {@link WorkQueueImpl}.
 *
 * @author Yossi Shaul
 */
@Test
public class WorkQueueTest extends WorkQueueTestBase {

    /**
     * Test that if there is no worker then no job is being done
     */
    public void offerTheSameWorkWithZeroWorkers() throws InterruptedException {
        List<AtomicInteger> counts = Collections.nCopies(10, new AtomicInteger(0));
        WorkQueueImpl<IntegerWorkItem> wq = new WorkQueueImpl<>(QUEUE_NAME, 0,
                workItem -> counts.get(workItem.getValue()).incrementAndGet());

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                wq.offerWork(new IntegerWorkItem(i));
                executorService.submit(wq::doJobs);
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(1500, TimeUnit.SECONDS);
        // We expect each job to be executed at least once
        counts.forEach(count -> assertThat(count.get()).isEqualTo(0));
    }

    public void offerTheSameWorkConcurrently() throws InterruptedException {
        List<AtomicInteger> counts = Collections.nCopies(10, new AtomicInteger(0));
        WorkQueueImpl<IntegerWorkItem> wq = new WorkQueueImpl<>("Test Queue", 1,
                workItem -> counts.get(workItem.getValue()).incrementAndGet());

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10000; j++) {
                wq.offerWork(new IntegerWorkItem(i));
                executorService.submit(wq::doJobs);
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(1500, TimeUnit.SECONDS);
        // We expect each job to be executed at least once
        counts.forEach(count -> assertThat(count.get()).isGreaterThanOrEqualTo(1));
    }

    public void offerWorkWithNWorkers() throws InterruptedException {
        List<AtomicInteger> counts = Collections.nCopies(1000, new AtomicInteger(0));
        WorkQueueImpl<IntegerWorkItem> wq = new WorkQueueImpl<>("Test Queue", 4,
                workItem -> counts.get(workItem.getValue()).incrementAndGet());

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                wq.offerWork(new IntegerWorkItem(i));
                executorService.submit(wq::doJobs);
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(1500, TimeUnit.SECONDS);
        // We expect each job to be executed at least once
        counts.forEach(count -> assertThat(count.get()).isGreaterThanOrEqualTo(1));
    }

    public void waitUntilTest() throws InterruptedException, ExecutionException {
        List<AtomicInteger> counts = Collections.nCopies(10, new AtomicInteger(0));
        WorkQueueImpl<IntegerWorkItem> wq = new WorkQueueImpl<>("Test Queue",8,
                new Consumer<IntegerWorkItem>() {
                    @Override
                    public void accept(IntegerWorkItem workItem) {
                        counts.get(workItem.getValue()).incrementAndGet();
                    }
                });

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        ExecutorService testExecutor = Executors.newFixedThreadPool(20);
        ArrayList<Future> futures = Lists.newArrayList();
        for (int i = 0; i < 6; i++) {
            futures.add(testExecutor.submit(() -> {
                for (int j = 0; j < 100000; j++) {
                    IntegerWorkItem workItem = new IntegerWorkItem((int) (Math.random() * 10d));
                    wq.offerWork(workItem);
                    if (wq.availablePermits() != 0) {
                        // there's a small chance we will miss event (especially if the work queue contain only single worker)
                        executorService.submit(() -> wq.doJobs());
                    }
                    wq.waitForItemDone(workItem);
                }
                return null;
            }));
        }
        for (Future future : futures) {
            future.get();
        }
        testExecutor.shutdown();
        testExecutor.awaitTermination(1500, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(1500, TimeUnit.SECONDS);
        // We expect each job to be executed at least once
        counts.forEach(count -> assertThat(count.get()).isGreaterThanOrEqualTo(1));
    }
}
