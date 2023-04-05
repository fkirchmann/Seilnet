/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "Rooms")
@Data
public class DBRoom
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int id;
	
	@DatabaseField(columnName = "Room_Nr", canBeNull = false, unique = true)
	private volatile String roomNumber;
	
	@DatabaseField
	private volatile String description;
	
	@DatabaseField
	private volatile Integer vlan;
}
