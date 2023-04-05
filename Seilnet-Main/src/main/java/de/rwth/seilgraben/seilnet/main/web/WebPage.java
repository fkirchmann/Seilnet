/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.db.Database;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.DatabaseExt;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.main.web.pages.*;
import de.rwth.seilgraben.seilnet.main.web.pages.admin.*;
import de.rwth.seilgraben.seilnet.main.web.pages.api.*;
import de.rwth.seilgraben.seilnet.main.web.pages.mail.SendMail;
import de.rwth.seilgraben.seilnet.main.web.pages.netsettings.DeviceList;
import de.rwth.seilgraben.seilnet.main.web.pages.netsettings.DeviceRegistration;
import de.rwth.seilgraben.seilnet.main.web.pages.netsettings.NetworkSettings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import spark.Request;
import spark.Response;
import spark.Session;

/**
 *
 * @author Felix Kirchmann
 */
public abstract class WebPage
{
	// @formatter:off
	// ---------------------------------------------------------------------------------------------
	/**
	 * An instance of every webpage to be displayed by the webserver must be included in this array.
	 */
	private static final WebPage[] webPages = new WebPage[]
	{
		// Default
		new LoginPage(), new ResetRequestPage(), new ResetPage(), new WelcomePage(), new UserSettingsPage(),
		new ExternalModuleRedirectPage(),
		
		// Admin module
		new Dashboard(), new CreateUser(), new ViewUser(), new PrintUserInfo(), new ActiveUsers(), new Groups(),
		new ViewGroup(), new UserSearch(), new Changelog(),
		
		// Mail module
		new SendMail(),

		// Network Settings module
		new NetworkSettings(), new DeviceList(), new DeviceRegistration(),
		
		// APIs
		new RadiusRestApi(), new HostsApi(), new ExternalAuthApi()
	};
	// ---------------------------------------------------------------------------------------------
	
	// @formatter:on
	
	static void initializeAllPages(DatabaseExt db, PebbleEngine engine)
	{
		for (WebPage page : webPages)
		{
			page.db = db;
			page.engine = engine;
			page.initialize();
		}
	}
	
	protected abstract void initialize();
	
	@Getter(AccessLevel.PROTECTED)
	private DatabaseExt		db;
	
	@Getter(AccessLevel.PROTECTED)
	private PebbleEngine	engine;
	
	protected PebbleTemplate getTemplate(String name)
	{
		try
		{
			return getEngine().getTemplate(name);
		}
		catch (PebbleException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	protected String runTemplate(PebbleTemplate template, Map<String, Object> arguments, Request request, Locale locale)
	{
		StringWriter writer = new StringWriter();
		if (arguments == null)
		{
			arguments = new HashMap<String, Object>();
		}
		
		arguments.putAll(Constants.WEB_CONSTANTS);
		
		User user = getUser(request);
		arguments.put("menu_username", user == null ? "Unknown" : user.getFirstName());
		try
		{
			arguments.put("lang", locale.getLanguage());
			arguments.put("locale", locale.toLanguageTag());
			arguments.put("locale_underscore", locale.toLanguageTag().replace('-', '_'));
			template.evaluate(writer, arguments, locale);
		}
		catch (PebbleException | IOException e)
		{
			throw new RuntimeException(e);
		}
		
		return writer.toString();
	}
	
	protected String runTemplate(PebbleTemplate template, Map<String, Object> arguments, Request request)
	{
		return runTemplate(template, arguments, request, getLocale(request));
	}
	
	/**
	 * Redirects the request and returns false if the user is not logged in or does not have <b>all</b> of the given
	 * permissions.
	 */
	protected boolean authorizeAllPermissions(Request request, Response response, Permission ... requiredPermissions)
	{
		return authorize(request, response, true, requiredPermissions);
	}
	
	/**
	 * Redirects the request and returns false if the user is not logged in or does not have <b>any</b> of the given
	 * permissions.
	 */
	protected boolean authorizeAnyPermission(Request request, Response response, Permission ... anyPermissions)
	{
		return authorize(request, response, false, anyPermissions);
	}
	
	@SneakyThrows
	private boolean authorize(Request request, Response response, boolean allPermissions, Permission ... permissions)
	{
		User user = getUser(request);
		if (user == null || user.canLogin() != AuthResult.OK)
		{
			if (request.pathInfo().length() > (Constants.PATH_PREFIX.length() + 1))
			{
				response.redirect(Constants.PATH_PREFIX + "/login?error=loginRequired&redirect="
						+ URLEncoder.encode(request.pathInfo(), "UTF-8"));
			}
			else
			{
				response.redirect(Constants.PATH_PREFIX + "/login");
			}
			return false;
		}
		
		for (int i = 0; i < permissions.length; i++)
		{
			if (!user.hasPermission(permissions[i]))
			{
				if (allPermissions || i == permissions.length - 1)
				{
					redirectUnauthorized(response);
					return false;
				}
			}
			else if (!allPermissions) { return true; }
		}
		return true;
	}

	protected void redirectUnauthorized(Response response) {
		response.redirect(Constants.PATH_PREFIX + "/?error=unauthorized");
	}

	protected String getIp(Request request) {
		if(request.ip().equals(SeilnetMain.getConfig().getWebXRealIPTrusted())
			&& request.headers("X-Real-IP") != null) {
			return request.headers("X-Real-IP");
		}
		return request.ip();
	}
	
	protected User getUser(Request request)
	{
		if (request.session(false) == null) { return null; }
		if (request.session().attribute(SessionField.USER_ID) == null) { return null; }
		return db.getUserByID(request.session().attribute(SessionField.USER_ID));
	}
	
	protected Locale getLocale(Request request)
	{
		Session session;
		if ((session = request.session(false)) != null && session.attribute(SessionField.LOCALE) != null)
		{
			return (Locale) session.attribute(SessionField.LOCALE);
		}
		else
		{
			return Constants.DEFAULT_LOCALE;
		}
	}
	
	protected String i18n(String file, String messageName, Locale locale)
	{
		return (String) ResourceBundle.getBundle(file, locale).getObject(messageName);
	}
	
	public static class Messages
	{
		public static final String	TEMPLATE_PARAM	= "messages";
		
		private final List<Message>	messages		= new ArrayList<>();
		
		public Message add(MessageType type, String i18nFile, String name)
		{
			Message message = new Message(type, i18nFile, name);
			messages.add(message);
			return message;
		}
		
		public Message addError(String i18nFile, String name)
		{
			return add(MessageType.ERROR, i18nFile, name);
		}
		
		public Message addWarning(String i18nFile, String name)
		{
			return add(MessageType.WARNING, i18nFile, name);
		}
		
		public Message addInfo(String i18nFile, String name)
		{
			return add(MessageType.INFO, i18nFile, name);
		}
		
		public Message addOk(String i18nFile, String name)
		{
			return add(MessageType.OK, i18nFile, name);
		}
		
		public void addToTemplateArgs(Map<String, Object> args)
		{
			List<Map<String, Object>> messageList = new LinkedList<>();
			
			for (Message message : messages)
			{
				Map<String, Object> messageMap = new HashMap<>();
				
				messageMap.put("cssClass", message.type.cssClass);
				messageMap.put("i18nFile", message.i18nFile);
				messageMap.put("name", message.name);
				if (!message.params.isEmpty())
				{
					messageMap.put("params", new ArrayList<String>(message.params));
				}
				
				messageList.add(messageMap);
			}
			
			args.put(Messages.TEMPLATE_PARAM, messageList);
		}
		
		public class Message
		{
			private final MessageType	type;
			private final String		i18nFile, name;
			private final List<String>	params	= new ArrayList<>();
			
			private Message(@NonNull MessageType type, String i18nFile, @NonNull String name)
			{
				this.type = type;
				this.i18nFile = i18nFile;
				this.name = name;
			}
			
			public Message addParam(@NonNull String value)
			{
				params.add(value);
				return this;
			}
		}
		
		public enum MessageType
		{
			ERROR("alert-danger"), WARNING("alert-warning"), INFO("alert-info"), OK("alert-success");
			
			public final String cssClass;
			
			private MessageType(String cssClass)
			{
				this.cssClass = cssClass;
			}
		}
	}
}
