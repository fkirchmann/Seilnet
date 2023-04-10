/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import java.net.Inet4Address;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import de.rwth.seilgraben.seilnet.main.SeilnetConfig;
import org.eclipse.jetty.http.HttpStatus;

import com.esotericsoftware.minlog.Log;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.firewall.shared.NetworkHostList.NetworkHost;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.MailSender;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.User.AssignmentAlreadyEndedException;
import de.rwth.seilgraben.seilnet.main.db.User.Device;
import de.rwth.seilgraben.seilnet.main.db.User.MacAlreadyAssignedException;
import de.rwth.seilgraben.seilnet.main.db.User.NoFreeIPv4Exception;
import de.rwth.seilgraben.seilnet.main.db.User.RoomAlreadyAssignedException;
import de.rwth.seilgraben.seilnet.main.db.User.RoomAssignment;
import de.rwth.seilgraben.seilnet.main.db.User.RoomHasSubTenantException;
import de.rwth.seilgraben.seilnet.main.db.User.RoomWithoutMainTenantException;
import de.rwth.seilgraben.seilnet.main.db.User.SubTenantExpiresAfterMainTenantException;
import de.rwth.seilgraben.seilnet.main.db.User.UserAlreadyAssignedException;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.main.web.pages.FormException.InvalidFieldValueException;
import de.rwth.seilgraben.seilnet.main.web.pages.FormException.MissingFieldException;
import de.rwth.seilgraben.seilnet.util.Func;
import de.rwth.seilgraben.seilnet.util.MacAddress;
import spark.Request;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class ViewUser extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/view_user");

		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user", (request, response) -> {
			response.redirect(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/" +
					request.queryParams("id"));
			return null;
		});
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/:id", viewRoute);
		Spark.post(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/:id", modifyValueRoute);
	}
	
	Route viewRoute = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		Messages msgs = new Messages();
		
		/**
		 * Get the user specified in the request.
		 */
		int userId = -1;
		try
		{
			userId = Integer.parseInt(request.params("id"));
		}
		catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		User user = getDb().getUserByID(userId);
		if (user == null)
		{
			Spark.halt(HttpStatus.NOT_FOUND_404);
		}
		
		/**
		 * Process form data
		 */
		processFormData(request, user, msgs, args);
		
		/**
		 * Show user details
		 */
		args.put("title", "viewUser");
		RoomAssignment assignment = user.getRoomAssignment();
		if (assignment == null)
		{
			args.put("titleParam", user.getFirstName() + " " + user.getLastName());
		}
		else
		{
			args.put("titleParam",
					assignment.getRoom().getRoomNumber() + " - " + user.getFirstName() + " " + user.getLastName());
			args.put("vlan", assignment.getRoom().getVlan());

			List<String> roomInfo = new ArrayList<>();
			roomInfo.add(assignment.getRoom().getRoomNumber());
			roomInfo.add(Constants.DATE_FORMATTER.format(assignment.getExpiration()));

			if (!assignment.isSubtenant())
			{
				User roomTenant = assignment.getRoom().getCurrentUser();
				if (!roomTenant.equals(user))
				{
					List<String> subTenantInfo = new ArrayList<>();
					subTenantInfo.add(getLink(roomTenant));
					subTenantInfo.add(roomTenant.getFullName());
					subTenantInfo.add(Constants.DATE_FORMATTER.format(roomTenant.getRoomAssignment().getExpiration()));
					args.put("subTenantInfo", subTenantInfo);
				}
				args.put("roomInfoType", "roomInfoMain");
			}
			else
			{
				args.put("roomInfoType", "roomInfoSub");
				User mainTenant = assignment.getRoom().getMainTenant();
				roomInfo.add(ViewUser.getLink(mainTenant));
				roomInfo.add(mainTenant.getFullName());
			}
			args.put("roomInfo", roomInfo);

			// Lists all hosts in the network and then removes those that have already been registered
			args.put("unregisteredDevices", SeilnetMain.getFirewallClient().getHosts()
					.listVlanHosts(assignment.getRoom().getVlan()).stream()
					.filter(networkHost -> user.getAssignedDevices().stream().noneMatch(device ->
							device.getMacAddress().equals(networkHost.getMacAddress())))
					.collect(Collectors.toList()));
		}
		// Determine status of account
		if (user.isDeactivated())
		{
			args.put("statusButtonClass", "danger");
			args.put("status", "statusDisabled");
		}
		else
		{
			if (user.getRoomAssignment() == null)
			{
				args.put("statusButtonClass", "warning");
				args.put("status", "statusNoLease");
			}
			else if (!user.getRoomAssignment().getRoom().getCurrentUser().equals(user))
			{
				args.put("statusButtonClass", "warning");
				args.put("status", "statusSubleased");
			}
			else
			{
				args.put("statusButtonClass", "success");
				args.put("status", "statusEnabled");
			}
		}
		
		args.put("firstName", user.getFirstName());
		args.put("lastName", user.getLastName());
		args.put("email", user.getEmail());
		args.put("phone", user.getPhone() == null ? "" : user.getPhone());
		args.put("birthday", user.getBirthday() == null ? "" : user.getBirthday().format(Constants.DATE_FORMATTER));
		args.put("matriculationNumber", user.getMatriculationNumber() == null ? "" : user.getMatriculationNumber());
		args.put("timUsername", user.getTimUsername() == null ? "" : user.getTimUsername());
		args.put("comments", user.getComments() == null ? "" : user.getComments());
		Inet4Address addr = user.getAssignedNatIPv4();
		if (addr != null)
		{
			args.put("ipAddress", addr.getHostAddress());
		}
		args.put("dynamicIP", user.isNatIPv4Dynamic());
		if(SeilnetMain.getConfig().getAdblockDnsServer() != null) {
			args.put("adblock", user.isAdblock());
		}

		args.put("wlanPassword", user.getWlanPassword());
		args.put("printWifiLink", PrintUserInfo.getLinkWifi(user));
		args.put("assignedDevices", user.getAssignedDevices());
		args.put("previousDevices", user.getPreviousDevices());
		args.put("printDevicesLink", PrintUserInfo.getLinkDevices(user));
		
		/**
		 * Display additional forms, if requested
		 */
		if (request.queryParams("showForm") != null)
		{
			args.put("showForm", request.queryParams("showForm"));
		}
		
		msgs.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
	
	private void processFormData(Request request, User user, Messages msgs, Map<String, Object> args)
	{
		String form = request.queryParams("doForm");
		if (form == null || !request.requestMethod().equals("POST")) { return; }
		
		switch (form)
		{
			case "createLease":
			{
				try
				{
					args.put("showForm", "createLease");
					createLease(request, user);
					// If the lease creation was successful, then don't show the lease creation form again.
					args.remove("showForm");
				}
				catch (UserAlreadyAssignedException e)
				{
					msgs.addError("strings", "alreadyHasLease");
				}
				catch (MissingFieldException e)
				{
					msgs.addError("strings", "missingField");
				}
				catch (InvalidFieldValueException e)
				{
					msgs.addError("strings", e.messageName);
				}
				catch (RoomWithoutMainTenantException e)
				{
					msgs.addError("strings", "roomWithoutMainTenant");
				}
				catch (SubTenantExpiresAfterMainTenantException e)
				{
					synchronized (getDb())
					{
						User mainTenant = e.getRoom().getMainTenant();
						msgs.addError("strings", "earlySubLeaseExpirationInfo").addParam(ViewUser.getLink(mainTenant))
								.addParam(Constants.DATE_FORMATTER
										.format(mainTenant.getRoomAssignment().getExpiration()));
					}
				}
				catch (RoomAlreadyAssignedException e)
				{
					User currentUser = e.getRoom().getCurrentUser();
					if (currentUser.getRoomAssignment().isSubtenant())
					{
						msgs.addError("strings", "roomAlreadyAssignedSubTenant").addParam(ViewUser.getLink(currentUser))
								.addParam(currentUser.getFullName());
					}
					else
					{
						msgs.addError("strings", "roomAlreadyAssignedMainTenant")
								.addParam(ViewUser.getLink(currentUser)).addParam(currentUser.getFullName());
					}
				}
				catch (NoFreeIPv4Exception e)
				{
					msgs.addError("strings", "noFreeIPv4");
				}
				break;
			}
			case "endLease":
			{
				try
				{
					if (user.getRoomAssignment() == null) { throw new AssignmentAlreadyEndedException(); }
					user.getRoomAssignment().endNow();
				}
				catch (AssignmentAlreadyEndedException e)
				{
					msgs.addError("strings", "noActiveLease");
				}
				catch (RoomHasSubTenantException e)
				{
					msgs.addError("strings", "leaseUnremovableSubtenant");
				}
				break;
			}
			case "toggleEnabled":
			{
				user.setDeactivated(!user.isDeactivated());
				break;
			}
			case "newNatIPv4":
			{
				try
				{
					user.assignNatIPv4();
				}
				catch (NoFreeIPv4Exception e)
				{
					msgs.addError("strings", "noFreeIPv4");
				}
				break;
			}
			case "toggleDynamicIPv4":
			{
				synchronized (getDb()) {
					user.setNatIPv4Dynamic(!user.isNatIPv4Dynamic());
				}
				break;
			}
			case "toggleAdblock":
			{
				synchronized (getDb()) {
					user.setAdblock(!user.isAdblock());
				}
				break;
			}
			case "addDevice":
			{
				try
				{
					user.assignDevice(request.queryParams("name"), new MacAddress(request.queryParams("macAddress")));
				}
				catch (MacAlreadyAssignedException e)
				{
					msgs.addError("strings", "macAlreadyAssigned");
				}
				catch (NullPointerException e)
				{
					msgs.addError("strings", "missingField");
				}
				catch (IllegalArgumentException e)
				{
					msgs.addError("strings", "invalidMacAddress");
				}
				break;
			}
			case "removeDevice":
			{
				boolean success = false;
				int id = Integer.parseInt(request.queryParams("id"));
				for (Device d : user.getAssignedDevices())
				{
					if (id == d.getId())
					{
						d.unassign();
						success = true;
					}
				}
				if (!success)
				{
					msgs.addError("strings", "deviceNotFound");
				}
				break;
			}
			case "newWlanPassword":
			{
				user.setWlanPassword(
						Func.generateRandomString(Constants.RADIUS_PASSWORD_LENGTH, Constants.RADIUS_PASSWORD_CHARSET));
				break;
			}
		}
	}
	
	public static String getLink(User user)
	{
		return Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/user/" + user.getId();
	}
	
	Route modifyValueRoute = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		// If this is a submitted HTML form and not a background AJAX request,
		// use the other route instead.
		if (request.queryParams("doForm") != null) { return viewRoute.handle(request, response); }
		
		// Assume error status to return error messages in one line
		// At the end, if the request is okay, we change it back to 200
		response.status(400);
		
		int userId;
		try
		{
			userId = Integer.parseInt(request.params("id"));
		}
		catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
		{
			return i18n("strings", "invalidUserId", getLocale(request));
		}
		User user = getDb().getUserByID(userId);
		if (user == null) { return i18n("strings", "unknownUserId", getLocale(request)); }
		
		String name = request.queryParams("name");
		String value = request.queryParams("value");

		// Assumption: empty fields not acceptable (except for the phone number, matriculation & TIM)
		if( !"phone".equals(name)
			&& !"matriculationNumber".equals(name)
			&& !"timUsername".equals(name)
			&& !"comments".equals(name)
			&& (value == null || value.trim().isEmpty())) {	return i18n("strings", "fieldRequired", getLocale(request)); }
		try
		{
			switch (name)
			{
				case "firstName":
				{
					user.setFirstName(value);
					break;
				}
				case "lastName":
				{
					user.setLastName(value);
					break;
				}
				case "email":
				{
					if (!MailSender.isEmailValid(value)) { return i18n("strings", "invalidEmail", getLocale(request)); }
					user.setEmail(value);
					break;
				}
				case "phone":
				{
					user.setPhone(value);
					break;
				}
				case "birthday":
				{
					LocalDate birthday = LocalDate.parse(value, Constants.DATE_FORMATTER);
					// Birthdays must not be in the future
					if (birthday.compareTo(
							LocalDate.now()) > 0) { return i18n("strings", "futureBirthday", getLocale(request)); }
					user.setBirthday(birthday);
					break;
				}
				case "matriculationNumber":
				{
					user.setMatriculationNumber(value);
					break;
				}
				case "timUsername":
				{
					user.setTimUsername(value);
					break;
				}
				case "leaseExpiration":
				{
					LocalDate leaseExpirationDate = LocalDate.parse(value, Constants.DATE_FORMATTER);
					Instant leaseExpiration = leaseExpirationDate.atTime(Constants.DAILY_EXPIRY_TIME)
							.atZone(ZoneId.systemDefault()).toInstant();
					// The lease expiration date must be after the current date
					if (leaseExpirationDate.compareTo(LocalDate.now()) <= 0) { return i18n("strings",
							"earlyLeaseExpiration", getLocale(request)); }
					synchronized (getDb())
					{
						try
						{
							user.getRoomAssignment().setExpiration(leaseExpiration);
						}
						catch (SubTenantExpiresAfterMainTenantException e)
						{
							return i18n("strings", "earlySubLeaseExpiration", getLocale(request));
						}
					}
					break;
				}
				case "comments":
				{
					user.setComments(value);
					break;
				}
				case "deviceName":
				{
					String deviceId = request.queryParams("pk");

					synchronized (getDb())
					{
						Optional<Device> deviceOptional = user.getAssignedDevices().stream()
								.filter(device -> Integer.toString(device.getId()).equals(deviceId))
								.findFirst();
						if(!deviceOptional.isPresent()) { return i18n("strings", "deviceNotFound", getLocale(request)); }
						deviceOptional.get().setName(value);
					}
					break;
				}
				default:
				{
					return i18n("strings", "unknownField", getLocale(request));
				}
			}
		}
		catch (DateTimeParseException e)
		{
			return i18n("strings", "wrongDateFormat", getLocale(request));
		}
		
		response.status(200);
		return "";
	};
	
	private void createLease(Request request, User user) throws MissingFieldException, InvalidFieldValueException,
			UserAlreadyAssignedException, RoomAlreadyAssignedException, RoomWithoutMainTenantException,
			SubTenantExpiresAfterMainTenantException, NoFreeIPv4Exception
	{
		String roomNumber = request.queryParams("roomNr");
		String subtenant = request.queryParams("subtenant");
		String leaseExpiration = request.queryParams("leaseExpiration");
		/*
		 * Ensure that mandatory fields are present
		 */
		MissingFieldException.requireFields(roomNumber, leaseExpiration, subtenant);
		/*
		 * Parse lease expiration
		 */
		Instant leaseExpirationInstant = null;
		if (leaseExpiration != null)
		{
			try
			{
				LocalDate leaseExpirationDate = LocalDate.parse(leaseExpiration, Constants.DATE_FORMATTER);
				leaseExpirationInstant = leaseExpirationDate.atTime(Constants.DAILY_EXPIRY_TIME)
						.atZone(ZoneId.systemDefault()).toInstant();
				// The lease expiration date must be after the current date
				if (leaseExpirationDate.compareTo(
						LocalDate.now()) <= 0) { throw new InvalidFieldValueException("earlyLeaseExpiration"); }
			}
			catch (DateTimeParseException e)
			{
				throw new InvalidFieldValueException("invalidLeaseExpiration");
			}
		}
		/*
		 * Parse subtenant field
		 */
		boolean subtenantBool = Boolean.parseBoolean(subtenant);
		
		synchronized (getDb())
		{
			/*
			 * Check room number
			 */
			Room room = getDb().getRoomByNumber(roomNumber);
			if (room == null) { throw new InvalidFieldValueException("unknownRoomNumber"); }
			user.assignRoom(room, leaseExpirationInstant, subtenantBool);
		}
	}
}
