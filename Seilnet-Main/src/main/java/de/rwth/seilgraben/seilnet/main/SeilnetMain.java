/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;

import com.esotericsoftware.minlog.Log;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.db.Database;
import de.rwth.seilgraben.seilnet.main.db.Database.EMailInUseException;
import de.rwth.seilgraben.seilnet.main.db.Database.GroupNameInUseException;
import de.rwth.seilgraben.seilnet.main.db.DatabaseExt;
import de.rwth.seilgraben.seilnet.main.db.Group;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebServer;
import de.rwth.seilgraben.seilnet.util.Config.InvalidConfigurationException;
import de.rwth.seilgraben.seilnet.util.Func;
import lombok.Getter;

/**
 *
 * @author Felix Kirchmann
 */
public class SeilnetMain
{
	@Getter
	private static SeilnetConfig	config;
	
	@Getter
	private static FirewallClient	firewallClient;
	
	@Getter
	private static FirewallManager	firewallManager;
	
	@Getter
	private static MailSender		mailSender;
	
	@Getter
	private static String			classpathPrefix	= "";
	
	public static void main(String[] args) throws SQLException, IOException, InvalidConfigurationException
	{
		String configLocation = Constants.STANDARD_CONFIG_LOCATION;
		if (args.length > 0)
		{
			if (args.length > 1)
			{
				System.err.println("Only up to one optional argument (the configuration file) is supported.");
				System.exit(1);
			}
			else if (!new File(args[0]).isFile())
			{
				System.err.println("Configuration file \"" + args[0] + "\" not found.");
				System.err.println();
				System.err.println("You can specify the location of a configuration file as the first parameter.");
				System.err.println("If you do not, the standard location (\"" + Constants.STANDARD_CONFIG_LOCATION
						+ "\") will be used.");
				System.exit(1);
			}
			else
			{
				configLocation = args[0];
			}
		}
		config = new SeilnetConfig(new File(configLocation),
				Func.getClasspathResourceAsStream(Constants.DEFAULT_CONFIG_CLASSPATH));
		Log.set(config.getLogLevel());
		
		// Detect classpath prefix
		if (SeilnetMain.class.getClassLoader().getResources("resources/" + Constants.DEFAULT_CONFIG_CLASSPATH)
				.hasMoreElements())
		{
			classpathPrefix = "resources/";
		}
		
		mailSender = new MailSender(config.getSmtpHost(), config.getSmtpPort(), config.getSmtpUser(),
				config.getSmtpPassword(), config.getSmtpEncryption(), config.getSmtpSender());
		
		Log.info("Opening database connection...");
		DatabaseExt db = new DatabaseExt(config.getMysqlHost(), config.getMysqlDatabase(), config.getMysqlUser(),
				config.getMysqlPass());
		db.test();
		
		if (config.getWebDebugAutoLogin() != null)
		{
			Log.warn(LogCategory.CFG,
					"WARNING: Automatic login is activated. Anyone can access the web interface without authentication.");
			User user = db.getUserByEmail(config.getWebDebugAutoLogin());
			if (user == null)
			{
				try
				{
					user = db.createUser("Default", "Admin", config.getWebDebugAutoLogin(), Constants.DEFAULT_LOCALE);
				}
				catch (EMailInUseException e)
				{
					e.printStackTrace();
				}
				Log.warn(LogCategory.CFG, "WARNING: Creating autologin user " + user.getEmail());
			}
			if (!user.hasPermission(Permission.ADMIN))
			{
				try
				{
					db.createGroup("Administrators");
				}
				catch (GroupNameInUseException e)
				{
				}
				Group admins = db.getGroupByName("Administrators");
				admins.setPermissions(new HashSet<Permission>(Arrays.asList(Permission.values())));
				user.addToGroup(admins);
			}
		}
		
		Log.info("Connecting to firewall...");
		firewallClient = new FirewallClient(config.getFirewallAddr(), config.getFirewallPort(),
				config.getFirewallApiKey());
		
		Log.info("Creating firewall rules...");
		firewallManager = new FirewallManager(firewallClient, db);
		firewallManager.updateAllRules();
		
		Log.info("Starting webserver...");
		WebServer.INSTANCE.start(db, config.getWebListenAddr(), config.getWebListenPort());
		
	}
}
