/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;


import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import spark.Route;
import spark.Spark;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author Felix Kirchmann
 */
public class Dashboard extends WebPage
{
	// Located in /static/img
	private static final List<String> ADMIN_IMGS = Collections.unmodifiableList(Arrays.asList(
			// it's a "gif", as in graphics, not a "jiff", as in giraffe
			// seriously wtf
			"admin1.gif", "admin2.gif","admin3.gif","admin4.gif"
	));

	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/dashboard");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX, route);
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/", route);
	}
	
	Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }

		Map<String, Object> args = new HashMap<>();
		args.put("admin_img", ADMIN_IMGS.get(ThreadLocalRandom.current().nextInt(0, ADMIN_IMGS.size())));
		return runTemplate(template, args, request);
	};
}
