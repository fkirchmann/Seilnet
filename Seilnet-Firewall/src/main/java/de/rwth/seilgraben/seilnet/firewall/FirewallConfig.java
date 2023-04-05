/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import de.rwth.seilgraben.seilnet.util.Config;
import lombok.Getter;

/**
 *
 * @author Felix Kirchmann
 */
@Getter
public class FirewallConfig extends Config
{
	/**
	 * @see Config#Config(File, InputStream)
	 */
	public FirewallConfig(File configFile, InputStream defaultConfig) throws IOException, InvalidConfigurationException
	{
		super(configFile, defaultConfig);
	}
	
	private int		logLevel, listenPort;
	private String	initScript, vlanRulesetScript, listenAddr, apiKey, apiHostsPushUrl, dnsmasqLeaseFile,
			rulesetStorageFolder;
	
	@Override
	protected void loadOptions() throws InvalidConfigurationException
	{
		logLevel = optionLogLevel("log_level");
		listenAddr = option("listen_addr");
		listenPort = optionPort("listen_port");
		apiKey = option("api_key");
		if (hasOption("api_hosts_push_url"))
		{
			apiHostsPushUrl = option("api_hosts_push_url");
		}
		dnsmasqLeaseFile = option("dnsmasq_lease_file");
		rulesetStorageFolder = option("ruleset_storage_folder");
		if (hasOption("exec_init"))
		{
			initScript = option("exec_init");
		}
		vlanRulesetScript = option("exec_vlan");
	}
}
