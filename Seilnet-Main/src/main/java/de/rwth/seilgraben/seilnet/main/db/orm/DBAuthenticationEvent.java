/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.time.Instant;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.I18nEnum;
import de.rwth.seilgraben.seilnet.main.db.orm.util.InstantPersister;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "Authentication_Events")
@Data
public class DBAuthenticationEvent
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int		id;
								
	@DatabaseField(foreign = true, foreignAutoRefresh = false, columnName = "User_ID")
	private volatile DBUser		user;
								
	@DatabaseField(defaultValue = "CURRENT_TIMESTAMP", readOnly = true, canBeNull = false,
			persisterClass = InstantPersister.class)
	private volatile Instant	time;
								
	@DatabaseField(columnName = "Client_Info")
	private volatile String		clientInfo;
								
	@DatabaseField(columnName = "Auth_Type", canBeNull = false)
	private volatile AuthType	authType;
								
	@DatabaseField(columnName = "Auth_Result", canBeNull = false)
	private volatile AuthResult	authResult;
								
	/*
	 * If you want to add an AuthType or AuthResult, remember to define the appropriate Strings in
	 * all strings_**_**.properties
	 * 
	 * WARNING: Do not remove or rename an entry from one of these enums.
	 */
	
	public static enum AuthType implements I18nEnum
	{
		WEB, WLAN
	}
	
	public static enum AuthResult implements I18nEnum
	{
		OK, UNKNOWN_USER, WRONG_PASSWORD, UNKNOWN_MAC, NO_LEASE, ACCOUNT_DEACTIVATED;
	}
}
