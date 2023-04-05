/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import java.util.HashMap;
import java.util.Map;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Constants.SupportedLanguage;
import de.rwth.seilgraben.seilnet.main.web.SessionField;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class UserSettingsPage extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("user_settings");
		
		Spark.get(Constants.PATH_PREFIX + "/settings", route);
		Spark.post(Constants.PATH_PREFIX + "/settings", route);
	}
	
	private final Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response)) { return ""; }
		
		String userSpecifiedLang = request.queryParams("lang");
		if (userSpecifiedLang != null)
		{
			SupportedLanguage lang = SupportedLanguage.fromLanguageTag(userSpecifiedLang);
			if (lang != null)
			{
				request.session().attribute(SessionField.LOCALE, lang.locale);
				getUser(request).setLocale(lang.locale);
			}
		}
		
		Map<String, Object> args = new HashMap<>();
		Messages msgs = new Messages();
		String action = request.queryParams("action");
		if (action == null) { return runTemplate(template, null, request); }
		switch (action)
		{
			case "changepw":
			{
				String oldPw = request.queryParams("password");
				String newPw = request.queryParams("newPassword");
				String newPwRepeat = request.queryParams("newPasswordRepeat");
				if (oldPw == null || !getUser(request).verifyWebPassword(oldPw))
				{
					msgs.addError("strings", "passwordChangeWrongPw");
				}
				else if (newPw == null || newPw.length() == 0 || newPwRepeat == null || newPwRepeat.length() == 0)
				{
					msgs.addError("strings", "passwordChangeEmpty");
				}
				else if (!newPw.equals(newPwRepeat))
				{
					msgs.addError("strings", "passwordChangeMismatch");
				}
				else
				{
					getUser(request).setWebPassword(newPw);
					msgs.addOk("strings", "passwordChangeSuccess");
				}
				break;
			}
		}
		msgs.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
}
