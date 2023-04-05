/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import io.pebbletemplates.pebble.template.PebbleTemplate;
import de.rwth.seilgraben.seilnet.main.TokenManager;
import de.rwth.seilgraben.seilnet.main.config.*;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import org.eclipse.jetty.http.HttpStatus;
import spark.Route;
import spark.Spark;

import java.util.*;

public class ExternalModuleRedirectPage extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("post_redirect");
		Spark.get(Constants.PATH_PREFIX + "/external_module/:identifier", route);
		Spark.post(Constants.PATH_PREFIX + "/external_module/:identifier", route);
	}
	
	public static String getLink(ExternalModule module)
	{
		return getLink(module.getIdentifier());
	}

	private static String getLink(String moduleIdentifier)
	{
		return Constants.PATH_PREFIX + "/external_module/" + moduleIdentifier;
	}

	private final Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();

		String identifier = request.params("identifier");

		if(request.requestMethod().equals("POST")) {
			ExternalModule module = null;
			if (identifier == null || (module = ExternalModule.fromIdentifier(identifier)) == null) {
				Spark.halt(HttpStatus.NOT_FOUND_404, "Module not found");
			}
			String token = TokenManager.getInstance().newToken(module, getUser(request));

			args.put("url", module.getExternalUrl(token));
		}
		else
		{
			args.put("url", getLink(identifier));
		}
		return runTemplate(template, args, request);
	};
}
