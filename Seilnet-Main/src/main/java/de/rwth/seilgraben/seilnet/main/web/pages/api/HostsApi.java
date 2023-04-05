/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.api;

import org.eclipse.jetty.http.HttpStatus;

import com.google.gson.Gson;

import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.util.Func;
import spark.Route;
import spark.Spark;

public class HostsApi extends WebPage
{
	private static Gson GSON = new Gson();
	
	@Override
	protected void initialize()
	{
		Spark.post(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/hosts", updateHosts);
	}
	
	Route updateHosts = (request, response) -> {
		if (!SeilnetMain.getConfig().getFirewallApiKey().equals(request.headers("Key")))
		{
			Spark.halt(HttpStatus.FORBIDDEN_403);
		}
		
		NetworkHostList hosts = GSON.fromJson(Func.readInputStream(request.raw().getInputStream()),
				NetworkHostList.class);
		
		SeilnetMain.getFirewallClient().updateHosts(hosts);
		
		response.status(200);
		return "OK";
	};
}
