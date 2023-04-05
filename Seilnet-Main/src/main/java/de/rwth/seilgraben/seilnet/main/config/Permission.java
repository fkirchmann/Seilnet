/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.config;

import java.util.*;

import de.rwth.seilgraben.seilnet.main.I18nEnum;

/**
 * Only up to 64 permission types allowed, since that's the maximum number we can encode in a 64-bit
 * long.
 * 
 * When creating a new permission (e.g. DELETE), remember to define the user-readable name (e.g.
 * permission_DELETE=Loeschen) in each strings_**_**.properties file.
 * 
 * <b>WARNING: Permissions may only be removed when no user or group in the database has them!</b>
 *
 * @author Felix Kirchmann
 */
public enum Permission implements I18nEnum
{
	// ------------------------
	// READ the comment above before adding or removing permissions
	/**
	 * ADMIN: Grants access to the administration interface (web.pages.admin.*)
	 */
	ADMIN(0),
	/**
	 * MAIL: Allows the user to send (newsletter) E-Mails
	 */
	MAIL(1),
	/**
	 * LOGIN_WITHOUT_LEASE: The user can login even if he does not have an active lease
	 * agreement.
	 */
	LOGIN_WITHOUT_LEASE(2),
	/**
	 * TREASURY: The user has access to financial data.
	 */
	TREASURY(3),
	/**
	 * ACCESS_ADMIN_NET: Can access the admin network
	 */
	ACCESS_ADMIN_NET(4),
	/**
	 * DEVICE_REGISTRATION_NOT_NECESSARY: User doesn't need to register devices for internet access
	 */
	DEVICE_REGISTRATION_NOT_NECESSARY(5),
	/**
	 * KEY_MANAGEMENT: Manage Physical Keys
	 */
	KEY_MANAGEMENT(6),
	/**
	 * UNLIMITED_DATA_RETENTION: Old information about this user (e.g. assigned IPs) is not deleted after the usual timeout
	 */
	UNLIMITED_DATA_RETENTION(7),
	/**
	 * Permission to modify various calendards
	 */
	CALENDAR_BAR(8),
	CALENDAR_MEDIA(9),
	CALENDAR_GARDEN(10),
	/**
	 * The user may not register devices by themselves on their network settings page.
	 */
	NO_SELF_SERVICE_DEVICE_REGISTRATION(11);

	// READ the comment above before adding or removing permissions
	// ------------------------

	private static List<Permission> displayOrder = Collections.unmodifiableList(Arrays.asList(
		ADMIN, ACCESS_ADMIN_NET, MAIL, UNLIMITED_DATA_RETENTION, LOGIN_WITHOUT_LEASE, DEVICE_REGISTRATION_NOT_NECESSARY,
			TREASURY, KEY_MANAGEMENT, CALENDAR_BAR, CALENDAR_MEDIA, CALENDAR_GARDEN));
	static
	{
		// Adds elements missing from the displayOrder to the end of it
		List<Permission> newDisplayOrder = new ArrayList<>(displayOrder);
		for(Permission p : Permission.values())
		{
			if(!newDisplayOrder.contains(p)) { newDisplayOrder.add(p); }
		}
		Permission.displayOrder = newDisplayOrder;
	}

	public static List<Permission> valuesInDisplayOrder() { return displayOrder; }

	public final int id;
	
	private Permission(int id)
	{
		if (id > 63) { throw new IllegalArgumentException(); }
		this.id = id;
	}
	
	public static Permission forId(int id)
	{
		for (Permission p : Permission.values())
		{
			if (p.id == id) { return p; }
		}
		return null;
	}
	
	public static Set<Permission> decodeBitmask(long permissionsBitmask)
	{
		Set<Permission> permissions = new HashSet<>(Permission.values().length);
		for (Permission p : Permission.values())
		{
			if ((permissionsBitmask & (1 << p.id)) != 0)
			{
				permissions.add(p);
			}
		}
		return permissions;
	}
	
	public static long encodeBitmask(Set<Permission> permissions)
	{
		long result = 0;
		for (Permission p : permissions)
		{
			result |= 1 << p.id;
		}
		return result;
	}
}
