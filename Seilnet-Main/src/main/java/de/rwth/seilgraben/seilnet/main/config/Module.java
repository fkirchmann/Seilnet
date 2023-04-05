/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.config;

import de.rwth.seilgraben.seilnet.main.db.User;

public interface Module
{
	String getIdentifier();
	
	default String getI18nFile() { return "strings"; }
	String getI18nNameTag();
	String getI18nDescriptionTag();
	
	boolean isUsagePermitted(User user);
}
