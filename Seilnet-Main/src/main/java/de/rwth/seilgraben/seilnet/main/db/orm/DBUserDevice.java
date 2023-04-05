/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.time.Instant;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.db.orm.util.InstantPersister;
import de.rwth.seilgraben.seilnet.main.db.orm.util.MacAddressPersister;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "User_Devices")
@Data
public class DBUserDevice
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int		id;
								
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "User_ID", canBeNull = false)
	private volatile DBUser		user;
								
	@DatabaseField(columnName = "Assigned_From", defaultValue = "CURRENT_TIMESTAMP", readOnly = true, canBeNull = false,
			persisterClass = InstantPersister.class)
	private volatile Instant	assignedFrom;
								
	@DatabaseField(columnName = "Assigned_To", persisterClass = InstantPersister.class)
	private volatile Instant	assignedTo;
								
	@DatabaseField(canBeNull = false)
	private volatile String		name;
								
	@DatabaseField(columnName = "MAC_Address", canBeNull = false, persisterClass = MacAddressPersister.class)
	private volatile MacAddress	macAddress;
}
