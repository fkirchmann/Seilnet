/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.util.Func;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class ActiveUsers extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/active_users");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/active_users", route);
	}
	
	private final Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		List<Map<String, Object>> rooms = new ArrayList<>();
		
		for (Room room : Func.asSortedList(getDb().listRooms()))
		{
			User user = room.getCurrentUser();
			Map<String, Object> roomInfo = new HashMap<>();
			roomInfo.put("number", room.getRoomNumber());
			roomInfo.put("vlan", room.getVlan());
			roomInfo.put("occupied", user != null);
			if (user != null)
			{
				roomInfo.put("userFullName", user.getFullName());
				roomInfo.put("userLink", ViewUser.getLink(user));
				if(!user.equals(room.getMainTenant()))
				{
					User mainTenant = room.getMainTenant();
					roomInfo.put("mainTenantFullName", mainTenant.getFullName());
					roomInfo.put("mainTenantLink", ViewUser.getLink(mainTenant));
				}
			}
			rooms.add(roomInfo);
		}
		args.put("rooms", rooms);
		return runTemplate(template, args, request);
	};
}
