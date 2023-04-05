/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.esotericsoftware.minlog.Log;

import de.rwth.seilgraben.seilnet.firewall.shared.FirewallApi;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset.FirewallVlanRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList;
import de.rwth.seilgraben.seilnet.firewall.shared.SharedXStream;
import lombok.Synchronized;

/**
 *
 * @author Felix Kirchmann
 */
public class FirewallController implements FirewallApi
{
	private final File							rules, rulesTemp, rulesBackup;
	
	private Map<Integer, FirewallVlanRuleset>	vlanRulesets;
	
	private final FirewallRulesetListener		listener;
	
	public FirewallController(File rulesFolder, FirewallRulesetListener listener) throws IOException
	{
		this.rules = new File(rulesFolder, Constants.RULES_FILENAME);
		this.rulesBackup = new File(rulesFolder, Constants.RULES_BACKUP_FILENAME);
		this.rulesTemp = new File(rulesFolder, Constants.RULES_TEMP_FILENAME);
		this.listener = listener;
		
		if (rules.isDirectory() || rulesBackup
				.isDirectory()) { throw new IOException("Rules or backup rules file must not be a drectory"); }
		
		if (!rules.exists())
		{
			if (rulesBackup.exists())
			{
				Log.info(LogCategory.FIREWALL, "Main rules file non-existent, using backup.");
				loadRulesFile(rulesBackup);
			}
			else
			{
				Log.info(LogCategory.FIREWALL, "No rules found, using empty initial ruleset.");
				vlanRulesets = new HashMap<>();
			}
		}
		else
		{
			try
			{
				loadRulesFile(rules);
			}
			catch (IOException e)
			{
				Log.warn(LogCategory.FIREWALL, "Failed to load main rules file. Attempting to load backup.", e);
				loadRulesFile(rulesBackup);
			}
		}
		
		for (Entry<Integer, FirewallVlanRuleset> ruleset : vlanRulesets.entrySet())
		{
			try
			{
				listener.onRulesetActivated(ruleset.getValue());
			}
			catch (Exception e)
			{
				Log.error(LogCategory.FIREWALL, "Failed to activate stored Ruleset for VLAN ID " + ruleset.getKey(), e);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void loadRulesFile(File ruleFile) throws IOException
	{
		try
		{
			vlanRulesets = (Map<Integer, FirewallVlanRuleset>) SharedXStream.INSTANCE.fromXML(ruleFile);
		}
		catch (ClassCastException e)
		{
			throw new IOException("Invalid object type stored on disk", e);
		}
	}
	
	private void writeRules() throws IOException
	{
		try (OutputStream out = new FileOutputStream(rulesTemp))
		{
			SharedXStream.INSTANCE.toXML(vlanRulesets, out);
		}
		Files.move(rulesTemp.toPath(), rules.toPath(), StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		Files.copy(rules.toPath(), rulesBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}
	
	public static interface FirewallRulesetListener
	{
		public void onRulesetActivated(FirewallRuleset ruleset) throws IOException;
	}
	
	@Override
	@Synchronized
	public void activate(FirewallRuleset ... rulesets)
	{
		for (FirewallRuleset ruleset : rulesets)
		{
			if (ruleset instanceof FirewallVlanRuleset)
			{
				FirewallVlanRuleset vlanRuleset = (FirewallVlanRuleset) ruleset;
				// Don't do anything if the VLAN rulset is unchanged
				if (vlanRuleset.equals(vlanRulesets.get(vlanRuleset.getVlan())))
				{
					Log.debug(LogCategory.FIREWALL,
							"Ruleset for VLAN " + vlanRuleset.getVlan() + " has not changed, script not executed.");
				}
				else
				{
					vlanRulesets.put(vlanRuleset.getVlan(), vlanRuleset);
					try
					{
						listener.onRulesetActivated(ruleset);
						writeRules();
					}
					catch (IOException e)
					{
						throw new RuntimeException(e);
					}
				}
			}
			else
			{
				throw new IllegalArgumentException("Unsupported ruleset type");
			}
			
		}
	}
	
	@Override
	public NetworkHostList getHosts()
	{
		return FirewallMain.getHostsMonitor().getHosts();
	}
}
