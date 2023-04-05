/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

import de.rwth.seilgraben.seilnet.firewall.FirewallController.FirewallRulesetListener;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset.FirewallVlanRuleset;
import de.rwth.seilgraben.seilnet.util.MacAddress;

/**
 *
 * @author Felix Kirchmann
 */
public class FirewallRulesetExecListener implements FirewallRulesetListener
{
	private final String vlanExec;
	
	public FirewallRulesetExecListener(String vlanExec)
	{
		this.vlanExec = vlanExec;
	}
	
	@Override
	public void onRulesetActivated(FirewallRuleset ruleset) throws IOException
	{
		Process process = null;
		if (ruleset instanceof FirewallVlanRuleset)
		{
			FirewallVlanRuleset vlan = (FirewallVlanRuleset) ruleset;
			List<String> command = new ArrayList<>();
			command.add(vlanExec);
			// VLAN ID
			command.add(Integer.toString(vlan.getVlan()));
			// VLAN: Can it access the admin net?
			command.add(Boolean.toString(vlan.isAccessAdminNet()));
			// VLAN's IPv4 Address
			if (vlan.getInetIPv4() != null)
			{
				command.add(vlan.getInetIPv4().getHostAddress());
			}
			else
			{
				command.add("none");
			}
			// VLAN's DNS Server IPv4 Address
			if (vlan.getInetDnsIPv4() != null)
			{
				command.add(vlan.getInetDnsIPv4().getHostAddress());
			}
			else
			{
				command.add("none");
			}
			// VLAN: Devices allowed internet access (can also be none or any)
			if(vlan.isAllowAllHosts())
			{
				command.add("any");
			}
			else if(vlan.getAllowedHosts().size() == 0)
			{
				command.add("none");
			}
			else
			{
				StringBuilder sb = new StringBuilder(vlan.getAllowedHosts().size() * 18);
				for (MacAddress mac : vlan.getAllowedHosts())
				{
					if (sb.length() > 0)
					{
						sb.append(',');
					}
					sb.append(mac.toString());
				}
				command.add(sb.toString());
			}
			process = startProcess(command);
		}
		else
		{
			throw new IllegalArgumentException("Unsupported ruleset type");
		}
		
		try
		{
			int result = process.waitFor();
			if (result != 0) { throw new IOException("Firewall script returned nonzero status code " + result); }
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private Process startProcess(List<String> commands) throws IOException
	{
		return new ProcessBuilder(commands).redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT).start();
	}
}
