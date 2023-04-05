/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import lombok.Data;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "User_Groups")
@Data
public class DBUserGroupAssignment
{
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "User_ID", canBeNull = false)
	private volatile DBUser		user;
								
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "Group_ID", canBeNull = false)
	private volatile DBGroup	group;
}
