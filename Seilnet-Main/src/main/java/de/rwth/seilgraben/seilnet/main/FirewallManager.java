/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset;
import de.rwth.seilgraben.seilnet.firewall.shared.FirewallRuleset.FirewallVlanRuleset;
import de.rwth.seilgraben.seilnet.main.db.Database;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.NonNull;
import lombok.Synchronized;

/**
 * Creates Firewall ruleset from Database Rooms / Users and send
 *
 * @author Felix Kirchmann
 */
public class FirewallManager
{
	private final FirewallClient	client;
	private final Database			db;
	
	public FirewallManager(@NonNull FirewallClient client, @NonNull Database db)
	{
		this.client = client;
		this.db = db;
	}
	
	/**
	 * For debugging
	 */
	@Synchronized("db")
	public void customRule(FirewallRuleset ruleset)
	{
		client.activate(ruleset);
	}
	
	@Synchronized("db")
	public void updateRules(Room room)
	{
		if (room.getVlan() == null) { return; }
		
		User tenant = room.getCurrentUser();
		FirewallVlanRuleset ruleset;
		if (tenant == null || tenant.canLogin() != AuthResult.OK)
		{
			ruleset = new FirewallVlanRuleset(room.getVlan(),  false, null, null, false, new HashSet<>());
		}
		else
		{
			Set<MacAddress> allowedHosts = tenant.getAssignedDevices().stream().map(device -> device.getMacAddress())
					.collect(Collectors.toSet());
			ruleset = new FirewallVlanRuleset(room.getVlan(),
					tenant.hasPermission(Permission.ACCESS_ADMIN_NET),
					tenant.getAssignedNatIPv4(),
					tenant.isAdblock() ? SeilnetMain.getConfig().getAdblockDnsServer() : null,
					tenant.hasPermission(Permission.DEVICE_REGISTRATION_NOT_NECESSARY),
					allowedHosts
			);
		}
		client.activate(ruleset);
	}
	
	@Synchronized("db")
	public void updateAllRules()
	{
		try
		{
			client.startBatch();
			for (Room room : db.listRooms())
			{
				updateRules(room);
			}
		}
		finally
		{
			client.finishBatch();
		}
	}
}
