/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.time.Instant;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.db.orm.util.InstantPersister;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "User_IPv4_Assignments")
@Data
public class DBUserIPv4Assignment
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int			id;
									
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "User_ID", canBeNull = false)
	private volatile DBUser			user;
									
	@DatabaseField(columnName = "Assigned_From", defaultValue = "CURRENT_TIMESTAMP", readOnly = true, canBeNull = false,
			persisterClass = InstantPersister.class)
	private volatile Instant		assignedFrom;
									
	@DatabaseField(columnName = "Assigned_To", persisterClass = InstantPersister.class)
	private volatile Instant		assignedTo;
									
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "IP_ID", canBeNull = false)
	private volatile DBIPv4Address	address;
}
