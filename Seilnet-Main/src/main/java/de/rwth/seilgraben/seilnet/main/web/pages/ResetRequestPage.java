/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import com.google.common.util.concurrent.RateLimiter;
import de.rwth.seilgraben.seilnet.main.SimpleRateLimiter;
import org.apache.commons.mail.EmailException;

import io.pebbletemplates.pebble.template.PebbleTemplate;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.MailSender.Mail;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import lombok.SneakyThrows;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class ResetRequestPage extends WebPage
{
	private PebbleTemplate template;
	private SimpleRateLimiter<Integer> userThrottle
			= new SimpleRateLimiter<>(Constants.PASSWORD_RESET_LIMIT, Constants.PASSWORD_RESET_LIMIT_TIMEFRAME);
	private SimpleRateLimiter<String> ipThrottle
			= new SimpleRateLimiter<>(Constants.PASSWORD_RESET_LIMIT, Constants.PASSWORD_RESET_LIMIT_TIMEFRAME);
	
	@Override
	protected void initialize()
	{
		template = getTemplate("reset_request");
		
		Spark.get(Constants.PATH_PREFIX + "/reset_request", route);
		Spark.post(Constants.PATH_PREFIX + "/reset_request", route);
	}
	
	Route route = (request, response) -> {
		Map<String, Object> args = new HashMap<>();
		Messages messages = new Messages();
		
		if (request.requestMethod().equals("POST"))
		{
			passwordReset(request, response, messages, args);
		}
		
		messages.addToTemplateArgs(args);
		return runTemplate(template, args, request);
	};
	
	@SneakyThrows
	private void passwordReset(Request request, Response response, Messages messages, Map<String, Object> args)
	{
		User user = getDb().getUserByEmail(request.queryParams("email"));
		if (user == null)
		{
			messages.addError("strings", "userEmailNotFound");
			return;
		}

		if(!userThrottle.tryAcquire(user.getId()) || !ipThrottle.tryAcquire(getIp(request))) {
			messages.addError("strings", "rateLimitedPasswordReset");
			return;
		}

		user.generateWebPasswordResetToken();
		
		PebbleTemplate mailTemplate = getTemplate("mail/text/reset_password-" + user.getLocale().toLanguageTag());
		Map<String, Object> mailArgs = new HashMap<>();
		mailArgs.put("user", user);
		mailArgs.put("resetlink", ResetPage.getExternalUrl(user));
		StringWriter writer = new StringWriter();
		mailTemplate.evaluate(writer, mailArgs);
		String mailText = writer.toString();
		
		Mail mail = SeilnetMain.getMailSender().create();
		mail.subject(i18n("strings", "resetSubject", user.getLocale()));
		try
		{
			mail.addRecipient(user.getEmail());
			mail.htmlText(mailText);
			mail.send();
			messages.addOk("strings", "resetEmailSent");
		}
		catch (EmailException e)
		{
			messages.addError("strings", "resetFailedEmail");
			return;
		}
	}
}
