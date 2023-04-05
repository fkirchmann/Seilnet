/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class UserSearch extends WebPage
{
	private static Gson GSON = new Gson();
	
	@Override
	protected void initialize()
	{
		Spark.get(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/user_search", route);
	}
	
	Route route = (request, response) -> {
		if (!authorizeAnyPermission(request, response, Permission.ADMIN, Permission.MAIL)) { return ""; }
		
		String query = request.queryParams("searchQuery");
		List<Map<String, String>> results = new ArrayList<>();
		if (query != null)
		{
			for (Entry<Integer, String> entry : getDb().searchUsers(query).entrySet())
			{
								Map<String, String> resultMap = new HashMap<>();
				resultMap.put("id", Integer.toString(entry.getKey()));
				resultMap.put("text", entry.getValue());
				results.add(resultMap);
			}
		}
		return GSON.toJson(results);
	};
}
