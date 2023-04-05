/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web;

import de.rwth.seilgraben.seilnet.main.db.User;
import lombok.NonNull;
import spark.Request;

import java.util.Locale;

public class Session
{
	/*static Session fromRequest(Request request) { return new Session(request.session()); }
	
	private final spark.Session session;
	
	private Session(spark.Session session) {
		this.session = session;
	}
	
	public void login(@NonNull User user) {
		session.attribute(SessionField.USER_ID, user.getId());
		session.attribute(SessionField.LOCALE, user.getLocale());
	}
	
	public void logout() {
		session.removeAttribute(SessionField.USER_ID);
	}
	
	public void setLocale(@NonNull Locale locale) {
		session.attribute(SessionField.LOCALE, locale);
	}*/
}
