/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.time.LocalDate;
import java.util.Set;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.orm.util.LocalDatePersister;
import de.rwth.seilgraben.seilnet.main.db.orm.util.PermissionsPersister;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "Users")
@Data
public class DBUser
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int					id;
											
	@DatabaseField(canBeNull = false, defaultValue = "FALSE")
	private volatile boolean				deleted;
											
	@DatabaseField(canBeNull = false, defaultValue = "FALSE")
	private volatile boolean				deactivated;
											
	@DatabaseField(columnName = "First_Name", canBeNull = false)
	private volatile String					firstName;

	@DatabaseField(columnName = "Last_Name", canBeNull = false)
	private volatile String					lastName;
											
	@DatabaseField(canBeNull = false, unique = true, index = true)
	private volatile String					email;
											
	@DatabaseField(canBeNull = false)
	private volatile String					locale;
											
	@DatabaseField
	private volatile String					phone;

	@DatabaseField(columnName = "Matriculation_Number")
	private volatile String					matriculationNumber;

	@DatabaseField(columnName = "TIM_Username")
	private volatile String					timUsername;

	@DatabaseField
	private volatile String					comments;
											
	@DatabaseField(persisterClass = LocalDatePersister.class)
	private volatile LocalDate				birthday;
											
	@DatabaseField(columnName = "Web_Password_Hash")
	private volatile String					webPasswordHash;
											
	@DatabaseField(columnName = "Web_Password_Reset_Token")
	private volatile String					webPasswordResetToken;
											
	@DatabaseField(columnName = "Wlan_Password")
	private volatile String					wlanPassword;

	@DatabaseField(canBeNull = false, defaultValue = "FALSE")
	private volatile boolean				adblock;

	@DatabaseField(columnName = "NAT_IPv4_Dynamic", canBeNull = false, defaultValue = "TRUE")
	private volatile boolean				natIPv4Dynamic;

	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "NAT_IPv4_Assignment_ID")
	private volatile DBUserIPv4Assignment	natIPv4Assignment;
											
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "Room_Assignment_ID")
	private volatile DBUserRoomAssignment	roomAssignment;
											
	@DatabaseField(canBeNull = false, defaultValue = "0", persisterClass = PermissionsPersister.class)
	private volatile Set<Permission>		permissions;
}
