/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.Constants.SupportedLanguage;
import de.rwth.seilgraben.seilnet.main.MailSender;
import de.rwth.seilgraben.seilnet.main.db.Database.EMailInUseException;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.User.NoFreeIPv4Exception;
import de.rwth.seilgraben.seilnet.main.db.User.RoomAlreadyAssignedException;
import de.rwth.seilgraben.seilnet.main.db.User.RoomWithoutMainTenantException;
import de.rwth.seilgraben.seilnet.main.db.User.SubTenantExpiresAfterMainTenantException;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import de.rwth.seilgraben.seilnet.main.web.pages.FormException.InvalidFieldValueException;
import de.rwth.seilgraben.seilnet.main.web.pages.FormException.MissingFieldException;
import spark.Request;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class CreateUser extends WebPage
{
	private PebbleTemplate template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("admin/create_user");
		
		Spark.get(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/users/create", route);
		Spark.post(Constants.PATH_PREFIX + Constants.ADMIN_PATH_PREFIX + "/users/create", route);
	}
	
	private static final String[]	fields	= new String[] { "firstName", "lastName", "email", "language",
			"phoneNumber", "birthday", "matriculationNumber", "roomNr", "leaseType", "leaseExpiration" };
			
	// @formatter:off
	Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.ADMIN)) { return ""; }
		
		Map<String, Object> args = new HashMap<>();
		
		if (!request.queryParams().isEmpty() && request.requestMethod().equalsIgnoreCase("POST"))
		{
			Messages msgs = new Messages();
			User user = null;
			try
			{
				user = createUser(request);
				msgs.addOk("strings", "userCreated").addParam(ViewUser.getLink(user))
						.addParam(user.getFirstName() + " " + user.getLastName());
			}
			catch (MissingFieldException e)
			{
				msgs.addError("strings", "missingField");
			}
			catch (InvalidFieldValueException e)
			{
				msgs.addError("strings", e.messageName);
			}
			catch (EMailInUseException e)
			{
				msgs.addError("strings", "emailAlreadyRegistered")
					.addParam(ViewUser.getLink(e.getEmailUsedBy()))
					.addParam(e.getEmailUsedBy().getFullName());
			}
			catch (RoomWithoutMainTenantException e)
			{
				msgs.addError("strings", "roomWithoutMainTenant");
			}
			catch (SubTenantExpiresAfterMainTenantException e)
			{
				synchronized(getDb())
				{
					User mainTenant = e.getRoom().getMainTenant();
					msgs.addError("strings", "earlySubLeaseExpirationInfo")
						.addParam(ViewUser.getLink(mainTenant))
						.addParam(Constants.DATE_FORMATTER.format(mainTenant.getRoomAssignment().getExpiration()));
				}
			}
			catch (RoomAlreadyAssignedException e)
			{
				User currentUser = e.getRoom().getCurrentUser();
				if(currentUser.getRoomAssignment().isSubtenant())
				{
					msgs.addError("strings", "roomAlreadyAssignedSubTenant")
						.addParam(ViewUser.getLink(currentUser))
						.addParam(currentUser.getFullName());
				}
				else
				{
					msgs.addError("strings", "roomAlreadyAssignedMainTenant")
					.addParam(ViewUser.getLink(currentUser))
					.addParam(currentUser.getFullName());
				}
			}
			catch (NoFreeIPv4Exception e)
			{
				msgs.addError("strings", "noFreeIPv4");
			}
			msgs.addToTemplateArgs(args);
			
			if (user == null)
			{
				/*
				 * If the user could not be created for some reason, this fills the fields with the previously entered data,
				 * so the admin doesn't have to enter everything again.
				 */
				for (String field : fields)
				{
					String value = request.queryParams(field);
					args.put(field, value == null ? "" : value);
				}
				// Some special cases for radio buttons
				if(request.queryParams("subtenant") != null)
				{
					args.put("subtenant_true", request.queryParams("subtenant").equals("true") ? "checked" : "");
					args.put("subtenant_false", request.queryParams("subtenant").equals("false") ? "checked" : "");
				}
				// And for the selected language
				if(request.queryParams("language") != null)
				{
					for (SupportedLanguage lang : Constants.SupportedLanguage.values())
					{
						args.put("language_" + lang.languageTag.replace('-', '_'),
							request.queryParams("language").equals(lang.languageTag) ? "checked" : "");
					}
				}
			}
		}
		
		return runTemplate(template, args, request);
	};
	// @formatter:on
	
	private User createUser(Request request)
			throws MissingFieldException, InvalidFieldValueException, EMailInUseException, RoomAlreadyAssignedException,
			RoomWithoutMainTenantException, SubTenantExpiresAfterMainTenantException, NoFreeIPv4Exception
	{
		String firstName = request.queryParams("firstName");
		String lastName = request.queryParams("lastName");
		String email = request.queryParams("email");
		String language = request.queryParams("language");
		String phone = request.queryParams("phoneNumber");
		String birthday = request.queryParams("birthday");
		String matriculationNumber = request.queryParams("matriculationNumber");
		String roomNumber = request.queryParams("roomNr");
		String subtenant = request.queryParams("subtenant");
		String leaseExpiration = request.queryParams("leaseExpiration");
		
		/*
		 * Ensure that mandatory fields are present
		 */
		MissingFieldException.requireFields(firstName, lastName, email, language, birthday, roomNumber, leaseExpiration,
				subtenant);
		/*
		 * Parse birthday
		 */
		LocalDate birthdayDate = null;
		try
		{
			birthdayDate = LocalDate.parse(birthday, Constants.DATE_FORMATTER);
			// Birthdays must not be in the future
			if (birthdayDate.compareTo(LocalDate.now()) > 0) { throw new InvalidFieldValueException("futureBirthday"); }
		}
		catch (DateTimeParseException e)
		{
			e.printStackTrace();
			throw new InvalidFieldValueException("invalidBirthday");
		}
		/*
		 * Check E-Mail address
		 */
		if (!MailSender.isEmailValid(email)) { throw new InvalidFieldValueException("invalidEmail"); }
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
		 * Parse user language
		 */
		SupportedLanguage lang = Constants.SupportedLanguage.fromLanguageTag(language);
		if (lang == null) { throw new InvalidFieldValueException("invalidLanguage"); }
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
			/*
			 * Everything ok, create user.
			 */
			User user = getDb().createUser(firstName, lastName, email, lang.locale, room, leaseExpirationInstant,
					subtenantBool);
			user.setBirthday(birthdayDate);
			if (phone != null && phone.trim().length() > 0)
			{
				user.setPhone(phone);
			}
			if (matriculationNumber != null && matriculationNumber.trim().length() > 0)
			{
				user.setMatriculationNumber(matriculationNumber);
			}
			return user;
		}
	}
	
}
