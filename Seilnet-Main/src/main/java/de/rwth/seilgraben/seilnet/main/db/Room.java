/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.sql.SQLException;
import java.util.List;

import de.rwth.seilgraben.seilnet.main.db.Database.DatabaseObject;
import de.rwth.seilgraben.seilnet.main.db.Database.RoomNumberInUseException;
import de.rwth.seilgraben.seilnet.main.db.orm.DBRoom;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserRoomAssignment;
import lombok.NonNull;

/**
 *
 * @author Felix Kirchmann
 */
public class Room extends DatabaseObject<DBRoom>implements Comparable<Room>
{
	Room(Database db, @NonNull DBRoom dbObject)
	{
		super(db, db.roomDao, dbObject);
	}
	
	Room(Database db, @NonNull String roomNumber) throws RoomNumberInUseException
	{
		super(db, db.roomDao, new DBRoom());
		dbObject.setRoomNumber(roomNumber);
		
		synchronized (db)
		{
			Room existingRoom = db.roomsByNumber.get(roomNumber);
			if (existingRoom != null) { throw new Database.RoomNumberInUseException(existingRoom); }
			try
			{
				dao.create(dbObject);
			}
			catch (SQLException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
	
	@Override
	public int getId()
	{
		return dbObject.getId();
	}
	
	public void setRoomNumber(@NonNull String roomNumber) throws RoomNumberInUseException
	{
		if (roomNumber.equals(dbObject.getRoomNumber())) { return; }
		synchronized (db)
		{
			Room existingRoom = db.roomsByNumber.get(roomNumber);
			if (existingRoom != null) { throw new Database.RoomNumberInUseException(existingRoom); }
			db.roomsByNumber.remove(dbObject.getRoomNumber());
			dbObject.setRoomNumber(roomNumber);
			db.roomsByNumber.put(roomNumber, this);
			update();
		}
	}
	
	public String getRoomNumber()
	{
		return dbObject.getRoomNumber();
	}
	
	public void setDescription(String info)
	{
		dbObject.setDescription(info);
		update();
	}
	
	public String getDescription()
	{
		return dbObject.getDescription();
	}
	
	/*public void setVlan(Integer vlan)
	{
		dbObject.setVlan(vlan);
		// TODO: Update firewall rules
		update();
	}*/
	
	public Integer getVlan()
	{
		return dbObject.getVlan();
	}
	
	DBRoom getDbRoom()
	{
		return dbObject;
	}
	
	public User getCurrentUser()
	{
		return getUser(true);
	}
	
	public User getMainTenant()
	{
		return getUser(false);
	}
	
	private User getUser(boolean subtenantAllowed)
	{
		synchronized (db)
		{
			try
			{
				List<DBUserRoomAssignment> assignments = db.userRoomDao.queryBuilder().where()
						.eq("Room_ID", dbObject.getId()).and().isNull("Assigned_To").query();
				if (assignments.size() > 2)
				{
					throw new RuntimeException("Room ID " + dbObject.getId() + " is assigned to " + assignments.size()
							+ " users simultaneously!");
				}
				else if (assignments.isEmpty()) { return null; }
				
				for (DBUserRoomAssignment assignment : assignments)
				{
					if ((subtenantAllowed == assignment.isSubtenant())
							|| assignments.size() == 1) { return db.getUserByID(assignment.getUser().getId()); }
				}
				throw new RuntimeException("Room ID " + dbObject.getId() + " is assigned to " + assignments.size()
						+ " main tenants simultaneously!");
			}
			catch (SQLException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
	
	@Override
	public String toString()
	{
		return "[Rm:" + getId() + "] " + getRoomNumber();
	}
	
	/**
	 * Sorts based on room number, lexicographically, while ignoring upper- / lowercase.
	 * 
	 * @see String#compareToIgnoreCase(String)
	 */
	@Override
	public int compareTo(Room o)
	{
		if (o.dbObject.getId() == dbObject.getId()) { return 0; }
		return getRoomNumber().compareToIgnoreCase(o.getRoomNumber());
	}
}
