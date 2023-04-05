/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import com.esotericsoftware.minlog.Log;
import com.google.gson.Gson;

/**
 *
 * @author Felix Kirchmann
 */
public class HttpRestNetworkHostListener implements NetworkHostListener
{
	private final static Gson	GSON	= new Gson();
	private final String		url, key;
	
	public HttpRestNetworkHostListener(String url, String key)
	{
		this.url = url;
		this.key = key;
	}
	
	@Override
	public void hostsUpdated(DnsmasqDhcpHostMonitor source)
	{
		try
		{
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Key", key);
			connection.setDoOutput(true);
			connection.setDoInput(true);
			byte[] payload = GSON.toJson(source.getHosts()).getBytes("UTF-8");
			connection.setFixedLengthStreamingMode(payload.length);
			connection.getOutputStream().write(payload);
			connection.getOutputStream().flush();
			
			int responseCode = connection.getResponseCode();
			if (responseCode == 200)
			{
				return;
			}
			else if (responseCode == 401) // Unauthorized
			{
				throw new IOException("Incorrect API Key");
			}
			else
			{
				throw new IOException("Host Update URL returned unknown status code " + responseCode);
			}
		}
		catch (IOException e)
		{
			Log.warn(
					"Could not transmit hosts list update. "
							+ "This warning can safely be ignored if the Seilnet Main daemon has not been started yet.",
					e);
		}
	}
}
