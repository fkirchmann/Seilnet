/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jetty.http.HttpStatus;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.MailSender;
import de.rwth.seilgraben.seilnet.main.db.Database.GroupNameInUseException;
import de.rwth.seilgraben.seilnet.main.db.Group;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class ViewGroup extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/view_group");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/group/:id", route);
		Spark.post(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/group/:id", route);
	}
	
	public static String getLink(Group group)
	{
		return Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/group/" + group.getId();
	}
	
	Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		Messages msgs = new Messages();
		
		/**
		 * Retrieve the group to view
		 */
		Group group = null;
		try
		{
			group = getDb().getGroupByID(Integer.parseInt(request.params("id")));
		}
		catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		if (group == null)
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		
		/**
		 * Process any modifications to the group, these are made via POST Requests (HTML Forms or
		 * x-editable)
		 */
		if (request.requestMethod().equals("POST"))
		{
			if (request.queryParams("doForm") != null)
			{
				processFormData(request, group, msgs, args);
			}
			else
			{
				return processEditable(request, response, group);
			}
		}
		
		if (request.queryParams("showForm") != null)
		{
			args.put("showForm", request.queryParams("showForm"));
		}
		
		/**
		 * Display the group information to the user (after modifications have been processed)
		 */
		args.put("name", group.getName());
		args.put("email", group.getEmail());
		args.put("members", group.listMembers());
		args.put("permissions",
				group.getPermissions().stream().map(permission -> permission.id).collect(Collectors.toList()));
		args.put("availablePermissions", Permission.valuesInDisplayOrder());
		args.put("showMailingList", group.isShowMailingList() ? "yes" : "no");
		args.put("showMailingListButtonClass", group.isShowMailingList() ? "success" : "danger");
		
		msgs.addToTemplateArgs(args);
		template = getTemplate("admin/view_group");
		return runTemplate(template, args, request);
	};
	
	private void processFormData(Request request, Group group, Messages msgs, Map<String, Object> args)
	{
		String form = request.queryParams("doForm");
		if (form == null || !request.requestMethod().equals("POST")) { return; }
		
		switch (form)
		{
			case "removeMember":
			{
				User user = getDb().getUserByID(Integer.parseInt(request.queryParams("id")));
				if (!user.removeFromGroup(group))
				{
					msgs.addError("strings", "userNotInGroup");
				}
				break;
			}
			case "addMember":
			{
				if (request.queryParams("addMemberId") == null)
				{
					msgs.addError("strings", "noUserChosen");
					break;
				}
				User user = getDb().getUserByID(Integer.parseInt(request.queryParams("addMemberId")));
				user.addToGroup(group);
				break;
			}
			case "toggleShowMailingList":
			{
				group.setShowMailingList(!group.isShowMailingList());
				break;
			}
		}
	}
	
	private Object processEditable(Request request, Response response, Group group)
	{
		String fieldName = request.queryParams("name");
		String fieldValue = request.queryParams("value");
		
		// Assume error status to return error messages in one line
		// At the end, if the request is okay, we change it back to 200
		response.status(HttpStatus.BAD_REQUEST_400);
		
		switch (fieldName)
		{
			case "name":
			{
				if (fieldValue == null || fieldValue.trim().length() == 0) { return i18n("strings", "emptyGroupName",
						getLocale(request)); }
				try
				{
					group.setName(fieldValue);
					response.status(HttpStatus.OK_200);
				}
				catch (GroupNameInUseException e)
				{
					return i18n("strings", "groupNameInUse", getLocale(request));
				}
				break;
			}
			case "permissions":
			{
				Set<Permission> permissions = new HashSet<>();
				for (String permissionId : request.queryParamsValues("value[]"))
				{
					Permission p = Permission.forId(Integer.parseInt(permissionId));
					if (p == null) { return ""; }
					permissions.add(p);
				}
				group.setPermissions(permissions);
				response.status(HttpStatus.OK_200);
				break;
			}
			case "email":
			{
				if (fieldValue == null || fieldValue.trim().length() == 0)
				{
					group.setEmail(null);
					response.status(HttpStatus.OK_200);
				}
				else
				{
					if (!MailSender
							.isEmailValid(fieldValue)) { return i18n("strings", "invalidEmail", getLocale(request)); }
					group.setEmail(fieldValue);
					response.status(HttpStatus.OK_200);
				}
				break;
			}
		}
		return "";
	};
}
