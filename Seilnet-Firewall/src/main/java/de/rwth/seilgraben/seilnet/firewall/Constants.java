/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.time.Duration;

/**
 *
 * @author Felix Kirchmann
 */
public class Constants
{
	public static String	STANDARD_CONFIG_LOCATION	= "./firewall-config.properties";
	public static String	DEFAULT_CONFIG_CLASSPATH	= "firewall-default-config.properties";
														
	public static String	RULES_FILENAME				= "rules.xml";
	public static String	RULES_BACKUP_FILENAME		= "rules.backup.xml";
	public static String	RULES_TEMP_FILENAME			= "rules.temp.xml";

	public static Duration LEASE_FILE_READ_INTERVAL		= Duration.ofMillis(250);
}
