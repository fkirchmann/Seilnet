/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall.shared;

import java.net.Inet4Address;
import java.util.Set;

import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @author Felix Kirchmann
 */
public abstract class FirewallRuleset
{
	/**
	 * Configures which hosts (represented by their MAC address) can access the internet from within
	 * a given VLAN. Because the firewall / router performs NAT, <code>inetIPv4</code> specifies the
	 * external IP address that these hosts will use when connecting to the internet.
	 * 
	 * Note that there can only be one active Ruleset per VLAN ID - activating a VLAN Ruleset will
	 * overwrite any previous Rulesets for that VLAN.
	 *
	 * @author Felix Kirchmann
	 */
	@Getter
	@RequiredArgsConstructor
	@EqualsAndHashCode(callSuper = false)
	public static class FirewallVlanRuleset extends FirewallRuleset
	{
		private final int				vlan;
										
		private final boolean			accessAdminNet;

		private final Inet4Address		inetIPv4;

		/**
		 * If the user has an IPv4 address, all DNS requests from allowed hosts will be redirected to this IP address.
		 * If it is set to null, the standard gateway DNS address is used instead.
		 */
		private final Inet4Address		inetDnsIPv4;

		private final boolean			allowAllHosts;

		@NonNull
		private final Set<MacAddress>	allowedHosts;
	}
}
