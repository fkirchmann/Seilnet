/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.mail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.mail.EmailException;
import org.eclipse.jetty.http.HttpStatus;

import com.esotericsoftware.minlog.Log;
import com.google.gson.Gson;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.LogCategory;
import de.rwth.seilgraben.seilnet.main.MailSender.Mail;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.db.Database;
import de.rwth.seilgraben.seilnet.main.db.Group;
import de.rwth.seilgraben.seilnet.main.config.Permission;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class SendMail extends WebPage
{
	private static final Locale	EMAIL_LOCALE	= Constants.SupportedLanguage.german.locale;
	
	private static Gson			GSON			= new Gson();
	
	private PebbleTemplate		template;
	
	@Override
	protected void initialize()
	{
		template = getTemplate("mail/send");
		
		Spark.get(Constants.PATH_PREFIX + Constants.MAIL_PATH_PREFIX + "/", (request, response) -> {
			response.redirect(Constants.PATH_PREFIX + Constants.MAIL_PATH_PREFIX + "/send",
					HttpStatus.TEMPORARY_REDIRECT_307);
			return "";
		});
		Spark.get(Constants.PATH_PREFIX + Constants.MAIL_PATH_PREFIX + "/send", route);
		Spark.post(Constants.PATH_PREFIX + Constants.MAIL_PATH_PREFIX + "/send", route);
	}
	
	Route route = (request, response) -> {
		if (!authorizeAllPermissions(request, response, Permission.MAIL)) { return ""; }
		Map<String, Object> args = new HashMap<>();
		Messages msgs = new Messages();
		
		args.put("customRecipientGroups", CustomRecipientGroup.customGroups());
		List<Group> groups = getDb().listGroups();
		groups.sort(new Comparator<Group>()
		{
			@Override
			public int compare(Group g1, Group g2)
			{
				return g1.getName().compareToIgnoreCase(g2.getName());
			}
		});
		args.put("databaseGroups", groups);
		args.put("user", getUser(request));
		args.put("groupPrefix", Constants.NEWSLETTER_GROUP_PREFIX);
		args.put("senderGroups", getUser(request).listGroups().stream().filter(group -> group.getEmail() != null)
				.collect(Collectors.toList()));
		
		if (request.requestMethod().equals("POST"))
		{
			if (!sendEmail(request, response, args, msgs))
			{
				args.put("subject", request.queryParams("subject"));
				args.put("messageText", request.queryParams("messageText"));
				Map<Integer, String> checkedDb = new HashMap<>();
				Map<String, String> checkedCustom = new HashMap<>();
				for (String queryParam : request.queryParams())
				{
					if (queryParam.startsWith("group_db_"))
					{
						checkedDb.put(Integer.parseInt(queryParam.substring(9)), "checked");
					}
					else if (queryParam.startsWith("group_custom_"))
					{
						checkedCustom.put(queryParam.substring(13), "checked");
					}
				}
				args.put("checkedDb", checkedDb);
				args.put("checkedCustom", checkedCustom);
				if (request.queryParams("replyTo") != null)
				{
					args.put("replyTo", Integer.parseInt(request.queryParams("replyTo")));
				}
				if (request.queryParams("userRecipients") != null)
				{
					Set<User> userRecipients = Arrays.stream(request.queryParamsValues("userRecipients"))
							.map(Integer::parseInt).map(getDb()::getUserByID).collect(Collectors.toSet());
					args.put("userRecipients", userRecipients);
				}
			}
		}
		
		args.put("checkedAddReplyAddress",
				(request.queryParams("addReplyAddress") != null && request.queryParams("addReplyAddress").equals("on"))
						? "checked" : "");
		
		msgs.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
	
	private boolean sendEmail(Request request, Response response, Map<String, Object> args, Messages msgs)
	{
		Mail mail = SeilnetMain.getMailSender().create();
		/**
		 * Create Mail, set subject and sender
		 */
		String subject = request.queryParams("subject");
		if (subject != null && subject.trim().length() > 0)
		{
			mail.subject(subject);
		}
		/**
		 * Select recipients
		 */
		Set<String> recipientGroupNames = new HashSet<>();
		Set<User> recipients = new HashSet<>();
		recipients.add(getUser(request));
		if (request.queryParams("userRecipients") != null)
		{
			recipients.addAll(Arrays.stream(request.queryParamsValues("userRecipients")).map(Integer::parseInt)
					.map(getDb()::getUserByID).collect(Collectors.toSet()));
		}
		for (String queryParam : request.queryParams())
		{
			if (request.queryParams(queryParam).equalsIgnoreCase("on"))
			{
				if (queryParam.startsWith("group_db_"))
				{
					int groupId = Integer.parseInt(queryParam.substring(9));
					Group group = getDb().getGroupByID(groupId);
					if (group == null || !group.isShowMailingList())
					{
						msgs.addError("strings", "mailGroupError");
						return false;
					}
					recipients.addAll(group.listMembers());
					recipientGroupNames.add(group.getName());
				}
				else if (queryParam.startsWith("group_custom_"))
				{
					String customGroupName = queryParam.substring(13);
					for (CustomRecipientGroup group : CustomRecipientGroup.customGroups())
					{
						if (customGroupName.equals(group.getName()))
						{
							recipients.addAll(group.listUsers(getDb()));
							recipientGroupNames.add(i18n("strings", group.getI18nTag(), EMAIL_LOCALE));
						}
					}
				}
			}
		}
		if (recipients.isEmpty())
		{
			msgs.addError("strings", "mailNoRecipients");
			return false;
		}
		List<User> failedRecipients = new ArrayList<>();
		for (User recipient : recipients)
		{
			try
			{
				mail.addBcc(recipient.getEmail());
			}
			catch (EmailException e)
			{
				failedRecipients.add(recipient);
			}
		}
		if (failedRecipients.size() == recipients.size())
		{
			msgs.addError("strings", "mailNoValidRecipients")
					.addParam(recipients.stream().map(user -> user.getFullName()).collect(Collectors.joining(", ")));
			return false;
		}
		/**
		 * Process message text and reply-to option
		 */
		String message = request.queryParams("messageText");
		String footer = "";
		if (message == null || message.trim().length() < Constants.NEWSLETTER_MIN_LENGTH)
		{
			msgs.addError("strings", "mailBelowMinLength").addParam(Integer.toString(Constants.NEWSLETTER_MIN_LENGTH));
			return false;
		}
		message = message.trim();
		message = "<html><body>" + message.replace("\r\n", "<br>");
		if (request.queryParams("replyTo") == null || request.queryParams("replyTo").trim().length() == 0)
		{
			msgs.addError("strings", "mailNoSender");
			return false;
		}
		int replyTo = Integer.parseInt(request.queryParams("replyTo"));
		if (replyTo == -2)
		{
			footer += Constants.NEWSLETTER_NO_REPLY_FOOTER + "<br><br>";
			mail.from(Constants.NEWSLETTER_SENDER_NAME);
		}
		else
		{
			try
			{
				synchronized (getDb())
				{
					User user = getUser(request);
					if (replyTo == -1)
					{
						mail.from(user.getFullName());
						mail.addReplyTo(user.getEmail());
					}
					else
					{
						Group group = getDb().getGroupByID(replyTo);
						if (group == null || !user.listGroups().contains(group) || group.getEmail() == null)
						{
							msgs.addError("strings", "mailInvalidSender");
							return false;
						}
						mail.from(Constants.NEWSLETTER_GROUP_PREFIX + group.getName());
						mail.addReplyTo(group.getEmail());
					}
				}
			}
			catch (EmailException e)
			{
				msgs.addError("strings", "mailInvalidSender");
				return false;
			}
		}
		if (!recipientGroupNames.isEmpty())
		{
			footer += Constants.NEWSLETTER_GROUPS_FOOTER;
			footer += String.join(", ", recipientGroupNames);
			footer += "<br><br>";
		}
		if (!footer.isEmpty())
		{
			message += Constants.NEWSLETTER_FOOTER_SEPARATOR;
			message += footer;
		}
		message = message + "</body></html>";
		try
		{
			mail.htmlText(message);
		}
		catch (EmailException e)
		{
			msgs.addError("strings", "mailInvalidText");
			Log.warn(LogCategory.MAIL, "Invalid E-Mail text", e);
			return false;
		}
		
		/**
		 * Send the E-Mail
		 */
		try
		{
			//mail.addRecipient(SeilnetMain.getMailSender().getSender());
			mail.send();
		}
		catch (EmailException e)
		{
			Log.warn(LogCategory.MAIL, "Failed to send newsletter mail", e);
			msgs.addError("strings", "mailError");
			return false;
		}
		if (failedRecipients.size() > 0)
		{
			msgs.addWarning("strings", "mailPartialSuccess")
					.addParam(Integer.toString(recipients.size() - failedRecipients.size()))
					.addParam(Integer.toString(recipients.size())).addParam(failedRecipients.stream()
							.map(user -> user.getFullName()).collect(Collectors.joining(", ")));
		}
		else
		{
			msgs.addOk("strings", "mailSuccess").addParam(Integer.toString(recipients.size()));
		}
		return true;
	}
	
	public static abstract class CustomRecipientGroup
	{
		public static final CustomRecipientGroup[] customGroups()
		{
			return new CustomRecipientGroup[] { new AllTenantsRecipientGroup(), new FloorRecipientGroup("10"),
					new FloorRecipientGroup("11"), new FloorRecipientGroup("12"), new FloorRecipientGroup("13"),
					new FloorRecipientGroup("14") };
		}
		
		public abstract String getName();
		
		public abstract List<User> listUsers(Database db);
		
		public String getI18nTag()
		{
			return "recipientGroup_" + getName();
		}
	}
	
	public static class AllTenantsRecipientGroup extends FloorRecipientGroup
	{
		public AllTenantsRecipientGroup()
		{
			super("");
		}
		
		@Override
		public String getName()
		{
			return "all";
		}
	}
	
	public static class FloorRecipientGroup extends CustomRecipientGroup
	{
		private final String floorPrefix;
		
		public FloorRecipientGroup(String floorPrefix)
		{
			this.floorPrefix = floorPrefix;
		}
		
		@Override
		public String getName()
		{
			return "floor" + floorPrefix;
		}
		
		@Override
		public List<User> listUsers(Database db)
		{
			List<User> result = new ArrayList<>();
			for (Room room : db.listRooms())
			{
				if (room.getRoomNumber().startsWith(floorPrefix))
				{
					User user = room.getCurrentUser();
					if (user != null)
					{
						result.add(user);
						User main = room.getMainTenant();
						if (!main.equals(user))
						{
							result.add(main);
						}
					}
					
				}
			}
			return result;
		}
	}
}
