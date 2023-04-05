/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.esotericsoftware.minlog.Log;
import com.google.gson.Gson;

import de.rwth.seilgraben.seilnet.firewall.shared.FirewallApi;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset.FirewallVlanRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList;
import de.rwth.seilgraben.seilnet.firewall.shared.SharedXStream;
import de.rwth.seilgraben.seilnet.util.Func;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;

/**
 * Connects to a remote firewall server via HTTP and asynchronously sends firewall rules to it.
 * 
 * When the client is created, a test request to the firewall is made to determine if the firewall
 * is reachable and if the API key is correct. If this test succeeds, a background thread is started
 * which handles the communication with the firewall.
 *
 * @author Felix Kirchmann
 */
public class FirewallClient implements FirewallApi
{
	private static final Gson						GSON			= new Gson();
	
	private final FirewallClientThread				thread			= new FirewallClientThread();
	private final Object							monitor			= new Object();
	private final Map<Integer, FirewallVlanRuleset>	vlanRulesets	= new HashMap<>();
	private final List<FirewallRuleset>				queue			= new ArrayList<>();
	private volatile boolean						paused			= false;
	private final String							baseUrl;
	private final String							key;
	private volatile NetworkHostList				hosts;
	
	public FirewallClient(String host, int port, String key) throws IOException
	{
		baseUrl = "http://" + host + ":" + port + "/api/firewall";
		this.key = key;
		
		updateHosts();
		
		thread.start();
	}
	
	private void updateHosts() throws IOException
	{
		HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/hosts").openConnection();
		connection.setRequestProperty("Key", key);
		int responseCode = connection.getResponseCode();
		if (responseCode == 200)
		{
			try (InputStream response = connection.getInputStream();)
			{
				updateHosts(GSON.fromJson(Func.readInputStream(response), NetworkHostList.class));
				if (hosts == null) { throw new IOException("Firewall returned null hosts list"); }
			}
		}
		else if (responseCode == 401) // Unauthorized
		{
			throw new IOException("Firewall: Incorrect API Key");
		}
		else
		{
			throw new IOException("Firewall returned unknown status code " + responseCode);
		}
	}
	
	public void updateHosts(@NonNull NetworkHostList newHosts)
	{
		this.hosts = newHosts;
	}
	
	@Override
	public NetworkHostList getHosts()
	{
		return hosts;
	}
	
	public void stop()
	{
		thread.stopGracefully();
	}
	
	private class FirewallClientThread extends Thread
	{
		private boolean run = true;
		
		private FirewallClientThread()
		{
			super();
			this.setDaemon(true);
			this.setName("FirewallClientThread");
		}
		
		private void stopGracefully()
		{
			synchronized (monitor)
			{
				run = false;
				monitor.notifyAll();
			}
		}
		
		@Override
		@SneakyThrows
		public void run()
		{
			while (run)
			{
				FirewallRuleset[] rulesets = null;
				// Wait for a new rule to be put into the outgoing queue 
				synchronized (monitor)
				{
					while (paused || queue.isEmpty())
					{
						monitor.wait();
						if (!run) { return; }
					}
					rulesets = queue.toArray(new FirewallRuleset[queue.size()]);
					queue.clear();
					vlanRulesets.clear();
				}
				// Once at least one rule has been enqueued, send it to the firewall
				HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/rules").openConnection();
				connection.setRequestProperty("Key", key);
				connection.setDoOutput(true);
				try (OutputStream os = connection.getOutputStream())
				{
					SharedXStream.INSTANCE.toXML(rulesets, os);
				}
				int responseCode = connection.getResponseCode();
				if (responseCode == 200) // OK
				{
					String responseBody;
					try (InputStream response = connection.getInputStream(); Scanner scanner = new Scanner(response))
					{
						responseBody = scanner.useDelimiter("\\A").next();
					}
					if (!responseBody.equals("OK")) { throw new IOException(
							"Firewall returned unrecognized response \"" + responseBody + "\""); }
				}
				else if (responseCode == 500) // Internal Server Error
				{
					String responseBody;
					try (InputStream response = connection.getErrorStream(); Scanner scanner = new Scanner(response))
					{
						responseBody = scanner.useDelimiter("\\A").next();
					}
					throw new IOException("Internal Firewall error: \"" + responseBody + "\"");
				}
				else
				{
					Log.error("Firewall returned unknown status code " + responseCode);
				}
			}
		}
	}
	
	/**
	 * Queues a set of firewall rules to be sent to the server.
	 */
	@Override
	@Synchronized("monitor")
	public void activate(@NonNull FirewallRuleset ... rulesets)
	{
		for (FirewallRuleset ruleset : rulesets)
		{
			if (ruleset instanceof FirewallVlanRuleset)
			{
				// If there is an older ruleset for the same VLAN in the queue, remove it
				FirewallVlanRuleset fwRuleset = (FirewallVlanRuleset) ruleset;
				FirewallVlanRuleset previous = vlanRulesets.put(fwRuleset.getVlan(), fwRuleset);
				if (previous != null)
				{
					queue.remove(previous);
				}
			}
			queue.add(ruleset);
		}
		monitor.notifyAll();
	}
	
	/**
	 * Starts Batch processing. Once this is called, no further rulesets will be sent to the
	 * firewall. Instead, this client will collect all incoming rulesets in a local queue. Once the
	 * batch is complete, the caller must call {@link #finishBatch()} to cause the queue to be sent
	 * to the firewall. This will also allow any later rulesets to be sent to the firewall.
	 * <br>
	 * <b>Important:</b> To prevent a situation in which {@link #startBatch()} is called but
	 * {@link #finishBatch()} is not (which would cause all rulesets to be cached in the client
	 * forever), use a finally block:
	 * <code>
	 * try {
	 *   startBatch();
	 *   ...
	 * } finally {
	 *   finishBatch();
	 * }
	 * </code>
	 */
	@Synchronized("monitor")
	public void startBatch()
	{
		if (paused == true) { return; }
		paused = true;
	}
	
	/**
	 * @see #startBatch()
	 */
	@Synchronized("monitor")
	public void finishBatch()
	{
		if (!paused) { return; }
		paused = false;
		monitor.notifyAll();
	}
}
