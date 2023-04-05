/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.net.Inet4Address;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.db.orm.util.Inet4AddressPersister;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Felix Kirchmann
 */
@DatabaseTable(tableName = "IPv4_Addresses")
@Data
public class DBIPv4Address
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int					id;
											
	@DatabaseField(canBeNull = false, defaultValue = "FALSE")
	private volatile boolean				deleted;
											
	@DatabaseField(canBeNull = false, persisterClass = Inet4AddressPersister.class)
	private volatile Inet4Address			address;
											
	@DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "Current_Assignment_ID")
	private volatile DBUserIPv4Assignment	assignment;
											
	/*public DBIPv4Address()
	{}
	
	public DBIPv4Address(Inet4Address address)
	{
		this.address = address;
	}*/
}
