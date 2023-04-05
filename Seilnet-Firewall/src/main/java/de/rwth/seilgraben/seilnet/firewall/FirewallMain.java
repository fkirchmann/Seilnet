/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import com.esotericsoftware.minlog.Log;

import de.rwth.seilgraben.seilnet.firewall.FirewallController.FirewallRulesetListener;
import de.rwth.seilgraben.seilnet.util.Config.InvalidConfigurationException;
import de.rwth.seilgraben.seilnet.util.Func;
import lombok.Getter;

/**
 *
 * @author Felix Kirchmann
 */
public class FirewallMain
{
	@Getter
	private static FirewallConfig			config;
	
	@Getter
	private static DnsmasqDhcpHostMonitor	hostsMonitor;
	
	public static void main(String[] args) throws IOException, InvalidConfigurationException
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
		Log.info("Seilnet firewall daemon starting.");
		config = new FirewallConfig(new File(configLocation),
				Func.getClasspathResourceAsStream(Constants.DEFAULT_CONFIG_CLASSPATH));
		Log.set(config.getLogLevel());
		
		File rulesFolder = new File(config.getRulesetStorageFolder());
		if (!rulesFolder.exists() && !rulesFolder.mkdirs()) { throw new IOException("Could not create rules folder."); }
		
		if (config.getInitScript() != null)
		{
			Log.info("Executing init script...");
			ProcessBuilder pb = new ProcessBuilder();
			pb.command(config.getInitScript());
			pb.redirectInput(Redirect.INHERIT);
			pb.redirectOutput(Redirect.INHERIT);
			pb.redirectError(Redirect.INHERIT);
			Process p = pb.start();
			try
			{
				int code = p.waitFor();
				if (code == 0)
				{
					Log.info("Init script finished with exit code " + code + ".");
				}
				else
				{
					Log.warn("Init script finished with non-zero exit code " + code + ".");
				}
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
		
		FirewallRulesetListener listener = new FirewallRulesetExecListener(config.getVlanRulesetScript());
		
		FirewallController manager = new FirewallController(rulesFolder, listener);
		
		hostsMonitor = new DnsmasqDhcpHostMonitor(new File(config.getDnsmasqLeaseFile()));
		if (config.getApiHostsPushUrl() != null)
		{
			hostsMonitor.setListener(new HttpRestNetworkHostListener(config.getApiHostsPushUrl(), config.getApiKey()));
		}
		
		@SuppressWarnings("unused") HttpApi webserver = new HttpApi(config.getListenAddr(), config.getListenPort(),
				config.getApiKey(), manager);
	}
}
