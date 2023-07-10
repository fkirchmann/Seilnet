/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.util;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * Immutable MAC Address representation.
 * 
 * This class is fully thread-safe.
 *
 * @author Felix Kirchmann
 */
@EqualsAndHashCode
public class MacAddress
{
	private final byte[] mac;
	
	/**
	 * @param mac
	 *            The MAC address to be stored. <code>mac.length</code> must be exactly 6.
	 * @throws IllegalArgumentException
	 *             If <code>mac</code> does not contain exactly 6 bytes.
	 */
	public MacAddress(@NonNull byte[] mac)
	{
		if (mac.length != 6) { throw new IllegalArgumentException("MAC address length does not equal 6 bytes"); }
		this.mac = mac;
	}
	
	/**
	 * @param macStr
	 *            The MAC address to be stored, in textual representation. E.g. 00:11:22:aa:bb:cc
	 * @throws IllegalArgumentException
	 *             If <code>mac</code> can not be parsed as a MAC address.
	 */
	public MacAddress(@NonNull String macStr)
	{
		String macStrLower = macStr.toLowerCase().replace("-", "");
		macStrLower = macStrLower.replace("_", "");
		macStrLower = macStrLower.replace(":", "");
		if (!macStrLower.matches("^([0-9a-f]{2}){6}$")) { throw new IllegalArgumentException(
				"\"" + macStr + "\" is not a valid MAC address"); }
		
		mac = new byte[6];
		for (int i = 0; i < 6; i++)
		{
			mac[i] = (byte) Integer.parseInt(macStrLower.substring(i * 2, (i * 2) + 2), 16);
		}
	}
	
	/**
	 * @return A copy of the mac address given to {@link MacAddress#MacAddress(byte[])}}. This means
	 *         that changes in the array returned by this method will not change the MAC address
	 *         represented by this object.
	 */
	public byte[] getMac()
	{
		return mac.clone();
	}
	
	/**
	 * Returns the MAC in textual representation, e.g. 00:11:22:33:44:55
	 * 
	 * thx @ https://stackoverflow.com/questions/2797430
	 */
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(17);
		for (byte b : mac)
		{
			if (sb.length() > 0)
			{
				sb.append(':');
			}
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
