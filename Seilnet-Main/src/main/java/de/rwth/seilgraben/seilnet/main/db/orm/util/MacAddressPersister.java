/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm.util;

import java.net.Inet4Address;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.StringType;

import de.rwth.seilgraben.seilnet.util.MacAddress;

/**
 * Thanks to
 * https://stackoverflow.com/questions/29733464
 * and
 * https://stackoverflow.com/questions/2241229
 *
 * @author Felix Kirchmann
 */
public class MacAddressPersister extends StringType
{
	
	private static final MacAddressPersister INSTANCE = new MacAddressPersister();
	
	private MacAddressPersister()
	{
		super(SqlType.STRING, new Class<?>[] { Inet4Address.class });
	}
	
	public static MacAddressPersister getSingleton()
	{
		return INSTANCE;
	}
	
	@Override
	public Object javaToSqlArg(FieldType fieldType, Object javaObject)
	{
		if (javaObject == null) { return null; }
		
		return ((MacAddress) javaObject).toString();
	}
	
	@Override
	public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos)
	{
		if (sqlArg == null) { return null; }
		
		return new MacAddress((String) sqlArg);
	}
}