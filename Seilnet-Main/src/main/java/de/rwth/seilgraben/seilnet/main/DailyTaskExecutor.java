/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import com.esotericsoftware.minlog.Log;

import java.time.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Modified variant of https://stackoverflow.com/a/20388073
 */
public class DailyTaskExecutor
{
    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setDaemon(true);
                    thread.setName("DailyTaskExecutor");
                    return thread;
                });
    Runnable task;
    volatile boolean isStopIssued;
    private final LocalTime dailyExecutionTime;

    public DailyTaskExecutor(LocalTime dailyExecutionTime, Runnable task)
    {
        this.task = task;
        this.dailyExecutionTime = dailyExecutionTime;
        scheduleNextExecution();
    }

    private void scheduleNextExecution()
    {
        Runnable taskWrapper = () -> {
            scheduleNextExecution();
            try
            {
                task.run();
            }
            catch(Exception e) {
                Log.warn(LogCategory.CFG, "Daily task executable threw an exception", e);
            }
        };
        long delay = computeNextDelay();
        executorService.schedule(taskWrapper, delay, TimeUnit.SECONDS);
    }

    private long computeNextDelay()
    {
        LocalDateTime localNow = LocalDateTime.now();
        ZoneId currentZone = ZoneId.systemDefault();
        ZonedDateTime zonedNow = ZonedDateTime.of(localNow, currentZone);
        ZonedDateTime zonedNextTarget = zonedNow.with(dailyExecutionTime);
        if(zonedNow.plusSeconds(10).compareTo(zonedNextTarget) >= 0) {
            zonedNextTarget = zonedNextTarget.plusDays(1);
        }
        Duration duration = Duration.between(zonedNow, zonedNextTarget);
        return duration.getSeconds();
    }

    /*public void stop()
    {
        executorService.shutdown();
    }*/
}
