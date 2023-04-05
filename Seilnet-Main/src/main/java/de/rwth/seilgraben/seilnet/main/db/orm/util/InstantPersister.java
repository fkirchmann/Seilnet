/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm.util;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.TimeStampType;

/**
 * Source: https://stackoverflow.com/questions/13744883
 */
public class InstantPersister extends TimeStampType
{
	private static final InstantPersister	singleton	= new InstantPersister();
	private String							defaultStr;
	
	public InstantPersister()
	{
		super(SqlType.DATE, new Class<?>[] { java.sql.Timestamp.class });
	}
	
	public static InstantPersister getSingleton()
	{
		return singleton;
	}
	
	// TODO: shouldn't this be stateless?
	@Override
	public boolean isEscapedDefaultValue()
	{
		if ("CURRENT_TIMESTAMP".equals(defaultStr))
		{
			return false;
		}
		else
		{
			return super.isEscapedDefaultValue();
		}
	}
	
	@Override
	public Object parseDefaultString(FieldType fieldType, String defaultStr) throws SQLException
	{
		this.defaultStr = defaultStr;
		if ("CURRENT_TIMESTAMP".equals(defaultStr))
		{
			return defaultStr;
		}
		else
		{
			return super.parseDefaultString(fieldType, defaultStr);
		}
	}
	
	@Override
	public Object javaToSqlArg(FieldType fieldType, Object javaObject)
	{
		if (javaObject == null) { return null; }
		
		return Timestamp.from((Instant) javaObject);
	}
	
	@Override
	public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos)
	{
		if (sqlArg == null) { return null; }
		
		return ((Timestamp) sqlArg).toInstant();
	}
}