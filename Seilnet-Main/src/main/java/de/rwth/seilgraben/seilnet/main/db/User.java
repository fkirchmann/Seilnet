/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.net.Inet4Address;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.esotericsoftware.minlog.Log;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.lambdaworks.crypto.SCryptUtil;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.LogCategory;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Database.DatabaseObject;
import de.rwth.seilgraben.seilnet.main.db.Database.EMailInUseException;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.main.db.orm.DBIPv4Address;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUser;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserDevice;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserGroupAssignment;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserIPv4Assignment;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserRoomAssignment;
import de.rwth.seilgraben.seilnet.util.Func;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;

/**
 *
 * @author Felix Kirchmann
 */
public class User extends DatabaseObject<DBUser>
{
	@Getter(AccessLevel.PACKAGE)
	private final Set<Group>		groups			= new HashSet<>();
	private volatile RoomAssignment	assignment		= null;
	private final List<Device>		assignedDevices	= new ArrayList<>();
	private final List<Device>		previousDevices	= new ArrayList<>();
	
	private final Object			dbLock;
	
	User(Database db, @NonNull DBUser dbObject)
	{
		super(db, db.userDao, dbObject);
		dbLock = db;
		
		if (dbObject.getRoomAssignment() != null)
		{
			assignment = new RoomAssignment(db, this, dbObject.getRoomAssignment());
		}
		
		synchronized (db)
		{
			try
			{
				for (DBUserGroupAssignment assignment : db.userGroupDao.queryForEq("User_ID", getId()))
				{
					groups.add(db.getGroupByID(assignment.getGroup().getId()));
				}
				for (DBUserDevice device : db.userDeviceDao.queryForEq("User_ID", getId()))
				{
					Device deviceObj = new Device(db, this, device);
					if (device.getAssignedTo() == null)
					{
						assignedDevices.add(deviceObj);
					}
					else
					{
						previousDevices.add(deviceObj);
					}
				}
			}
			catch (SQLException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
	
	User(Database db, @NonNull String firstName, @NonNull String lastName, @NonNull String email,
			@NonNull Locale locale) throws EMailInUseException
	{
		super(db, db.userDao, new DBUser());
		dbLock = db;
		
		dbObject.setFirstName(firstName);
		dbObject.setLastName(lastName);
		dbObject.setEmail(email);
		dbObject.setLocale(locale.toLanguageTag());
		dbObject.setWlanPassword(
				Func.generateRandomString(Constants.RADIUS_PASSWORD_LENGTH, Constants.RADIUS_PASSWORD_CHARSET));
		
		synchronized (db)
		{
			try
			{
				User emailUser = db.getUserByEmail(email);
				if (emailUser != null) { throw new Database.EMailInUseException(emailUser); }
				db.userDao.create(dbObject);
				db.userDao.refresh(dbObject);
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
	
	public String getFirstName()
	{
		return dbObject.getFirstName();
	}
	
	@Synchronized("dbLock")
	public void setFirstName(@NonNull String firstName)
	{
		dbObject.setFirstName(firstName);
		update();
	}
	
	public String getLastName()
	{
		return dbObject.getLastName();
	}
	
	@Synchronized("dbLock")
	public void setLastName(@NonNull String lastName)
	{
		dbObject.setLastName(lastName);
		update();
	}
	
	/**
	 * @return First and last name, separated by a space, e.g. "John Doe"
	 */
	public String getFullName()
	{
		return getFirstName() + " " + getLastName();
	}
	
	public LocalDate getBirthday()
	{
		return dbObject.getBirthday();
	}
	
	@Synchronized("dbLock")
	public void setBirthday(LocalDate birthday)
	{
		dbObject.setBirthday(birthday);
		update();
	}
	
	public String getPhone()
	{
		return dbObject.getPhone();
	}
	
	@Synchronized("dbLock")
	public void setPhone(String phone)
	{
		dbObject.setPhone(phone);
		update();
	}
	
	public String getEmail()
	{
		return dbObject.getEmail();
	}
	
	@Synchronized("dbLock")
	public void setEmail(@NonNull String email)
	{
		dbObject.setEmail(email);
		update();
	}
	
	public Locale getLocale()
	{
		return Locale.forLanguageTag(dbObject.getLocale());
	}
	
	@Synchronized("dbLock")
	public void setLocale(@NonNull Locale locale)
	{
		dbObject.setLocale(locale.toLanguageTag());
		update();
	}

	public String getMatriculationNumber() { return dbObject.getMatriculationNumber(); }

	@Synchronized("dbLock")
	public void setMatriculationNumber(String matriculationNumber)
	{
		dbObject.setMatriculationNumber(matriculationNumber);
		update();
	}

	public String getTimUsername() { return dbObject.getTimUsername(); }

	@Synchronized("dbLock")
	public void setTimUsername(String timUsername)
	{
		dbObject.setTimUsername(timUsername);
		update();
	}

	public String getComments() { return dbObject.getComments(); }

	@Synchronized("dbLock")
	public void setComments(String comments)
	{
		dbObject.setComments(comments);
		update();
	}
	
	public String getWlanPassword()
	{
		return dbObject.getWlanPassword();
	}
	
	@Synchronized("dbLock")
	public void setWlanPassword(String wlanPassword)
	{
		dbObject.setWlanPassword(wlanPassword);
		update();
	}
	
	public boolean verifyWebPassword(@NonNull String passwordToVerify)
	{
		if (dbObject.getWebPasswordHash() == null) { return false; }
		return SCryptUtil.check(passwordToVerify, dbObject.getWebPasswordHash());
	}
	
	public void setWebPassword(String webPassword)
	{
		if (webPassword == null)
		{
			dbObject.setWebPasswordHash(null);
			return;
		}
		String pwCrypt = SCryptUtil.scrypt(webPassword, Constants.SCRYPT_N, Constants.SCRYPT_R, Constants.SCRYPT_P);
		synchronized (db)
		{
			dbObject.setWebPasswordHash(pwCrypt);
			update();
		}
	}
	
	public String getWebPasswordResetToken()
	{
		return dbObject.getWebPasswordResetToken();
	}
	
	@Synchronized("dbLock")
	public String generateWebPasswordResetToken()
	{
		String token = Func.generateRandomString(32, Constants.PASSWORD_RESET_TOKEN_CHARSET);
		dbObject.setWebPasswordResetToken(token);
		update();
		return token;
	}
	
	public void deactivateWebPasswordResetToken()
	{
		dbObject.setWebPasswordResetToken(null);
		update();
	}
	
	public boolean isDeactivated()
	{
		return dbObject.isDeactivated();
	}
	
	@Synchronized("dbLock")
	public void setDeactivated(boolean deactivated)
	{
		dbObject.setDeactivated(deactivated);
		update();
		updateFirewallRules();
	}
	
	public boolean isDeleted()
	{
		return dbObject.isDeleted();
	}
	
	@Synchronized("dbLock")
	public void setDeleted(boolean deleted)
	{
		dbObject.setDeleted(deleted);
		update();
		updateFirewallRules();
	}
	
	@Synchronized("dbLock")
	public void addToGroup(Group group)
	{
		if (group.isDeleted()) { throw new Database.DatabaseObjectDeletedException(); }
		if (groups.contains(group)) { return; }
		
		DBUserGroupAssignment assignment = new DBUserGroupAssignment();
		assignment.setGroup(group.getDbGroup());
		assignment.setUser(dbObject);
		try
		{
			db.userGroupDao.create(assignment);
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
		groups.add(group);
		updateFirewallRules();
	}
	
	@Synchronized("dbLock")
	@SneakyThrows
	public boolean removeFromGroup(Group group)
	{
		if (!groups.remove(group)) { return false; }
		DeleteBuilder<DBUserGroupAssignment, Integer> deleteBuilder = db.userGroupDao.deleteBuilder();
		deleteBuilder.where().eq("User_ID", getId()).and().eq("Group_ID", group.getId());
		if (deleteBuilder.delete() < 1)
		{
			Log.warn(LogCategory.DB,
					new SQLException("Removed user from group, but no group membership was stored in SQL DB"));
		}
		updateFirewallRules();
		return true;
	}
	
	@Synchronized("dbLock")
	public Set<Group> listGroups()
	{
		return new HashSet<>(groups);
	}
	
	@Synchronized("dbLock")
	public boolean hasPermission(Permission p)
	{
		if (dbObject.getPermissions().contains(p)) { return true; }
		for (Group g : groups)
		{
			if (g.getPermissions().contains(p)) { return true; }
		}
		return false;
	}
	
	/**
	 * *******************************************
	 * ********** USER ROOM ASSIGNMENTS **********
	 * *******************************************
	 */
	
	@Synchronized("dbLock")
	public RoomAssignment getRoomAssignment()
	{
		return assignment;
	}
	
	/**
	 * Atomically assigns this user to a room and gives him an IP address.
	 */
	@Synchronized("dbLock")
	public void assignRoom(@NonNull Room room, Instant leaseExpiration, boolean subtenant)
			throws UserAlreadyAssignedException, RoomAlreadyAssignedException, RoomWithoutMainTenantException,
			SubTenantExpiresAfterMainTenantException, NoFreeIPv4Exception
	{
		if (assignment != null) { throw new UserAlreadyAssignedException(); }
		if (db.getFreeIPv4() == null) { throw new NoFreeIPv4Exception(); }
		
		User currentUser = room.getCurrentUser();
		if ((currentUser != null && currentUser.getRoomAssignment().isSubtenant())
				|| (currentUser != null && !subtenant)) { throw new RoomAlreadyAssignedException(room, currentUser); }
		if (currentUser == null && subtenant) { throw new RoomWithoutMainTenantException(room); }
		if (subtenant)
		{
			User mainTenant = room.getMainTenant();
			Instant mainTenantExpiration = room.getMainTenant().getRoomAssignment().getExpiration();
			if (mainTenantExpiration
					.compareTo(leaseExpiration) <= 0) { throw new SubTenantExpiresAfterMainTenantException(room,
							mainTenant, mainTenantExpiration); }
		}
		
		assignment = new RoomAssignment(db, room, this, leaseExpiration, subtenant);
		dbObject.setRoomAssignment(assignment.dbObject);
		update();
		assignNatIPv4();
		db.roomExpiryService.schedule(dbObject.getId(), leaseExpiration);
		updateFirewallRules();
	}

	public static class UserAlreadyAssignedException extends Exception
	{
		private static final long serialVersionUID = 1410800193327786765L;
	}
	
	public static class PastLeaseExpirationException extends Exception
	{
		private static final long serialVersionUID = 1410800193327786765L;
	}
	
	public static class RoomAlreadyAssignedException extends Exception
	{
		private static final long	serialVersionUID	= 7859113620920384725L;
		
		@Getter
		private final User			currentTenant;
		
		@Getter
		private final Room			room;
		
		public RoomAlreadyAssignedException(Room room, User currentTenant)
		{
			this.room = room;
			this.currentTenant = currentTenant;
		}
	}
	
	/**
	 * Thrown when one tries to assign a user as a subtenant to a room which currently has no main
	 * tenant.
	 *
	 * @author Felix Kirchmann
	 */
	public static class RoomWithoutMainTenantException extends Exception
	{
		private static final long	serialVersionUID	= -4677843099355626336L;
		
		@Getter
		private final Room			room;
		
		public RoomWithoutMainTenantException(Room room)
		{
			this.room = room;
		}
	}
	
	public class RoomAssignment extends DatabaseObject<DBUserRoomAssignment>
	{
		private final Object	dbLockInt	= dbLock;
		@Getter
		private final User		user;
		
		private RoomAssignment(@SuppressWarnings("unused") @NonNull Database db, @NonNull User user,
				@NonNull DBUserRoomAssignment assignment)
		{
			super(db, db.userRoomDao, assignment);
			
			this.user = user;
		}
		
		private RoomAssignment(Database db, @NonNull Room room, @NonNull User user, Instant expiration,
				boolean subtenant)
		{
			this(db, user, new DBUserRoomAssignment());
			
			dbObject.setRoom(room.dbObject);
			dbObject.setUser(user.dbObject);
			dbObject.setExpiration(expiration);
			dbObject.setSubtenant(subtenant);
			
			synchronized (db)
			{
				try
				{
					dao.create(dbObject);
					dao.refresh(dbObject); // Workaround to load the DB-generated AssignedFrom
				}
				catch (SQLException e)
				{
					throw new RuntimeException(e);
				}
			}
		}
		
		@Synchronized("dbLockInt")
		public void endNow() throws AssignmentAlreadyEndedException, RoomHasSubTenantException
		{
			// Room currently has a subtenant? Can't remove the main tenant's lease then.
			if (!getRoom().getCurrentUser().equals(user)) { throw new RoomHasSubTenantException(); }
			
			dbObject.setExpiration(Instant.now());
			cleanup();
		}
		
		@Synchronized("dbLockInt")
		public void setExpiration(Instant newExpiration) throws AssignmentAlreadyEndedException,
				SubTenantExpiresAfterMainTenantException, EarlyLeaseExpirationException
		{
			if (isExpirationComplete()) { throw new AssignmentAlreadyEndedException(); }
			if (isSubtenant())
			{
				User mainTenant = getRoom().getMainTenant();
				Instant mainTenantExpiration = mainTenant.getRoomAssignment().getExpiration();
				if (mainTenantExpiration
						.compareTo(newExpiration) <= 0) { throw new SubTenantExpiresAfterMainTenantException(getRoom(),
								mainTenant, mainTenantExpiration); }
			}
			else
			{
				User subTenant = getRoom().getCurrentUser();
				if (!subTenant.equals(user))
				{
					if (newExpiration.compareTo(subTenant.getRoomAssignment()
							.getExpiration()) <= 0) { throw new SubTenantExpiresAfterMainTenantException(getRoom(),
									user, dbObject.getExpiration()); }
				}
			}
			if (newExpiration != null
					&& newExpiration.compareTo(Instant.now()) <= 0) { throw new EarlyLeaseExpirationException(); }
			dbObject.setExpiration(newExpiration);
			update();
		}
		
		@Synchronized("dbLockInt")
		public boolean isExpirationComplete()
		{
			return dbObject.getAssignedTo() != null;
		}
		
		public Instant getExpiration()
		{
			return dbObject.getExpiration();
		}
		
		public Instant getAssignedFrom()
		{
			return dbObject.getAssignedFrom();
		}
		
		public Instant getAssignedTo()
		{
			return dbObject.getAssignedTo();
		}
		
		public boolean isSubtenant()
		{
			return dbObject.isSubtenant();
		}
		
		public Room getRoom()
		{
			return db.getRoomByID(dbObject.getRoom().getId());
		}
		
		/**
		 * Ends this User <-> Room assignment if it is past its expiration date. This can be used
		 * together with a {@link de.rwth.seilgraben.seilnet.main.SimpleTaskSchedulerService}: as long as
		 * <code>cleanup()</code> returns a non-<code>null</code> value, the scheduler needs to schedule an expiration
		 * at the returned {@link Instant}.
		 * 
		 * @return The expiration date if it is in the future (in this case, the assignment has not
		 *         been ended yet), <code>null</code> otherwise.
		 */
		@Synchronized("dbLockInt")
		public Instant cleanup()
		{
			if (!isExpirationComplete() && dbObject.getExpiration().compareTo(Instant.now()) <= 0)
			{
				assignment = null;
				user.dbObject.setRoomAssignment(null);
				user.update();
				dbObject.setAssignedTo(Instant.now());
				update();
				user.unassignNatIPv4();
				
				SeilnetMain.getFirewallManager().updateRules(getRoom());
				return null;
			}
			return dbObject.getExpiration();
		}
		
		@Override
		public int getId()
		{
			return dbObject.getId();
		}
	}
	
	public static class AssignmentAlreadyEndedException extends Exception
	{
		private static final long serialVersionUID = 657040449974842921L;
	}
	
	public static class EarlyLeaseExpirationException extends Exception
	{
		private static final long serialVersionUID = 3571355472697884639L;
	}
	
	public static class SubTenantExpiresAfterMainTenantException extends Exception
	{
		private static final long	serialVersionUID	= 657040449974842921L;
		
		@Getter
		private final Room			room;
		@Getter
		private final User			mainTenant;
		@Getter
		private final Instant		mainTenantExpiration;
		
		public SubTenantExpiresAfterMainTenantException(Room room, User mainTenant, Instant mainTenantExpiration)
		{
			this.room = room;
			this.mainTenant = mainTenant;
			this.mainTenantExpiration = mainTenantExpiration;
		}
	}
	
	public static class RoomHasSubTenantException extends Exception
	{
		private static final long serialVersionUID = -5033774799820992333L;
	}
	
	/**
	 * **********************************
	 * ********** USER DEVICES **********
	 * **********************************
	 */
	
	@Synchronized("dbLock")
	public List<Device> getAssignedDevices()
	{
		return new ArrayList<Device>(assignedDevices);
	}
	
	@Synchronized("dbLock")
	public List<Device> getPreviousDevices()
	{
		return new ArrayList<Device>(previousDevices);
	}
	
	@Synchronized("dbLock")
	@SneakyThrows
	public Device assignDevice(@NonNull String name, @NonNull MacAddress macAddress) throws MacAlreadyAssignedException
	{
		for (DBUserDevice device :
				db.userDeviceDao.queryBuilder()
						.where().eq("MAC_Address", macAddress)
						.and().isNull("Assigned_To")
						.and().eq("User_ID", this.getId())
						.query())
		{
			throw new MacAlreadyAssignedException(db.getUserByID(device.getUser().getId()), macAddress);
		}
		Device device = new Device(db, this, name, macAddress);
		assignedDevices.add(device);
		updateFirewallRules();
		return device;
	}
	
	public static class MacAlreadyAssignedException extends Exception
	{
		private static final long	serialVersionUID	= 5825104476342522994L;
		
		@Getter
		private final User			user;
		@Getter
		private final MacAddress	macAddress;
		
		public MacAlreadyAssignedException(User user, MacAddress macAddress)
		{
			super("MAC Address " + macAddress.toString() + " is already assigned to user \"" + user.getFullName()
					+ "\" (ID: " + user.getId() + ")");
			this.user = user;
			this.macAddress = macAddress;
		}
	}
	
	public class Device extends DatabaseObject<DBUserDevice>
	{
		private final Object	dbLockInt	= dbLock;
		@Getter
		private final User		user;
		
		private Device(@SuppressWarnings("unused") @NonNull Database db, @NonNull User user, DBUserDevice device)
		{
			super(db, db.userDeviceDao, device);
			
			this.user = user;
		}
		
		private Device(Database db, @NonNull User user, @NonNull String name, MacAddress macAddress)
		{
			this(db, user, new DBUserDevice());
			
			dbObject.setName(name);
			dbObject.setMacAddress(macAddress);
			dbObject.setUser(user.dbObject);
			
			synchronized (db)
			{
				try
				{
					dao.create(dbObject);
					dao.refresh(dbObject); // Workaround to load the DB-generated AssignedFrom
				}
				catch (SQLException e)
				{
					throw new RuntimeException(e);
				}
			}
		}
		
		public MacAddress getMacAddress()
		{
			return dbObject.getMacAddress();
		}
		
		public String getName()
		{
			return dbObject.getName();
		}
		
		public void setName(@NonNull String name)
		{
			dbObject.setName(name);
			update();
		}
		
		public Instant getAssignedFrom()
		{
			return dbObject.getAssignedFrom();
		}
		
		public Instant getAssignedTo()
		{
			return dbObject.getAssignedTo();
		}
		
		public boolean isAssigned()
		{
			return dbObject.getAssignedTo() == null;
		}
		
		@Synchronized("dbLockInt")
		public void unassign()
		{
			if (!isAssigned()) { return; }
			dbObject.setAssignedTo(Instant.now());
			update();
			assignedDevices.remove(this);
			previousDevices.add(this);
			updateFirewallRules();
		}
		
		@Override
		public int getId()
		{
			return dbObject.getId();
		}
	}
	
	/**
	 * ******************************************
	 * ********** USER IPV4 ASSIGNMENT **********
	 * ******************************************
	 */

	public boolean isAdblock() { return dbObject.isAdblock();	}

	@Synchronized("dbLock")
	public void setAdblock(boolean adblock)
	{
		dbObject.setAdblock(adblock);
		update();
		updateFirewallRules();
	}

	public boolean isNatIPv4Dynamic() { return dbObject.isNatIPv4Dynamic();	}

	@Synchronized("dbLock")
	public void setNatIPv4Dynamic(boolean natIPv4Dynamic)
	{
		dbObject.setNatIPv4Dynamic(natIPv4Dynamic);
		update();
	}
	
	/**
	 * @return The NAT IPv4 assigned to this user, or <code>null</code> if none is assigned.
	 */
	public Inet4Address getAssignedNatIPv4()
	{
		return getAssignedNatIPv4DB() == null ? null : getAssignedNatIPv4DB().getAddress();
	}

	/**
	 * @return The NAT IPv4 assigned to this user, or <code>null</code> if none is assigned.
	 */
	@Synchronized("dbLock")
	DBIPv4Address getAssignedNatIPv4DB()
	{
		if (dbObject.getNatIPv4Assignment() == null) { return null; }
		return dbObject.getNatIPv4Assignment().getAddress();
	}
	
	/**
	 * Assigns a new random IPv4 to this user. If he is currently assigned an IPv4, it will be
	 * unassigned first.
	 * 
	 * @return The newly assigned IPv4
	 * @throws NoFreeIPv4Exception
	 *             If no free IPv4 is available.
	 */
	@SneakyThrows(IPAlreadyAssignedException.class)
	public Inet4Address assignNatIPv4() throws NoFreeIPv4Exception {
		DBIPv4Address newNatIPv4 = db.getFreeIPv4();
		if (newNatIPv4 == null) { throw new NoFreeIPv4Exception(); }
		assignNatIPv4(newNatIPv4, true);
		return newNatIPv4.getAddress();
	}

	@Synchronized("dbLock")
	@SneakyThrows(SQLException.class)
	void assignNatIPv4(@NonNull DBIPv4Address newNatIPv4, boolean updateFirewallRules) throws IPAlreadyAssignedException
	{
		if(newNatIPv4.getAssignment() != null) { throw new IPAlreadyAssignedException(); }
		if (getAssignedNatIPv4() != null)
		{
			unassignNatIPv4();
		}

		// Create the assignment between the IPv4 and this user
		DBUserIPv4Assignment assignment = new DBUserIPv4Assignment();
		assignment.setAddress(newNatIPv4);
		assignment.setUser(dbObject);
		db.userIPv4Dao.create(assignment);
		db.userIPv4Dao.refresh(assignment); // Workaround to load the DB-generated AssignedFrom
		// Create the link in this user's profile to his IPv4 assignment
		dbObject.setNatIPv4Assignment(assignment);
		update();
		// And let the IPv4 itself know that it has an active assignment
		newNatIPv4.setAssignment(assignment);
		db.IPv4Dao.update(newNatIPv4);
		
		if(updateFirewallRules)
		{
			updateFirewallRules();
		}
	}
	
	public void unassignNatIPv4() { unassignNatIPv4(true); }

	@Synchronized("dbLock")
	@SneakyThrows
	void unassignNatIPv4(boolean updateFirewallRules)
	{
		if (getAssignedNatIPv4() == null) { return; }
		// Store the time at which the assignment ended
		DBUserIPv4Assignment assignment = dbObject.getNatIPv4Assignment();
		assignment.setAssignedTo(Instant.now());
		db.userIPv4Dao.update(assignment);
		// This user no longer has a NAT IPv4 assigned to him
		dbObject.setNatIPv4Assignment(null);
		update();
		// And the ipv4 no longer has an active assignment
		DBIPv4Address ipv4 = assignment.getAddress();
		ipv4.setAssignment(null);
		db.IPv4Dao.update(ipv4);
		if(updateFirewallRules)
		{
			updateFirewallRules();
		}
	}
	
	public static class NoFreeIPv4Exception extends Exception
	{
		private static final long serialVersionUID = -5366222931762776625L;
	}

	public static class IPAlreadyAssignedException extends Exception
	{
		private static final long serialVersionUID = -5366222931762776525L;
	}
	
	/**
	 * *******************************************
	 * ********** AUTHENTICATION EVENTS **********
	 * *******************************************
	 */
	
	/**
	 * Retrieves the most recent authentication events.
	 * 
	 * @param limit
	 *            How many events to retrieve. Must not be negative
	 * @return The most recent authentication events, sorted by their time in descending order. This
	 *         list has at most <code>limit</code> entries.
	 */
	@SneakyThrows
	public List<AuthenticationEvent> listAuthEvent(int limit)
	{
		if (limit < 0) { throw new IllegalArgumentException("limit must not be negative"); }
		if (limit == 0) { return new ArrayList<AuthenticationEvent>(); }
		// Retrieving Authentication Events does not require synchronization, since it only performs a single SQL statement.
		QueryBuilder<DBAuthenticationEvent, Integer> qb = db.authEventDao.queryBuilder();
		qb.where().eq("User_ID", getId());
		qb.orderBy("Time", false);
		qb.limit((long) limit);
		return qb.query().stream().map(dbAuthEvent -> new AuthenticationEvent(db, dbAuthEvent))
				.collect(Collectors.toList());
	}
	
	/**
	 * Checks if this user is able to login, and if not, provides the reason why he isn't.
	 * The following criteria are checked:
	 * <ul>
	 * <li>User not deleted</li>
	 * <li>User not deactivated</li>
	 * <li>User has an active room assignment OR has the permission {{@link Permission#LOGIN_WITHOUT_LEASE}}</li>
	 * </ul>
	 * 
	 * @return <code>AuthResult.OK</code> if the user is currently able to login, or another auth
	 *         result if at least one of the criteria above are not met.
	 */
	public AuthResult canLogin()
	{
		if (this.isDeleted())
		{
			return AuthResult.UNKNOWN_USER;
		}
		else if (this.isDeactivated())
		{
			return AuthResult.ACCOUNT_DEACTIVATED;
		}
		else if (this.getRoomAssignment() == null && !this.hasPermission(Permission.LOGIN_WITHOUT_LEASE))
		{
			return AuthResult.NO_LEASE;
		}
		else
		{
			return AuthResult.OK;
		}
	}
	
	@Synchronized("dbLock")
	private void updateFirewallRules()
	{
		if (this.getRoomAssignment() != null)
		{
			Room room = this.getRoomAssignment().getRoom();
			if (room.getCurrentUser().equals(this))
			{
				SeilnetMain.getFirewallManager().updateRules(room);
			}
		}
	}
	
	@Override
	public String toString()
	{
		return "[Usr:" + getId() + "] " + getFirstName() + " " + getLastName();
	}
	
	@Override
	public boolean equals(Object o)
	{
		return o instanceof User && ((User) o).dbObject.equals(dbObject) && ((User) o).db == db;
	}
	
	@Override
	public int hashCode()
	{
		return dbObject.getId();
	}
}
