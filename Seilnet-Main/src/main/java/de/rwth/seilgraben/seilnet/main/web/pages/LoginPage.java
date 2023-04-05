/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import java.util.HashMap;
import java.util.Map;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.SimpleRateLimiter;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthType;
import de.rwth.seilgraben.seilnet.main.web.SessionField;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class LoginPage extends WebPage
{
	private PebbleTemplate template;

	private SimpleRateLimiter<String> ipThrottle
			= new SimpleRateLimiter<>(Constants.LOGIN_LIMIT, Constants.LOGIN_LIMIT_TIMEFRAME);
	
	@Override
	protected void initialize()
	{
		template = getTemplate("login");
		
		if (SeilnetMain.getConfig().getWebDebugAutoLogin() != null)
		{
			Spark.get(Constants.PATH_PREFIX + "/login", DEBUG_AUTO_LOGIN);
			Spark.post(Constants.PATH_PREFIX + "/login", DEBUG_AUTO_LOGIN);
		}
		else
		{
			Spark.get(Constants.PATH_PREFIX + "/login", login);
			Spark.post(Constants.PATH_PREFIX + "/login", login);
		}
	}
	
	// Goddammit eclipse
	// @formatter:off
	
	private final Route DEBUG_AUTO_LOGIN = (request, response) -> {
		User user = getDb().getUserByEmail(SeilnetMain.getConfig().getWebDebugAutoLogin());
		request.session().attribute(SessionField.USER_ID, user.getId());
		request.session().attribute(SessionField.LOCALE, user.getLocale());
		getDb().logAuthEvent(user, "DebugLogin", AuthType.WEB, AuthResult.OK);
		response.redirect(request.queryParams("redirect") != null ? request.queryParams("redirect") : Constants.PATH_PREFIX + "/");
		return "";
	};
	
	Route login = (request, response) -> {
		Map<String, Object> args = new HashMap<>();
		Messages messages = new Messages();
		
		String redirect = request.queryParams("redirect");
		if(redirect == null || !redirect.startsWith(Constants.PATH_PREFIX + "/"))
		{
			redirect = Constants.PATH_PREFIX + "/";
		}
		args.put("redirect", redirect);
		
		if ("loginRequired".equals(request.queryParams("error")) || "resetInvalidLink".equals(request.queryParams("error")))
		{
			//args.put("error", "loginRequired");
			//args.put("errorParam", "none");
			messages.addError("strings", request.queryParams("error"));
		}
		if("resetSuccess".equals(request.queryParams("message")))
		{
			messages.addOk("strings", request.queryParams("message"));
		}

		if ("true".equals(request.queryParams("logout")))
		{
			request.session().removeAttribute(SessionField.USER_ID);
			//args.put("message", "logoutSuccessful");
			messages.addOk("strings", "logoutSuccessful");
		}
		else if (request.queryParams("email") != null && request.requestMethod().equalsIgnoreCase("POST"))
		{
			if(!ipThrottle.tryAcquire(getIp(request)))
			{
				messages.addError("strings", "rateLimitedLogin");
			}
			else
			{
				String email = request.queryParams("email");
				//args.put("errorParam", email);
				User user = getDb().getUserByEmail(email);
				AuthResult result = (user == null) ? AuthResult.UNKNOWN_USER : user.canLogin();
				if(result == AuthResult.OK)
				{
					if (!user.verifyWebPassword(request.queryParams("password")))
					{
						result = AuthResult.WRONG_PASSWORD;
					}
					else
					{
						// Login successful!
						request.session().attribute(SessionField.USER_ID, user.getId());
						request.session().attribute(SessionField.LOCALE, user.getLocale());
						response.redirect(redirect);
					}
				}
				getDb().logAuthEvent(user, getIp(request) + " - " + request.userAgent(), AuthType.WEB, result);
				if(result != AuthResult.OK)
				{
					//args.put("error", result.getI18nTag());
					messages.addError("strings", result.getI18nTag()).addParam(email);
				}
			}
		}
		else if (getUser(request) != null && getUser(request).canLogin() == AuthResult.OK)
		{
			response.redirect(Constants.PATH_PREFIX + "/");
		}
		
		messages.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
}
