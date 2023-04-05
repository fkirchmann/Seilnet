/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.config;

import com.google.common.net.UrlEscapers;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.pages.ExternalModuleRedirectPage;
import de.rwth.seilgraben.seilnet.main.web.pages.api.ExternalAuthApi;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

/**
 * External modules. As opposed to internal modules, these are web applications that are not implemented as a part of
 * Seilnet-Web, but still integrated with it.
 *
 * External Modules interface with Seilnet-Web in the following two ways:
 * 1. A link to the external module is displayed on the Welcome page to all users that are authorized to use it.
 * 2. An external module can verify an identity token - see
 * {@link ExternalAuthApi} for details.
 *
 * TODO: Move this to a configuration file
 *
 * @author Felix Kirchmann
 */
public enum ExternalModule implements Module
{
	TREASURY("treasury", "/kasse/login?token=%s", "redacted", user -> user.hasPermission(Permission.TREASURY)),
	KEYS("keys", "/schluessel/login?token=%s", "redacted", user -> user.hasPermission(Permission.KEY_MANAGEMENT)),
	CALENDAR_BAR("calendar_bar", "/calendar/bar/admin/login?token=%s", "redacted", user -> user.hasPermission(Permission.CALENDAR_BAR)),
	CALENDAR_MEDIA("calendar_media", "/calendar/media/admin/login?token=%s", "redacted", user -> user.hasPermission(Permission.CALENDAR_MEDIA)),
	CALENDAR_GARDEN("calendar_garden", "/calendar/garden/admin/login?token=%s", "redacted", user -> user.hasPermission(Permission.CALENDAR_GARDEN));
	
	/**
	 * Defines an external module. It will be visible in the user's dashboard (if he has access to it).
	 *
	 * @param url A user wanting to access the app will be redirected to this URL, using a POST request. The first
	 *               occurrence of %s in the URL will be replaced by the authentication token.
	 * @param secret A random shared secret known only to SeilnetWeb and the external application.
	 * @param requiredPermissions Only users having these permissions will be granted authorization to use the external
	 *                            application.
	 */
	private ExternalModule(String moduleName, String url, String secret, Set<Permission> requiredPermissions)
	{
		this(moduleName, url, secret, user -> requiredPermissions.stream().allMatch(user::hasPermission));
	}
	
	private final String moduleName, url, secret;
	private final Predicate<User> entryValidator;
	
	private ExternalModule(String moduleName, String url, String secret, Predicate<User> entryValidator)
	{
		this.moduleName = moduleName;
		this.url = url;
		this.secret = secret;
		this.entryValidator = entryValidator;
	}
	
	public String getIdentifier() { return this.moduleName; }
	
	public String getSecret() { return this.secret; }
	
	public static ExternalModule fromIdentifier(@NonNull String identifier)
	{
		for(ExternalModule module : ExternalModule.values()) {
			if(module.getIdentifier().equals(identifier)) { return module; }
		}
		return null;
	}
	
	public String getI18nFile() { return "strings"; }
	
	public String getI18nNameTag() { return "module_ext_" + this.getIdentifier() + "_name"; }
	
	public String getI18nDescriptionTag() { return "module_ext_" + this.getIdentifier() + "_desc"; }
	
	public boolean isUsagePermitted(@NonNull User user) { return entryValidator.test(user); }
	
	public String getAuthRedirectUrl() { return ExternalModuleRedirectPage.getLink(this); }
	
	public String getExternalUrl(String token) {
		return String.format(this.url, UrlEscapers.urlPathSegmentEscaper().escape(token));
	}
}
