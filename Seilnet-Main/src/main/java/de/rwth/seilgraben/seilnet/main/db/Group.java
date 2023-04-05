/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Database.DatabaseObject;
import de.rwth.seilgraben.seilnet.main.db.Database.DatabaseObjectDeletedException;
import de.rwth.seilgraben.seilnet.main.db.Database.GroupNameInUseException;
import de.rwth.seilgraben.seilnet.main.db.orm.DBGroup;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserGroupAssignment;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;

/**
 *
 * @author Felix Kirchmann
 */
public class Group extends DatabaseObject<DBGroup>
{
	@Getter
	private boolean			deleted	= false;
	private final Object	dbLock;
							
	Group(Database db, @NonNull DBGroup dbGroup)
	{
		super(db, db.groupDao, dbGroup);
		dbLock = db;
	}
	
	Group(Database db, @NonNull String name) throws GroupNameInUseException
	{
		super(db, db.groupDao, new DBGroup());
		dbLock = db;
		dbObject.setName(name);
		
		synchronized (db)
		{
			Group existingGroup = db.groupsByName.get(name);
			if (existingGroup != null) { throw new Database.GroupNameInUseException(existingGroup); }
			try
			{
				dao.create(dbObject);
				dao.refresh(dbObject);
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
	
	@Synchronized("dbLock")
	public void setName(@NonNull String name) throws GroupNameInUseException
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		if (name.equals(dbObject.getName())) { return; }
		
		Group existingGroup = db.groupsByName.get(name);
		if (existingGroup != null) { throw new Database.GroupNameInUseException(existingGroup); }
		db.groupsByName.remove(dbObject.getName());
		dbObject.setName(name);
		db.groupsByName.put(name, this);
		update();
	}
	
	public String getName()
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		return dbObject.getName();
	}
	
	public boolean isShowMailingList()
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		return dbObject.isShowMailingList();
	}
	
	@Synchronized("dbLock")
	public void setShowMailingList(boolean showMailingList)
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		dbObject.setShowMailingList(showMailingList);
		update();
	}
	
	public String getEmail()
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		return dbObject.getEmail();
	}
	
	@Synchronized("dbLock")
	public void setEmail(String email)
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		dbObject.setEmail(email);
		update();
	}
	
	@Synchronized("dbLock")
	public void setPermissions(@NonNull Set<Permission> permissions)
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		dbObject.setPermissions(new HashSet<Permission>(permissions));
		update();
		updateFirewallRules(listMembers());
	}
	
	@Synchronized("dbLock")
	public Set<Permission> getPermissions()
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		return new HashSet<Permission>(dbObject.getPermissions());
	}
	
	DBGroup getDbGroup()
	{
		return dbObject;
	}
	
	public Set<User> listMembers()
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		
		synchronized (db)
		{
			try
			{
				Set<User> users = new HashSet<>();
				for (DBUserGroupAssignment assignment : db.userGroupDao.queryForEq("Group_ID", getId()))
				{
					users.add(db.getUserByID(assignment.getUser().getId()));
				}
				return users;
			}
			catch (SQLException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	@Synchronized("dbLock")
	private void updateFirewallRules(Set<User> users)
	{
		users.stream()
				.map(User::getRoomAssignment)
				.filter(roomAssignment -> roomAssignment != null)
				.map(User.RoomAssignment::getRoom)
				.forEach(SeilnetMain.getFirewallManager()::updateRules);
	}
	
	@Synchronized("dbLock")
	@SneakyThrows
	public void delete()
	{
		if (deleted) { throw new DatabaseObjectDeletedException(); }
		Set<User> members = listMembers();
		deleted = true;
		
		for (User member : members)
		{
			member.removeFromGroup(this);
		}
		
		db.groupsByName.remove(dbObject.getName());
		db.groups.remove(dbObject.getId());
		db.groupDao.delete(dbObject);

		updateFirewallRules(members);
	}
}
