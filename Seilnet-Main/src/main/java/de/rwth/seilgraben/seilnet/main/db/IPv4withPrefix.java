/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import com.google.common.net.InetAddresses;

import lombok.Getter;

/**
 *
 * @author Felix Kirchmann
 */
public class IPv4withPrefix
{
	@Getter
	private final Inet4Address	ip;
	@Getter
	private final int			prefix;
	
	public IPv4withPrefix(Inet4Address ip, int prefix) // throws InvalidIPv4withPrefixException
	{
		if (prefix < 0 || prefix > 32) { throw new IllegalArgumentException("prefix length out of range (0-32)"); }
		byte[] addr = ip.getAddress();
		for (int i = 31; i >= prefix; i--)
		{
			if (((addr[i / 8] >> (i % 8)) & 1) != 0) { throw new IllegalArgumentException(
					"prefix " + prefix + " specified, but bit no. " + (i + 1) + " from the IP is nonzero"); }
		}
		try
		{
			this.ip = (Inet4Address) Inet4Address.getByAddress(ip.getAddress());
		}
		catch (UnknownHostException e)
		{
			throw new RuntimeException(e);
		}
		this.prefix = prefix;
	}
	
	public static IPv4withPrefix fromCidr(String cidrNotation) // throws InvalidIPv4withPrefixException
	{
		String[] split = cidrNotation.split(Pattern.quote("/"));
		if (split.length != 2) { throw new IllegalArgumentException(
				"Notation must be of the form \"address/prefix\""); }
		int prefix;
		Inet4Address ip;
		try
		{
			ip = (Inet4Address) InetAddresses.forString(split[0]);
			prefix = Integer.parseInt(split[1]);
		}
		catch (IllegalArgumentException | ClassCastException e)
		{
			if (e instanceof NumberFormatException)
			{
				throw new IllegalArgumentException("Prefix length is not a number");
			}
			else
			{
				throw new IllegalArgumentException("Address is not a valid IPv4");
			}
		}
		return new IPv4withPrefix(ip, prefix);
	}
	
	@Override
	public String toString()
	{
		return ip.getHostAddress() + "/" + prefix;
	}
	
	/*public static class InvalidIPv4withPrefixException extends Exception
	{
		private static final long serialVersionUID = 1L;
		
		private InvalidIPv4withPrefixException(String message)
		{
			super(message);
		}
	}
	
	public static class InvalidCidrNotationException extends InvalidIPv4withPrefixException
	{
		private static final long serialVersionUID = 1L;
		
		private InvalidCidrNotationException(String message)
		{
			super(message);
		}
	}*/
}
