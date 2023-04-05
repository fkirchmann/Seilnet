/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import io.pebbletemplates.pebble.template.PebbleTemplate;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.util.Func;
import spark.Route;
import spark.Spark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Felix Kirchmann
 */
public class Changelog extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/changelog");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/changelog", route);
	}
	
	private final Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		return runTemplate(template, args, request);
	};
}
