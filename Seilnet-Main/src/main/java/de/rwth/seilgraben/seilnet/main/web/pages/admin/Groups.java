/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.db.Database.GroupNameInUseException;
import de.rwth.seilgraben.seilnet.main.db.Group;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Request;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class Groups extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/groups");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/groups", route);
		Spark.post(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/groups", route);
	}
	
	private final Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		Messages msgs = new Messages();
		
		if (request.queryParams("showForm") != null)
		{
			args.put("showForm", request.queryParams("showForm"));
		}
		
		if (request.requestMethod().equals("POST") && request.queryParams("doForm") != null)
		{
			processFormData(request, msgs, args);
		}
		
		synchronized (getDb())
		{
			List<Group> groups = getDb().listGroups();
			List<Map<String, Object>> groupsData = new ArrayList<>(groups.size());
			// Sort Groups by group name
			Collections.sort(groups, new Comparator<Group>()
			{
				@Override
				public int compare(Group g1, Group g2)
				{
					return g1.getName().compareTo(g2.getName());
				}
			});
			for (Group group : groups)
			{
				Map<String, Object> groupData = new HashMap<>();
				groupData.put("link", ViewGroup.getLink(group));
				groupData.put("name", group.getName());
				groupData.put("id", group.getId());
				groupData.put("members",
						group.listMembers().stream().map(member -> member.getFullName()).collect(Collectors.toList()));
				groupsData.add(groupData);
			}
			args.put("groups", groupsData);
		}
		
		msgs.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
	
	private void processFormData(Request request, Messages msgs, Map<String, Object> args)
	{
		String form = request.queryParams("doForm");
		if (form == null || !request.requestMethod().equals("POST")) { return; }
		
		switch (form)
		{
			case "addGroup":
			{
				String groupName = request.queryParams("name");
				if (groupName == null || groupName.trim().isEmpty())
				{
					msgs.addError("strings", "emptyGroupName");
					return;
				}
				try
				{
					getDb().createGroup(groupName);
				}
				catch (GroupNameInUseException e)
				{
					msgs.addError("strings", "groupNameInUse");
				}
				break;
			}
			case "deleteGroup":
			{
				Group group = getDb().getGroupByID(Integer.parseInt(request.queryParams("id")));
				if (group == null)
				{
					msgs.addError("strings", "unknownGroupId");
					break;
				}
				group.delete();
				break;
			}
		}
	}
}
