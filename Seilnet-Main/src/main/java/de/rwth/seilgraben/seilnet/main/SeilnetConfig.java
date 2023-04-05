/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.util.regex.Pattern;

import de.rwth.seilgraben.seilnet.main.MailSender.SmtpSecurity;
import de.rwth.seilgraben.seilnet.util.Config;
import lombok.Getter;

/**
 *
 * @author Felix Kirchmann
 */
@Getter
public class SeilnetConfig extends Config
{
	/**
	 * @see Config#Config(File, InputStream)
	 */
	public SeilnetConfig(File configFile, InputStream defaultConfig) throws IOException, InvalidConfigurationException
	{
		super(configFile, defaultConfig);
	}

	private Inet4Address	adblockDnsServer;
	private int				logLevel, oldIpRetentionDays, webListenPort, smtpPort, firewallPort;
	private String			webListenAddr, webExtUrl,
			webRadiusUser, webRadiusPassword,// webRadiusRoomSSIDRegex,
			smtpHost, smtpSender, smtpUser,	smtpPassword,
			firewallApiKey, firewallAddr,
			mysqlHost, mysqlDatabase,mysqlUser, mysqlPass,
			webDebugAutoLogin, webXRealIPTrusted;
	private Pattern			webRadiusRoomSSIDRegex;
	private File			webDataDirectory;
	private boolean			webDebugShowStacktrace;
	private SmtpSecurity	smtpEncryption;
							
	@Override
	protected void loadOptions() throws InvalidConfigurationException
	{
		logLevel = optionLogLevel("log_level");
		oldIpRetentionDays = optionIntNonNegative("old_ip_retention_days");
		adblockDnsServer = hasOption("adblock_dns_server") ? optionInet4Address("adblock_dns_server") : null;

		webListenAddr = option("web_listen_addr");
		webListenPort = optionPort("web_listen_port");
		webExtUrl = option("web_ext_url");
		webDebugShowStacktrace = optionBool("web_debug_show_stacktrace");
		webDebugAutoLogin = hasOption("web_debug_auto_login") ? option("web_debug_auto_login") : null;
		if (hasOption("web_radius_user"))
		{
			webRadiusUser = option("web_radius_user");
			webRadiusPassword = option("web_radius_password");
			webRadiusRoomSSIDRegex = hasOption("web_radius_room_ssid_regex") ?
					optionRegex("web_radius_room_ssid_regex") : null;
		}
		webDataDirectory = new File(option("web_data_directory"));
		if (!(webDataDirectory.isDirectory() || webDataDirectory.mkdirs())
				|| !webDataDirectory.canWrite()) { throw new InvalidConfigurationException("web_data_directory",
						"must be a writable folder"); }

		webXRealIPTrusted = hasOption("web_x_real_ip_trusted") ? option("web_x_real_ip_trusted") : null;

		smtpHost = option("smtp_host");
		smtpPort = optionPort("smtp_port");
		smtpEncryption = optionEnum("smtp_encryption", SmtpSecurity.class);
		smtpSender = option("smtp_sender");
		smtpUser = option("smtp_user");
		smtpPassword = option("smtp_pass");
		
		mysqlHost = option("mysql_host");
		mysqlDatabase = option("mysql_database");
		mysqlUser = option("mysql_user");
		mysqlPass = hasOption("mysql_pass") ? option("mysql_pass") : null;

		firewallAddr = option("firewall_addr");
		firewallPort = optionPort("firewall_port");
		firewallApiKey = option("firewall_api_key");
	}
}
