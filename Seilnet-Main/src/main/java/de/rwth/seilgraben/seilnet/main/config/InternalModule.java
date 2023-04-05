/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.config;

import de.rwth.seilgraben.seilnet.main.db.User;

import java.util.function.Predicate;

// ---------------------------------------------------------------------------------------------
/**
 * Modules to display in the dashboard once a user has logged in.
 *
 * IMPORTANT: These modules are essentially nothing more than links in the welcome page shown when a user logs in. As such
 *
 * A module is only shown if the user has the permissions specified by that module. However, note
 * that this only affects the visibility in the dashboard, the module itself must still perform
 * proper access checks.
 *
 * NOTE: if you define a module here, also define the i18n strings <code>module_int_[moduleName]_name</code> and
 * <code>module_int_[moduleName]_desc</code>, e.g. <code>module_int_admin_name</code> and
 * <code>module_int_admin_desc</code>
 */
public enum InternalModule implements Module
{
	ADMIN("admin", Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/", user -> user.hasPermission(Permission.ADMIN)),
	MAIL("mail", Constants.PATH_PREFIX + Constants.MAIL_PATH_PREFIX + "/send", user -> user.hasPermission(Permission.MAIL)),
	NETSETTINGS("netsettings", Constants.PATH_PREFIX + Constants.NETSETTINGS_PATH_PREFIX + "/",
			user -> user.getRoomAssignment() != null);
	
	private final String moduleName, url;
	private final Predicate<User> entryValidator;
	
	private InternalModule(String moduleName, String url, Predicate<User> entryValidator)
	{
		this.moduleName = moduleName;
		this.url = url;
		this.entryValidator = entryValidator;
	}
	
	public String getIdentifier() { return this.moduleName; }
	
	public String getI18nNameTag() { return "module_int_" + this.getIdentifier() + "_name"; }
	
	public String getI18nDescriptionTag() { return "module_int_" + this.getIdentifier() + "_desc"; }
	
	public boolean isUsagePermitted(User user ) { return entryValidator.test(user); }
	
	public String getUrl() { return url; }
}
