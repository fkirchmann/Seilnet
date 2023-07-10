/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.esotericsoftware.minlog.Log;
import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.zaxxer.hikari.HikariDataSource;
import de.rwth.seilgraben.seilnet.main.DailyTaskExecutor;
import de.rwth.seilgraben.seilnet.main.LogCategory;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.CacheSource;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.stmt.PreparedQuery;

import de.rwth.seilgraben.seilnet.main.SimpleTaskSchedulerService;
import de.rwth.seilgraben.seilnet.main.SimpleTaskSchedulerService.TaskIDRunnable;
import de.rwth.seilgraben.seilnet.main.db.User.NoFreeIPv4Exception;
import de.rwth.seilgraben.seilnet.main.db.User.RoomAlreadyAssignedException;
import de.rwth.seilgraben.seilnet.main.db.User.RoomWithoutMainTenantException;
import de.rwth.seilgraben.seilnet.main.db.User.SubTenantExpiresAfterMainTenantException;
import de.rwth.seilgraben.seilnet.main.db.User.UserAlreadyAssignedException;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthType;
import de.rwth.seilgraben.seilnet.main.db.orm.DBGroup;
import de.rwth.seilgraben.seilnet.main.db.orm.DBIPv4Address;
import de.rwth.seilgraben.seilnet.main.db.orm.DBRoom;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUser;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserDevice;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserGroupAssignment;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserIPv4Assignment;
import de.rwth.seilgraben.seilnet.main.db.orm.DBUserRoomAssignment;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

import javax.sql.DataSource;

/**
 *
 * @author Felix Kirchmann
 */
public class Database
{
	public Database(@NonNull final String host, @NonNull final String databaseName, @NonNull final String username,
			final String password) throws SQLException
	{
		/**
		 * NOTE: Both the connection source, the JDBC Driver and the Database must support threading
		 * / concurrent operations
		 */
		jdbcUrl = "jdbc:mysql://" + host + "/"
				+ databaseName + "?" + "user=" + username + ((password != null) ? ("&password=" + password) : "");

		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(jdbcUrl);
		this.dataSource = ds;

		try(Connection connection = ds.getConnection()) {
			Liquibase liquibase = new Liquibase(Constants.DB_CHANGELOG_CLASSPATH, new ClassLoaderResourceAccessor(),
					new JdbcConnection(connection));
			//liquibase.changeLogSync(null, new PrintWriter(System.out));
			liquibase.update((Contexts) null);
		} catch (LiquibaseException e) {
			throw new SQLException(e);
		}

		ConnectionSource connectionSource = new DataSourceConnectionSource(ds, jdbcUrl);

		userDao = DaoManager.createDao(connectionSource, DBUser.class);
		groupDao = DaoManager.createDao(connectionSource, DBGroup.class);
		userGroupDao = DaoManager.createDao(connectionSource, DBUserGroupAssignment.class);
		userIPv4Dao = DaoManager.createDao(connectionSource, DBUserIPv4Assignment.class);
		userDeviceDao = DaoManager.createDao(connectionSource, DBUserDevice.class);
		IPv4Dao = DaoManager.createDao(connectionSource, DBIPv4Address.class);
		roomDao = DaoManager.createDao(connectionSource, DBRoom.class);
		userRoomDao = DaoManager.createDao(connectionSource, DBUserRoomAssignment.class);
		authEventDao = DaoManager.createDao(connectionSource, DBAuthenticationEvent.class);
		
		CacheSource<Integer, User> userCacheSource = (id) -> {
			synchronized (this)
			{
				DBUser dbu = userDao.queryForId(id);
				if (dbu == null) { return null; }
				return new User(this, dbu);
			}
		};
		userCache = CacheBuilder.newCache(Integer.class, User.class).source(userCacheSource).build();

		groups = new HashMap<>();
		groupsByName = new HashMap<>();
		for (DBGroup dbGroup : groupDao.queryForAll())
		{
			Group group = new Group(this, dbGroup);
			groups.put(dbGroup.getId(), group);
			groupsByName.put(dbGroup.getName(), group);
		}
		
		roomExpiryService = new SimpleTaskSchedulerService(new TaskIDRunnable()
		{
			@Override
			public void run(int taskId)
			{
				Instant nextExpiration = userCache.get(taskId).getRoomAssignment().cleanup();
				if (nextExpiration != null)
				{
					roomExpiryService.schedule(taskId, nextExpiration);
				}
			}
		});

				rooms = new HashMap<>();
		roomsByNumber = new HashMap<>();
		roomUsers = new HashMap<>();
		for (DBRoom dbRoom : roomDao.queryForAll())
		{
			Room room = new Room(this, dbRoom);
			rooms.put(room.getId(), room);
			roomsByNumber.put(room.getRoomNumber(), room);
		}

		dynamicIPv4AssignmentExecutor
                //= new DailyTaskExecutor(LocalTime.now().plusSeconds(20),() -> {
                = new DailyTaskExecutor(Constants.DAILY_DYNAMIC_IP_CHANGE_TIME, () -> {
                    this.reassignDynamicNatIpv4s();
                    this.cleanup();
                });
		
		synchronized (this)
		{
			PreparedQuery<DBUserRoomAssignment> pquery = userRoomDao.queryBuilder().where().isNull("Assigned_To").and()
					.isNotNull("Expiration").prepare();
			for (DBUserRoomAssignment assignment : userRoomDao.query(pquery))
			{
				// TODO 
				roomExpiryService.schedule(assignment.getUser().getId(), assignment.getExpiration());
			}
		}
	}
	protected final String						jdbcUrl;
	protected final DataSource					dataSource;
	
	final Cache<Integer, User>					userCache;
	final Map<Integer, Group>					groups;
	final Map<String, Group>					groupsByName;
	final Map<Integer, Room>					rooms;
	final Map<String, Room>						roomsByNumber;
	final Map<Room, User>						roomUsers;
	
	final Dao<DBUser, Integer>					userDao;
	final Dao<DBGroup, Integer>					groupDao;
	final Dao<DBUserGroupAssignment, Integer>	userGroupDao;
	final Dao<DBUserIPv4Assignment, Integer>	userIPv4Dao;
	final Dao<DBUserDevice, Integer>			userDeviceDao;
	final Dao<DBIPv4Address, Integer>			IPv4Dao;
	final Dao<DBRoom, Integer>					roomDao;
	final Dao<DBUserRoomAssignment, Integer>	userRoomDao;
	final Dao<DBAuthenticationEvent, Integer>	authEventDao;
	
	final SimpleTaskSchedulerService			roomExpiryService;
	final DailyTaskExecutor						dynamicIPv4AssignmentExecutor;

	/**
	 * Must only be used within synchronzed {}
	 */
	final Random random							= new SecureRandom();

	/**
	 * Executed on startup. Remember to comment out its contents for production!
	 */
	synchronized public void test() throws SQLException
	{
		/*User user = null;
		try
		{
			user = createUser("Default", "Admin", "admin@localhost", new Locale("en", "US"));
			user.setWebPassword("admin");
		}
		catch (EMailInUseException e1)
		{
			user = getUserByEmail("admin@localhost");
		}*/
		
		/*if (getFreeIPv4() == null)
		{
			try
			{
				List<byte[]> ips = new ArrayList<byte[]>();
				ips.add(new byte[] { 127, 0, 0, 1 });
				ips.add(new byte[] { 1, 2, 3, 4 });
				ips.add(new byte[] { 5, 6, 7, 8 });
				
				for (byte[] ip : ips)
				{
					DBIPv4Address a = new DBIPv4Address();
					a.setAddress((Inet4Address) InetAddress.getByAddress(ip));
					IPv4Dao.create(a);
				}
			}
			catch (UnknownHostException e)
			{
				throw new RuntimeException(e);
			}
		}*/
	}
	
	public User getUserByID(int id)
	{
		return userCache.get(id);
	}

	@SneakyThrows
	synchronized public User getUserByMacAddress(@NonNull MacAddress macAddress)
	{
		GenericRawResults<String[]> results = userDeviceDao.queryRaw(
				"SELECT User_ID FROM User_Devices WHERE MAC_Address = ?" +
				" AND Assigned_To is NULL" +
				" ORDER BY Assigned_From DESC" +
				" LIMIT 1", macAddress.toString());
		try {
			String[] firstResult = results.getFirstResult();
			if (firstResult == null || firstResult.length == 0) {
				return null;
			}
			return getUserByID(Integer.parseInt(firstResult[0]));
		} finally {
			results.close();
		}
	}

	@SneakyThrows
	synchronized public User getUserByEmail(String email)
	{
		Map<String, Object> queryFields = new HashMap<>();
		queryFields.put("Email", email);
		List<DBUser> results = userDao.queryForFieldValuesArgs(queryFields);
		if (results.isEmpty()) { return null; }
		if (results.size() > 1) { throw new SQLException(
				"Multiple users with the same E-Mail \"" + email + "\" exist in the database"); }
		DBUser dbu = results.get(0);
		userCache.putIfAbsent(dbu.getId(), new User(this, dbu));
		return userCache.get(dbu.getId());
	}
	
	synchronized public User createUser(@NonNull String firstName, @NonNull String lastName, @NonNull String email,
			@NonNull Locale locale) throws EMailInUseException
	{
		User user = new User(this, firstName, lastName, email, locale);
		userCache.put(user.getId(), user);
		return userCache.get(user.getId());
	}
	
	synchronized public User createUser(@NonNull String firstName, @NonNull String lastName, @NonNull String email,
			@NonNull Locale locale, @NonNull Room room, Instant leaseExpiration, boolean subtenant)
			throws EMailInUseException, RoomAlreadyAssignedException, RoomWithoutMainTenantException,
			SubTenantExpiresAfterMainTenantException, NoFreeIPv4Exception
	{
		if (this.getFreeIPv4() == null) { throw new NoFreeIPv4Exception(); }
		User currentUser = room.getCurrentUser();
		if ((currentUser != null && currentUser.getRoomAssignment().isSubtenant())
				|| (currentUser != null && !subtenant)) { throw new RoomAlreadyAssignedException(room, currentUser); }
		if (currentUser == null && subtenant) { throw new RoomWithoutMainTenantException(room); }
		
		if (subtenant)
		{
			User mainTenant = room.getMainTenant();
			Instant mainTenantExpiration = mainTenant.getRoomAssignment().getExpiration();
			if (mainTenantExpiration
					.compareTo(leaseExpiration) <= 0) { throw new SubTenantExpiresAfterMainTenantException(room,
							mainTenant, mainTenantExpiration); }
		}
		
		User user = new User(this, firstName, lastName, email, locale);
		userCache.put(user.getId(), user);
		try
		{
			user.assignRoom(room, leaseExpiration, subtenant);
		}
		catch (RoomAlreadyAssignedException | UserAlreadyAssignedException e)
		{
			throw new RuntimeException(e);
		}
		return userCache.get(user.getId());
	}
	
	/**
	 * Performs a fulltext search for users in the database. Only the ID and full names of found
	 * users are returned, this improves performance since it does not require entire {@link User}
	 * objects (with several foreign fields) to be retrieved from the database. Additionally, if a
	 * valid room number is entered as the query, then the room's tenant is included as the first
	 * result.
	 * 
	 * @param nameQuery
	 *            The search query.
	 * @return A Map with one entry per search result. The key is the user's ID, the password the
	 *         user's full name.
	 */
	@SneakyThrows
	public Map<Integer, String> searchUsers(String nameQuery)
	{
		String[] split = nameQuery.split(Pattern.quote(" "));
		StringBuilder query = new StringBuilder();
		for (int i = 0; i < split.length; i++)
		{
			query.append('+'); // Prefixing each word with a + will require every word of the search query to be present in a result
			query.append(split[i]);
			query.append('*'); // Appending an * to the query allows a keyword like "Ayu" to find the user "Ayudita"
			if (i < split.length - 1)
			{
				query.append(' '); // Only add a space if there is another keyword after this one
			}
		}
		GenericRawResults<String[]> results = userDao.queryRaw("SELECT Id, First_Name, Last_Name FROM Users "
				+ "WHERE MATCH(First_Name,Last_Name) AGAINST (? IN BOOLEAN MODE)", query.toString());
		Map<Integer, String> searchResults = new HashMap<>();
		try {
			synchronized (this) {
				Room room = getRoomByNumber(nameQuery.trim());
				if (room != null) {
					User tenant = room.getCurrentUser();
					if (tenant != null) {
						User main = room.getMainTenant();
						if (!main.equals(tenant)) {
							searchResults.put(main.getId(), main.getFullName());
						}
						searchResults.put(tenant.getId(), tenant.getFullName());
					}

				}
			}
			for (String[] result : results) {
				searchResults.put(Integer.parseInt(result[0]), result[1] + " " + result[2]);
			}
		} finally {
			results.close();
		}
		return searchResults;
	}
	
	synchronized public Group getGroupByID(int id)
	{
		return groups.get(id);
	}
	
	synchronized public Group getGroupByName(@NonNull String name)
	{
		return groupsByName.get(name);
	}
	
	synchronized public Group createGroup(@NonNull String name) throws GroupNameInUseException
	{
		Group group = new Group(this, name);
		groups.put(group.getId(), group);
		groupsByName.put(group.getName(), group);
		return group;
	}
	
	synchronized public List<Group> listGroups()
	{
		return new ArrayList<Group>(groups.values());
	}
	
	synchronized public Room getRoomByID(int id)
	{
		return rooms.get(id);
	}
	
	synchronized public Room getRoomByNumber(String roomNumber)
	{
		return roomsByNumber.get(roomNumber);
	}
	
	synchronized public Room createRoom(@NonNull String roomNumber) throws RoomNumberInUseException
	{
		Room room = new Room(this, roomNumber);
		rooms.put(room.getId(), room);
		roomsByNumber.put(room.getRoomNumber(), room);
		return room;
	}
	
	synchronized public Set<Room> listRooms()
	{
		return new HashSet<Room>(rooms.values());
	}
	
	@SneakyThrows
	public void logAuthEvent(User user, @NonNull String clientInfo, @NonNull AuthType type, @NonNull AuthResult result)
	{
		DBAuthenticationEvent event = new DBAuthenticationEvent();
		if (user != null)
		{
			event.setUser(user.dbObject);
		}
		event.setTime(Instant.now());
		event.setClientInfo(clientInfo);
		event.setAuthType(type);
		event.setAuthResult(result);
		// Does not need to be synchronized, since it is one single SQL INSERT with no side-effects
		authEventDao.create(event);
	}
	
	public static class EMailInUseException extends Exception
	{
		private static final long	serialVersionUID	= -130686853946443672L;
		
		@Getter
		private final User			emailUsedBy;
		
		public EMailInUseException(User emailUsedBy)
		{
			this.emailUsedBy = emailUsedBy;
		}
	}
	
	public static class GroupNameInUseException extends Exception
	{
		private static final long	serialVersionUID	= -130686853946443672L;
		
		@Getter
		private final Group			groupNameUsedBy;
		
		public GroupNameInUseException(Group groupNameUsedBy)
		{
			this.groupNameUsedBy = groupNameUsedBy;
		}
	}
	
	public static class RoomNumberInUseException extends Exception
	{
		private static final long	serialVersionUID	= 3776550473171293343L;
		
		@Getter
		private final Room			roomNumberUsedBy;
		
		public RoomNumberInUseException(Room roomNumberUsedBy)
		{
			this.roomNumberUsedBy = roomNumberUsedBy;
		}
	}
	
	public static class DatabaseObjectDeletedException extends RuntimeException
	{
		private static final long serialVersionUID = 2329429593157000551L;
	}
	
	/**
	 * @return A DBIPv4Address that is not currently assigned to any user, or <code>null</code> if
	 *         no such address exists.
	 */
	public synchronized DBIPv4Address getFreeIPv4()
	{
		List<DBIPv4Address> freeIPv4s = listFreeIPv4();
		if (freeIPv4s.isEmpty()) { return null; }
		return freeIPv4s.get(ThreadLocalRandom.current().nextInt(0, freeIPv4s.size()));
	}

	@SneakyThrows(SQLException.class)
	private synchronized List<DBIPv4Address> listFreeIPv4()
	{
		return IPv4Dao.queryBuilder().where().isNull("Current_Assignment_ID").and()
				.eq("Deleted", false).query();
	}

	@SneakyThrows(User.IPAlreadyAssignedException.class)
	private synchronized void reassignDynamicNatIpv4s()
	{
		Log.debug(LogCategory.DB, "Starting IPv4 Reassignment");
		List<User> users = listRooms().stream()
				.map(Room::getCurrentUser)
				.filter(user -> user != null && user.getAssignedNatIPv4() != null && user.isNatIPv4Dynamic())
				.collect(Collectors.toList());
		Log.trace(LogCategory.DB, users.size() + " users need a new IP");
		if(users.size() == 0) { return; } // Nothing to do.

		List<DBIPv4Address> newIPs = Stream.concat(
				users.stream().map(User::getAssignedNatIPv4DB),
				listFreeIPv4().stream()
			).collect(Collectors.toList());

		Log.trace(LogCategory.DB, "Calculating new IP assignment");
		boolean everyUserHasNewIP;
		do
		{
			Collections.shuffle(newIPs, random);

			everyUserHasNewIP = true;
			for(int i = 0; i < users.size(); i++)
			{
				if(users.get(i).getAssignedNatIPv4DB().getId() == newIPs.get(i).getId())
				{
					everyUserHasNewIP = false;
					break;
				}
			}
		} while(!everyUserHasNewIP);

		Log.trace(LogCategory.DB, "Reassigning IPs");
		for(int i = 0; i < users.size(); i++) {
			users.get(i).unassignNatIPv4(false);
		}
		for(int i = 0; i < users.size(); i++) {
			users.get(i).assignNatIPv4(newIPs.get(i), false);
		}
		Log.trace(LogCategory.DB, "Triggering rules update");
		SeilnetMain.getFirewallManager().updateAllRules();
		Log.debug(LogCategory.DB, "Dynamic IPv4 Reassignment Done");
	}

	private void cleanup()
	{
		try
		{
		    // This need not be synchronized, as expired IPv4
		    List<DBUserIPv4Assignment> assignmentsToDelete = this.userIPv4Dao.queryBuilder().where()
					.isNotNull("Assigned_To")
					.and()
					.le("Assigned_To",
							Instant.now().minus(SeilnetMain.getConfig().getOldIpRetentionDays(), ChronoUnit.DAYS))
					.query();

			synchronized (this) {
                // Filter assignmentsToDelete to not remove assignemts from users with UNLIMITED_DATA_RETENTION
                new ArrayList<>(assignmentsToDelete).stream()
                        .map(assignment -> assignment.getUser().getId())
                        .distinct()
                        .map(this::getUserByID)
                        .filter(user -> user.hasPermission(Permission.UNLIMITED_DATA_RETENTION))
                        .map(User::getId)
                        .forEach(user -> {
                            assignmentsToDelete.removeIf(assignment -> assignment.getUser().getId() == user);
                        });
            }

            Log.debug(LogCategory.DB, "Cleaning up " + assignmentsToDelete.size() + " old IPv4 assignments");
			assignmentsToDelete.forEach(assignment ->
                    Log.trace(LogCategory.DB, " -> deleting " + assignment.getId() + " (of user "
                            + assignment.getUser().getId() + ": "
                            + assignment.getUser().getFirstName() + " " + assignment.getUser().getLastName() + ") "
                            + assignment.getAddress().getAddress().toString() + " : "
                            + assignment.getAssignedFrom() + " - " + assignment.getAssignedTo()
                    ));

            userIPv4Dao.delete(assignmentsToDelete);
            Log.debug(LogCategory.DB,"Old IPv4 assignments cleanup complete.");

			/*
			TODO: Delete old users

			this.userDao.queryBuilder()
					.where().isNull("Room_Assignment_ID")
					.query().stream()
					.map(dbUser -> getUserByID(dbUser.getId()))
					.filter(user -> user.listGroups().isEmpty());
			this.userGroupDao.queryBuilder().where().eq("User_ID", 1234).query().isEmpty();*/
		}
		catch(SQLException e) {
			Log.warn(LogCategory.DB, "Database cleanup failed", e);
		}
	}
	
	public abstract static class DatabaseObject<T>
	{
		protected final Database		db;
		protected final Dao<T, Integer>	dao;
		protected final T				dbObject;
		
		protected DatabaseObject(@NonNull Database db, @NonNull Dao<T, Integer> dao, T dbObject)
		{
			this.db = db;
			this.dao = dao;
			this.dbObject = dbObject;
		}
		
		public abstract int getId();
		
		@SneakyThrows
		protected void update()
		{
			// TODO (performance): figure out how to do batch updates
			// i.e. change multiple fields and then do a final SQL UPDATE, instead of one UPDATE per field
			synchronized (db)
			{
				dao.update(dbObject);
			}
		}
		
		@Override
		public boolean equals(Object o)
		{
			return o instanceof DatabaseObject && ((DatabaseObject<?>) o).dbObject.equals(dbObject)
					&& ((DatabaseObject<?>) o).db == db;
		}
		
		@Override
		public int hashCode()
		{
			return getId();
		}
	}
}
