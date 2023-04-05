/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.SqlDateType;

/**
 * Thanks to
 * https://stackoverflow.com/questions/29750861
 *
 * @author Felix Kirchmann
 */
public class LocalDatePersister extends SqlDateType
{
	private static final LocalDatePersister INSTANCE = new LocalDatePersister();
	
	private LocalDatePersister()
	{
		super(SqlType.STRING, new Class<?>[] { LocalDate.class });
	}
	
	public static LocalDatePersister getSingleton()
	{
		return INSTANCE;
	}
	
	@Override
	public Object javaToSqlArg(FieldType fieldType, Object javaObject)
	{
		if (javaObject == null) { return null; }
		
		return Date.valueOf((LocalDate) javaObject);
	}
	
	@Override
	public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos)
	{
		if (sqlArg == null) { return null; }
		
		if (sqlArg instanceof Date)
		{
			return ((Date) sqlArg).toLocalDate();
		}
		else
		{
			return ((Timestamp) sqlArg).toLocalDateTime().toLocalDate();
		}
	}
}