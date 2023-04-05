/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import lombok.Cleanup;
import lombok.NonNull;

/**
 *
 * @author Felix Kirchmann
 */
public class Func
{
	/**
	 * Returns the VLAN from an IPv4 address.
	 * 
	 * This is highly specific to the Seilgraben network, and should instead be made configurable
	 * somewhere.
	 * 
	 * @param ipAddress
	 *            The IPv4 address in textual representation, e.g. 192.168.143.226
	 * @return The VLAN that this IP Address belongs to, or <code>-1</code> if the VLAN is unknown.
	 * @throws IllegalArgumentException
	 *             If ipAddress is not a valid IPv4.
	 */
	public static int getVlan(String ipAddress)
	{
		String[] split = ipAddress.split(Pattern.quote("."));
		int subnet;
		try
		{
			subnet = Integer.parseInt(split[2]);
		}
		catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
		{
			throw new IllegalArgumentException("Unrecognized ipv4 \"" + ipAddress + "\"");
		}
		if (subnet >= 100 && subnet <= 255)
		{
			return subnet;
		}
		else if (subnet == 0)
		{
			return 10;
		}
		else
		{
			return -1;
		}
	}
	
	/**
	 * Reads the contents of an {@link InputStream} until EOF, decodes them using UTF-8 and returns
	 * the resulting String.
	 * 
	 * @param in
	 *            The InputStream to read from.
	 * @return The contents of the InputStream, decoded using UTF-8.
	 * @throws IOException
	 *             If an I/O Exception occurs.
	 */
	public static String readInputStream(InputStream in) throws IOException
	{
		@Cleanup Scanner scanner = new Scanner(in, "UTF-8");
		return scanner.useDelimiter("\\A").next();
	}
	
	public static void notNull(Object ... objects)
	{
		for (Object o : objects)
		{
			if (o == null) { throw new NullPointerException(); }
		}
	}
	
	/**
	 * thx @ stackoverflow #740299
	 */
	public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c)
	{
		List<T> list = new ArrayList<>(c);
		Collections.sort(list);
		return list;
	}
	
	public static URL getClasspathResource(String path)
	{
		Func f = new Func();
		URL r = f.getClass().getResource(path);
		if (r != null) { return r; }
		r = f.getClass().getClassLoader().getResource(path);
		if (r != null) { return r; }
		r = f.getClass().getResource("/" + path);
		if (r != null) { return r; }
		if (!path.startsWith("resources/")) { return getClasspathResource("resources/" + path); }
		return null;
	}
	
	public static InputStream getClasspathResourceAsStream(String path)
	{
		try
		{
			return getClasspathResource(path).openStream();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Converts any given <code>Object</code> to a <code>String</code>, treating the following
	 * object types in a special manner: <br />
	 * - <code>String</code> is returned as-is. <br />
	 * - <code>Throwable</code> will return the stacktrace. <br />
	 * Any other object will return <code>Object.toString()</code>.
	 *
	 * @param object
	 *            The <code>Object</code> that shall be converted.
	 * @return
	 * 		The <code>String</code> representation of the given <code>Object</code>.
	 * @throws NullPointerException
	 *             if <code>object</code> is <code>null</code>
	 */
	public static String object2string(final Object object)
	{
		if (object == null) { throw new NullPointerException(); }
		if (object instanceof String)
		{
			return (String) object;
		}
		else if (object instanceof Throwable)
		{
			final StringWriter stringWriter = new StringWriter();
			final PrintWriter printWriter = new PrintWriter(stringWriter);
			((Throwable) object).printStackTrace(printWriter);
			return stringWriter.toString();
		}
		else
		{
			return object.toString();
		}
	}
	
	private static SecureRandom rng = new SecureRandom();
	
	/**
	 * Generates a random string. This method uses {@link SecureRandom} to generate random
	 * numbers, it can thus be used to generate sensitive data (e.g. passwords).
	 * 
	 * @param length
	 *            The length of the desired random string.
	 * @param randomChars
	 *            The characters to be used in the random string. For example, "0123456789" would
	 *            cause this method to generate numbers. Note that each character must only occur
	 *            once inside <code>randomChars</code>.
	 * @return A random string.
	 */
	public static String generateRandomString(int length, @NonNull String randomChars)
	{
		if (randomChars.length() == 0) { throw new IllegalArgumentException("randomChars must not be empty"); }
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++)
		{
			sb.append(randomChars.charAt(rng.nextInt(randomChars.length())));
		}
		return sb.toString();
	}
}
