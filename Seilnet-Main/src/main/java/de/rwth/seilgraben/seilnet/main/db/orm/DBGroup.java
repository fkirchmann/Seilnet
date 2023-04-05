/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.util.Set;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.orm.util.PermissionsPersister;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "Groups")
@Data
public class DBGroup
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int				id;
										
	@DatabaseField(canBeNull = false, unique = true)
	private volatile String				name;
										
	@DatabaseField
	private volatile String				email;
										
	@DatabaseField(canBeNull = false, defaultValue = "0", persisterClass = PermissionsPersister.class)
	private volatile Set<Permission>	permissions;
										
	@DatabaseField(columnName = "Show_Mailing_List", canBeNull = false, defaultValue = "FALSE")
	private volatile boolean			showMailingList;
}
