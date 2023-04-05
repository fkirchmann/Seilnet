/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import com.google.common.cache.Cache;
import com.google.common.collect.EvictingQueue;
import com.google.common.cache.CacheBuilder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Felix Kirchmann
 */
public class SimpleRateLimiter<K> {
    Cache<K, EvictingQueue<Instant>> accessTimes;
    private final Duration limitTimeframe;
    private final int limit;

    public SimpleRateLimiter(final int limit, @NonNull final Duration limitTimeframe) {
        if(limit <= 0) { throw new IllegalArgumentException("Limit must be greater than 0"); }
        accessTimes = CacheBuilder.newBuilder()
                .expireAfterAccess(limitTimeframe.get(ChronoUnit.SECONDS), TimeUnit.SECONDS)
                .build();
        this.limit = limit;
        this.limitTimeframe = limitTimeframe;
    }

    @SneakyThrows(ExecutionException.class)
    @Synchronized
    public boolean tryAcquire(K key) {
        EvictingQueue<Instant> queue = accessTimes.get(key, () -> EvictingQueue.create(limit));

        if(queue.remainingCapacity() == 0
                && !queue.peek().isBefore(Instant.now().minus(limitTimeframe))) {
            return false;
        } else {
            queue.add(Instant.now());
            return true;
        }
    }
}
