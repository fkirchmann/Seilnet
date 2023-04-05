/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm.util;

import java.util.HashSet;
import java.util.Set;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.LongType;

import de.rwth.seilgraben.seilnet.main.config.Permission;

/**
 *
 * @author Felix Kirchmann
 */
public class PermissionsPersister extends LongType
{
	private static final PermissionsPersister INSTANCE = new PermissionsPersister();
	
	private PermissionsPersister()
	{
		super(SqlType.LONG, new Class<?>[] { Set.class });
	}
	
	public static PermissionsPersister getSingleton()
	{
		return INSTANCE;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object javaToSqlArg(FieldType fieldType, Object javaObject)
	{
		if (javaObject == null) { return (long) 0; }
		
		return Permission.encodeBitmask((Set<Permission>) javaObject);
	}
	
	@Override
	public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos)
	{
		if (sqlArg == null) { return new HashSet<Permission>(); }
		
		return Permission.decodeBitmask((long) sqlArg);
	}
}
