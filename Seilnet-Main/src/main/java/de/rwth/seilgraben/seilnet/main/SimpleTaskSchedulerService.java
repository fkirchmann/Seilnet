/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.minlog.Log;

import lombok.Synchronized;

/**
 * Allows the same basic task to be run at several different scheduled execution times. Each
 * execution time is linked with an ID.
 * 
 * Internally, this class uses a {@link ScheduledThreadPoolExecutor} with one thread to run tasks in
 * the background.
 *
 * @author Felix Kirchmann
 */
public class SimpleTaskSchedulerService
{
	private final Map<Integer, ScheduledFuture<?>>	tasks	= new HashMap<>();
	private final ScheduledThreadPoolExecutor		executor;
	private final TaskIDRunnable					runnable;
	
	/**
	 * @param runnable
	 *            The task to be run at each execution time.
	 */
	public SimpleTaskSchedulerService(TaskIDRunnable runnable)
	{
		this.runnable = runnable;
		executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory()
		{
			@Override
			public Thread newThread(Runnable r)
			{
				Thread t = new Thread(r);
				t.setDaemon(true);
				t.setName("ExpirationService");
				return t;
			}
		});
		executor.setRemoveOnCancelPolicy(true);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
	}
	
	/**
	 * Schedules a task for execution in the background thread.
	 * 
	 * @param taskId
	 *            The ID of the new task. If a task is already registered under the given ID, it
	 *            will be removed, or finish execution if it is already running.
	 * @param runAt
	 *            The time to run this task at. If it is in the past, the task will be scheduled to
	 *            execute immediately.
	 */
	@Synchronized
	public void schedule(int taskId, Instant runAt)
	{
		cancel(taskId);
		
		long delay = Instant.now().until(runAt, ChronoUnit.MILLIS);
		if (delay < 0)
		{
			Log.debug("tid: " + taskId + ", delay: " + delay + ", sched: " + runAt);
			delay = 0;
		}
		ScheduledFuture<?> oldScheduledTask = tasks.put(taskId,
				executor.schedule(TaskIDRunnable.toRunnable(taskId, runnable), delay, TimeUnit.MILLISECONDS));
		if (oldScheduledTask != null)
		{
			oldScheduledTask.cancel(false);
		}
		
	}
	
	/**
	 * Cancels the execution of a task.
	 * 
	 * @param taskId
	 *            The ID of the task to cancel.
	 * @return <code>true</code> if the task was successfully removed, <code>false</code> if no task
	 *         is currently registered under the given ID.
	 */
	@Synchronized
	public boolean cancel(int taskId)
	{
		ScheduledFuture<?> task = tasks.remove(taskId);
		if (task != null)
		{
			task.cancel(false);
			return true;
		}
		return false;
	}
	
	public static interface TaskIDRunnable
	{
		public void run(int taskId);
		
		public static Runnable toRunnable(int taskId, TaskIDRunnable r)
		{
			return new Runnable()
			{
				@Override
				public void run()
				{
					r.run(taskId);
				}
			};
		}
	}
}
