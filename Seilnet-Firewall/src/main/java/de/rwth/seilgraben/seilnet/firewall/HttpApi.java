/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import org.eclipse.jetty.http.HttpStatus;

import com.esotericsoftware.minlog.Log;
import com.google.gson.Gson;

import de.rwth.seilgraben.seilnet.firewall.shared.FirewallApi;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.SharedXStream;
import de.rwth.seilgraben.seilnet.util.Func;
import lombok.NonNull;
import lombok.Synchronized;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class HttpApi
{
	private final static Gson	GSON	= new Gson();
	private boolean				started	= true;
	private FirewallApi			api		= null;
	
	public HttpApi(@NonNull String ip, int port, @NonNull String apiKey, @NonNull FirewallApi api)
	{
		this.api = api;
		Spark.ipAddress(ip);
		Spark.port(port);
		Spark.before((request, response) -> {
			if (!apiKey.equals(request.headers("Key")))
			{
				Spark.halt(HttpStatus.FORBIDDEN_403);
			}
		});
		Spark.get("/api/firewall/test", (request, response) -> {
			return "OK";
		});
		Spark.get("/api/firewall/hosts", (request, response) -> {
			return GSON.toJson(api.getHosts());
		});
		Spark.post("/api/firewall/rules", rules);
		started = true;
	}
	
	@Synchronized
	public void stop()
	{
		if (!started) { throw new IllegalStateException("already stopped"); }
		Spark.stop();
	}
	
	Route rules = (request, response) -> {
		try
		{
			FirewallRuleset[] rulesets = (FirewallRuleset[]) SharedXStream.INSTANCE.fromXML(request.body());
			Log.debug("Got " + rulesets.length + " rules");
			api.activate(rulesets);
		}
		catch (RuntimeException e)
		{
			Log.warn(LogCategory.FIREWALL, "Could not activate rules", e);
			response.status(HttpStatus.INTERNAL_SERVER_ERROR_500);
			return Func.object2string(e);
		}
		response.status(HttpStatus.OK_200);
		return "OK";
	};
}
