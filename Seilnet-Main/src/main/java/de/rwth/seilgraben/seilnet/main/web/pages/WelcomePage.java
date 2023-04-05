/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.ExternalModule;
import de.rwth.seilgraben.seilnet.main.config.InternalModule;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.main.web.WebPage.Messages.MessageType;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class WelcomePage extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("welcome");
		
		if (Constants.PATH_PREFIX.length() > 0)
		{
			Spark.get(Constants.PATH_PREFIX, (request, response) -> {
				response.redirect(Constants.PATH_PREFIX + "/");
				return "";
			});
		}
		Spark.get(Constants.PATH_PREFIX + "/", welcomeRoute);
	}
	
	private final Route welcomeRoute = (request, response) -> {
		if (!authorizeAllPermissions(request, response)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		
		if ("unauthorized".equals(request.queryParams("error")))
		{
			Messages msgs = new Messages();
			msgs.add(MessageType.ERROR, "strings", "unauthorized");
			msgs.addToTemplateArgs(args);
		}
		
		User user = getUser(request);
		args.put("internalModules", Arrays.stream(InternalModule.values())
						.filter(m -> m.isUsagePermitted(user))
						.collect(Collectors.toList()));
		args.put("externalModules", Arrays.stream(ExternalModule.values())
				.filter(m -> m.isUsagePermitted(user))
				.collect(Collectors.toList()));
		
		return runTemplate(template, args, request);
	};
}
