/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.firewall.shared;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode
public class NetworkHostList
{
	private final Map<Integer, Set<NetworkHost>> hosts;
	
	private NetworkHostList(Map<Integer, Set<NetworkHost>> hosts)
	{
		this.hosts = hosts;
	}
	
	public static NetworkHostListBuilder builder()
	{
		return new NetworkHostListBuilder();
	}
	
	public Set<NetworkHost> listVlanHosts(int vlan)
	{
		Set<NetworkHost> vlanHosts = hosts.get(vlan);
		if (vlanHosts == null) vlanHosts = new HashSet<NetworkHost>();
		return Collections.unmodifiableSet(vlanHosts);
	}
	
	public Map<Integer, Set<NetworkHost>> listHosts()
	{
		return hosts;
	}
	
	@Getter
	@EqualsAndHashCode
	@RequiredArgsConstructor
	public static class NetworkHost
	{
		private final String		name;
		private final MacAddress	macAddress;
	}
	
	public static class NetworkHostListBuilder
	{
		private final Map<Integer, Set<NetworkHost>> hosts;
		
		private NetworkHostListBuilder()
		{
			hosts = new HashMap<>();
		}
		
		public NetworkHostListBuilder vlanHosts(int vlan, @NonNull NetworkHost ... vlanHostsAdd)
		{
			Set<NetworkHost> vlanHosts = hosts.getOrDefault(vlan, new HashSet<NetworkHost>());
			for (NetworkHost host : vlanHostsAdd)
			{
				vlanHosts.add(host);
			}
			if (!vlanHosts.isEmpty())
			{
				hosts.put(vlan, new HashSet<>(vlanHosts));
			}
			else
			{
				hosts.remove(vlan);
			}
			return this;
		}
		
		public NetworkHostList build()
		{
			return new NetworkHostList(new HashMap<>(hosts));
		}
	}
}
