/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm.util;

import java.net.Inet4Address;
import java.net.InetAddress;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.StringType;

import lombok.SneakyThrows;

/**
 * Thanks to
 * https://stackoverflow.com/questions/29733464
 * and
 * https://stackoverflow.com/questions/2241229
 *
 * @author Felix Kirchmann
 */
/*public class Inet4AddressPersister extends IntegerObjectType
{
	private static final Inet4AddressPersister INSTANCE = new Inet4AddressPersister();
	
	private Inet4AddressPersister()
	{
		super(SqlType.STRING, new Class<?>[] { Inet4Address.class });
	}
	
	public static Inet4AddressPersister getSingleton()
	{
		return INSTANCE;
	}
	
	@Override
	public Object javaToSqlArg(FieldType fieldType, Object javaObject)
	{
		if (javaObject == null) { return null; }
		
		byte[] bytes = ((Inet4Address) javaObject).getAddress();
		int val = 0;
		for (int i = 0; i < bytes.length; i++)
		{
			val <<= 8;
			val |= bytes[i] & 0xff;
		}
		return val;
	}
	
	@Override
	public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos)
	{
		if (sqlArg == null) { return null; }
		
		int bytes = (int) sqlArg;
		
		try
		{
			return InetAddress.getByAddress(new byte[] { (byte) ((bytes >>> 24) & 0xff), (byte) ((bytes >>> 16) & 0xff),
					(byte) ((bytes >>> 8) & 0xff), (byte) ((bytes) & 0xff) });
		}
		catch (UnknownHostException e)
		{
			throw new RuntimeException(e);
		}
	}
}*/
public class Inet4AddressPersister extends StringType
{
	private static final Inet4AddressPersister INSTANCE = new Inet4AddressPersister();
	
	private Inet4AddressPersister()
	{
		super(SqlType.STRING, new Class<?>[] { Inet4Address.class });
	}
	
	public static Inet4AddressPersister getSingleton()
	{
		return INSTANCE;
	}
	
	@Override
	public Object javaToSqlArg(FieldType fieldType, Object javaObject)
	{
		if (javaObject == null) { return null; }
		
		return ((Inet4Address) javaObject).getHostAddress();
	}
	
	@Override
	@SneakyThrows
	public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos)
	{
		if (sqlArg == null) { return null; }
		
		return InetAddress.getByName((String) sqlArg);
	}
}