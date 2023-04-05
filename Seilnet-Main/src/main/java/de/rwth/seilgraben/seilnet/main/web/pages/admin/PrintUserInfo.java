/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.http.HttpStatus;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.User.RoomAssignment;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Route;
import spark.Spark;

public class PrintUserInfo extends WebPage
{
	private PebbleTemplate templateDevices, templateWifi;
	
	@Override
	protected void initialize()
	{
		templateDevices = getTemplate("admin/print_user_devices");
		templateWifi = getTemplate("admin/print_user_wifi");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/:id/print/:print", route);
	}
	
	public static String getLinkDevices(User user)
	{
		return Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/" + user.getId() + "/print/devices";
	}
	
	public static String getLinkWifi(User user)
	{
		return Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/" + user.getId() + "/print/wifi";
	}
	
	Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		/**
		 * Get the user specified in the request.
		 */
		int userId = -1;
		try
		{
			userId = Integer.parseInt(request.params("id"));
		}
		catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		User user = getDb().getUserByID(userId);
		if (user == null)
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		
		/**
		 * Select the correct template to print
		 */
		PebbleTemplate template = null;
		if ("wifi".equals(request.params("print")))
		{
			template = templateWifi;
		}
		else if ("devices".equals(request.params("print")))
		{
			template = templateDevices;
		}
		else
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		
		/**
		 * Add user data to the template
		 */
		Map<String, Object> args = new HashMap<>();
		
		args.put("user", user);
		RoomAssignment roomAssignment = user.getRoomAssignment();
		if (roomAssignment != null)
		{
			args.put("room", roomAssignment.getRoom().getRoomNumber());
		}
		Instant now = Instant.now();
		args.put("time", Constants.TIME_FORMATTER.format(now));
		args.put("timeAndDate", Constants.DATE_TIME_FORMATTER.format(now));
		args.put("date", Constants.DATE_FORMATTER.format(now));
		
		return runTemplate(template, args, request, user.getLocale());
	};
}
