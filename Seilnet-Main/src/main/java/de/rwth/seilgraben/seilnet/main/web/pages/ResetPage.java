/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.http.HttpStatus;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import lombok.SneakyThrows;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class ResetPage extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("reset");
		
		Spark.get(Constants.PATH_PREFIX + "/reset", route);
		Spark.post(Constants.PATH_PREFIX + "/reset", route);
	}
	
	Route route = (request, response) -> {
		Map<String, Object> args = new HashMap<>();
		Messages messages = new Messages();
		
		args.put("email", request.queryParams("email"));
		args.put("token", request.queryParams("token"));
		
		User user = getDb().getUserByEmail(request.queryParams("email"));
		if (user == null || !user.getWebPasswordResetToken().equals(request.queryParams("token")))
		{
			response.redirect(Constants.PATH_PREFIX + "/login?error=resetInvalidLink",
					HttpStatus.TEMPORARY_REDIRECT_307);
			return "";
		}
		if (request.requestMethod().equals("POST"))
		{
			reset(request, response, messages, args, user);
		}
		
		messages.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
	
	private void reset(Request request, Response response, Messages messages, Map<String, Object> args, User user)
	{
		String newPassword = request.queryParams("newPassword");
		String newPasswordRepeat = request.queryParams("newPasswordRepeat");
		if (newPassword == null || newPassword.length() == 0 || newPasswordRepeat == null
				|| newPasswordRepeat.length() == 0)
		{
			messages.addError("strings", "missingField");
			return;
		}
		if (!newPassword.equals(newPasswordRepeat))
		{
			messages.addError("strings", "passwordChangeMismatch");
			return;
		}
		user.setWebPassword(newPassword);
		user.deactivateWebPasswordResetToken();
		response.redirect(Constants.PATH_PREFIX + "/login?message=resetSuccess", HttpStatus.TEMPORARY_REDIRECT_307);
	}
	
	@SneakyThrows
	public static String getExternalUrl(User user)
	{
		return SeilnetMain.getConfig().getWebExtUrl() + Constants.PATH_PREFIX + "/reset?email="
				+ URLEncoder.encode(user.getEmail(), "UTF-8") + "&token=" + user.getWebPasswordResetToken();
	}
}
