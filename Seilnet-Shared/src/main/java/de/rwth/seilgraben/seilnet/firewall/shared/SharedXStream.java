/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall.shared;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset.FirewallVlanRuleset;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 *
 * @author Felix Kirchmann
 */
public class SharedXStream
{
	public static final XStream INSTANCE = new XStream(new StaxDriver());
	
	static
	{
		INSTANCE.alias("VlanRuleset", FirewallVlanRuleset.class);
		INSTANCE.allowTypeHierarchy(FirewallVlanRuleset.class);
		INSTANCE.allowTypeHierarchy(InetAddress.class);
		INSTANCE.allowTypeHierarchy(Inet4Address.class);
	}
}
