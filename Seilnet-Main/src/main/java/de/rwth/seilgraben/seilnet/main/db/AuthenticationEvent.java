/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.time.Instant;

import de.rwth.seilgraben.seilnet.main.db.Database.DatabaseObject;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthType;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUser;
import lombok.NonNull;

/**
 *
 * @author Felix Kirchmann
 */
public class AuthenticationEvent extends DatabaseObject<DBAuthenticationEvent>
{
	private final DBAuthenticationEvent dbAuthEvent;
	
	AuthenticationEvent(Database db, @NonNull DBAuthenticationEvent dbAuthEvent)
	{
		super(db, db.authEventDao, dbAuthEvent);
		this.dbAuthEvent = dbAuthEvent;
	}
	
	@Override
	public int getId()
	{
		return dbAuthEvent.getId();
	}
	
	public Instant getTime()
	{
		return dbAuthEvent.getTime();
	}
	
	public String getClientInfo()
	{
		return dbAuthEvent.getClientInfo();
	}
	
	public AuthType getAuthType()
	{
		return dbAuthEvent.getAuthType();
	}
	
	public AuthResult getAuthResult()
	{
		return dbAuthEvent.getAuthResult();
	}
	
	public Integer getUserId()
	{
		DBUser user = dbAuthEvent.getUser();
		if (user == null) { return null; }
		return user.getId();
	}
}