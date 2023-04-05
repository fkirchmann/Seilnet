/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.esotericsoftware.minlog.Log;

import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList;
import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList.NetworkHost;
import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList.NetworkHostListBuilder;
import de.rwth.seilgraben.seilnet.util.Func;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 *
 * @author Felix Kirchmann
 */
public class DnsmasqDhcpHostMonitor
{
	@Getter
	private volatile NetworkHostList	hosts;
	private final Path					leaseFilePath;
	private final Path					leaseFolderPath;
	
	private final WatcherThread			watcher;
	private final EventNotifierThread	eventNotifier;
	
	public DnsmasqDhcpHostMonitor(File leaseFile) throws IOException
	{
		leaseFilePath = leaseFile.toPath().normalize();
		leaseFolderPath = leaseFilePath.getParent();
		
		readHosts();
		
		WatchService watchService = leaseFolderPath.getFileSystem().newWatchService();
		leaseFolderPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
		
		eventNotifier = new EventNotifierThread();
		watcher = new WatcherThread(watchService, eventNotifier);
	}
	
	public void setListener(NetworkHostListener listener)
	{
		eventNotifier.setListener(listener);
	}
	
	public void halt()
	{
		watcher.halt();
		eventNotifier.halt();
	}
	
	private void readHosts() throws IOException
	{
		try
		{
			NetworkHostListBuilder builder = NetworkHostList.builder();
			@Cleanup Stream<String> stream = Files.lines(leaseFilePath);
			Iterator<String> it = stream.iterator();
			while (it.hasNext())
			{
				String line = it.next();
				if (line.trim().length() == 0) return;
				String[] split = line.split(Pattern.quote(" "));
				try
				{
					MacAddress mac = new MacAddress(split[1]);
					int vlan = Func.getVlan(split[2]);
					String name = split[3];
					
					NetworkHost host = new NetworkHost(name, mac);
					builder.vlanHosts(vlan, host);
				}
				catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e)
				{
					throw new IOException("Lease file contains unrecognized line \"" + line + "\"", e);
				}
			}
			hosts = builder.build();
		}
		catch (UncheckedIOException e)
		{
			throw e.getCause();
		}
	}
	
	private class WatcherThread extends Thread
	{
		private boolean						run	= true;
		private final WatchService			watchService;
		private final EventNotifierThread	eventNotifier;
		
		private WatcherThread(WatchService watchService, EventNotifierThread eventNotifier)
		{
			this.watchService = watchService;
			this.eventNotifier = eventNotifier;
			
			this.setDaemon(true);
			this.setName("DnsmasqDhcp-WatcherThread");
			this.start();
		}
		
		@Override
		@SneakyThrows(InterruptedException.class)
		public void run()
		{
			try
			{
				while (run)
				{
					WatchKey watchKey;
					try
					{
						watchKey = watchService.take();
					}
					catch (ClosedWatchServiceException e)
					{
						if (run) Log.error(LogCategory.FIREWALL, "WatchService unexpectedly closed", e);
						return;
					}
					catch (InterruptedException e)
					{
						Log.error(LogCategory.FIREWALL, "WatcherThread interrupted", e);
						return;
					}
					List<WatchEvent<?>> events = watchKey.pollEvents();
					boolean leaseFileChanged = false;
					for (WatchEvent<?> event : events)
					{
						try
						{
							if (Files.isSameFile(leaseFolderPath.resolve((Path) event.context()), leaseFilePath))
							{
								leaseFileChanged = true;
								break;
							}
						}
						catch (IOException e)
						{
							Log.warn(LogCategory.FIREWALL,
									"WatcherThread could not check if the lease file got changed", e);
						}
					}
					// Once a change is detected, the file might still be in the process of being modified
					// This loop waits and retries until the file has been read successfully
					while (leaseFileChanged && Files.exists(leaseFilePath))
					{
						Thread.sleep(Constants.LEASE_FILE_READ_INTERVAL.toMillis());
						try
						{
							readHosts();
							// Successful read (no IOException)? OK, all changes processed
							leaseFileChanged = false;
							eventNotifier.notifyEvent();
						}
						catch (IOException e)
						{
							Log.error(LogCategory.FIREWALL, "Could not refresh host list from lease file. "
									+ "Retrying in " + Constants.LEASE_FILE_READ_INTERVAL.toMillis() + " ms", e);
							Thread.sleep(Constants.LEASE_FILE_READ_INTERVAL.toMillis());
						}
					}
					if (!watchKey.reset())
					{
						if (run) Log.warn(LogCategory.FIREWALL,
								"The WatchKey is no longer valid. Was the file moved or deleted?");
						return;
					}
				}
			}
			finally
			{
				eventNotifier.halt();
			}
		}
		
		public void halt()
		{
			run = false;
			try
			{
				watchService.close();
			}
			catch (IOException e)
			{
				Log.warn(LogCategory.FIREWALL, "Exception occurred while closing watcher service", e);
			}
		}
	}
	
	private class EventNotifierThread extends Thread
	{
		private volatile NetworkHostListener	listener			= null;
		
		private volatile boolean				notifyEvent			= false;
		private volatile boolean				notifyEventOverflow	= false;
		private boolean							run					= true;
		
		private EventNotifierThread()
		{
			this.setDaemon(true);
			this.setName("DnsmasqDhcp-NotifierThread");
			this.start();
		}
		
		@Override
		public void run()
		{
			NetworkHostList lastHosts = null;
			NetworkHostList newHosts = null;
			NetworkHostListener currentListener;
			while (run)
			{
				synchronized (this)
				{
					try
					{
						this.wait();
					}
					catch (InterruptedException e)
					{
						Log.error(LogCategory.FIREWALL, "EventNotifierThread interrupted", e);
					}
					currentListener = this.listener;
					newHosts = hosts;
				}
				if (notifyEvent && currentListener != null && !newHosts.equals(lastHosts))
				{
					try
					{
						currentListener.hostsUpdated(DnsmasqDhcpHostMonitor.this);
					}
					catch (Exception e)
					{
						Log.warn(LogCategory.FIREWALL, "NetworkHostListListener threw an exception", e);
					}
					synchronized (this)
					{
						lastHosts = newHosts;
						notifyEvent = notifyEventOverflow;
						notifyEventOverflow = false;
					}
				}
			}
		}
		
		public synchronized void setListener(NetworkHostListener listener)
		{
			this.listener = listener;
			notifyEvent();
		}
		
		public synchronized void notifyEvent()
		{
			if (notifyEvent)
			{
				notifyEventOverflow = true;
			}
			else
			{
				notifyEvent = true;
			}
			this.notify();
		}
		
		public synchronized void halt()
		{
			run = false;
			this.notify();
		}
	}
}
