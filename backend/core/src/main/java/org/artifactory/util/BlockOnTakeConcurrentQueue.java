/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.util;

import com.google.common.collect.Iterators;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A queue based on {@link ConcurrentLinkedQueue} which blocks on {@link #take()} (similar to a
 * {@link java.util.concurrent.BlockingQueue}) but does not block or lock on {@link #offer(Object)}.
 * Since {@link #size()} in <code>ConcurrentLinkedQueue</code> is done in <code>O(n)</code>, this implementation tries
 * to minimize the actual size calculations, but with the cost of correctness.
 * The queue has a fixed capacity. Once the size equals the capacity, new elements will not be added to the queue.
 * <p>Created on 08/07/16
 *
 * @author Yinon Avraham
 * @see #take()
 * @see #offer(Object)
 * @see #capacity()
 */
public class BlockOnTakeConcurrentQueue<E> implements Queue<E> {
    private static final long POLLING_SLEEP_MILLIS = 50;

    private final ConcurrentLinkedQueue<E> queue;
    private final int capacity;
    private final long cacheSizeForMillis;
    private final AtomicLong lastSizeUpdated = new AtomicLong(0);
    private final AtomicInteger cachedSize = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Construct a new queue with a given capacity and a defined period of time to cache the queue size.
     * @param capacity the capacity
     * @param cacheSizeForMillis the minimum period of time (in milliseconds) between size calculations
     */
    public BlockOnTakeConcurrentQueue(int capacity, long cacheSizeForMillis) {
        checkValue(capacity, cap -> cap > 0, "Capacity must be a positive integer: " + capacity);
        checkValue(cacheSizeForMillis, ms -> ms >= 0, "Cache size for millis must be a non-negative integer: " + cacheSizeForMillis);
        this.queue = new ConcurrentLinkedQueue<>();
        this.capacity = capacity;
        this.cacheSizeForMillis = cacheSizeForMillis;
    }

    /**
     * Get the fixed capacity defined for this queue
     * @return the capacity
     */
    public int capacity() {
        return this.capacity;
    }

    /**
     * Take an element from the queue's head.
     * This method blocks until an element is available and removes it from the queue.
     * @return the element
     * @throws InterruptedException
     */
    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        E element;
        try {
            while (cachedSize.get() == 0 || (element = poll()) == null) {
                Thread.sleep(POLLING_SLEEP_MILLIS);
            }
            return element;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take an element from the queue's head.
     * This method blocks until an element is available and removes it from the queue.
     * @return the element
     * @throws InterruptedException
     */
    public E take(long timeout, TimeUnit unit) throws InterruptedException {
        long millisRemained = unit.toMillis(timeout);
        lock.lockInterruptibly();
        E element = null;
        try {
            while (millisRemained > 0 && (cachedSize.get() == 0 || (element = poll()) == null)) {
                long millisToSleep = Math.min(POLLING_SLEEP_MILLIS, millisRemained);
                millisRemained -= millisToSleep;
                Thread.sleep(POLLING_SLEEP_MILLIS);
            }
            if (element == null) {
                element = poll();
            }
            return element;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll() {
        E element = queue.poll();
        if (element != null) {
            cachedSize.decrementAndGet();
        }
        return element;
    }

    @Override
    public E element() {
        return queue.element();
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public boolean add(E e) {
        if (cachedSize.get() >= capacity) {
            throw new IllegalStateException("Queue is already full");
        }
        if (queue.add(e)) {
            cachedSize.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean offer(E e) {
        if (cachedSize.get() >= capacity) {
            return false;
        }
        if (queue.offer(e)) {
            cachedSize.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public E remove() {
        E element = queue.remove();
        if (element != null) {
            cachedSize.decrementAndGet();
        }
        return element;
    }

    @Override
    public boolean remove(Object o) {
        if (queue.remove(o)) {
            cachedSize.decrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        List<E> elements = c.stream().filter(Objects::nonNull).collect(Collectors.toList());
        boolean changed = queue.addAll(elements);
        if (changed) {
            cachedSize.getAndAdd(elements.size());
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = queue.removeAll(c);
        if (changed) {
            updateSize(lastSizeUpdated.get(), System.currentTimeMillis());
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = queue.retainAll(c);
        if (changed) {
            updateSize(lastSizeUpdated.get(), System.currentTimeMillis());
        }
        return changed;
    }

    @Override
    public void clear() {
        queue.clear();
        updateSize(lastSizeUpdated.get(), System.currentTimeMillis());
    }

    @Override
    public int size() {
        long currentTime = System.currentTimeMillis();
        long lastUpdated = lastSizeUpdated.get();
        if (currentTime - lastUpdated > cacheSizeForMillis) {
            updateSize(lastUpdated, currentTime);
        }
        return cachedSize.get();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return Iterators.unmodifiableIterator(queue.iterator());
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return queue.toArray(a);
    }

    private void updateSize(long lastUpdated, long currentTime) {
        if (lastSizeUpdated.compareAndSet(lastUpdated, currentTime)) {
            cachedSize.set(queue.size());
        }
    }

    /**
     * Get the remaining capacity in this queue (<code>remainingCapacity = capacity - size</code>)
     * @return the remaining capacity
     */
    public int remainingCapacity() {
        return capacity - cachedSize.get();
    }

    private static <T> void checkValue(T value, Predicate<T> predicate, String message) {
        if (!predicate.test(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
