/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.mail.EmailConstants;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

import lombok.Getter;
import lombok.SneakyThrows;

/**
 * 
 *
 * @author Felix Kirchmann
 */
public class MailSender
{
	@Getter
	private final String		sender;
	private final String		smtpHost, userName, password;
	private final int			smtpPort;
	private final SmtpSecurity	security;
	
	public MailSender(String smtpHost, int smtpPort, String userName, String password, SmtpSecurity security,
			String sender)
	{
		this.smtpHost = smtpHost;
		this.smtpPort = smtpPort;
		this.userName = userName;
		this.password = password;
		this.security = security;
		this.sender = sender;
	}
	
	public Mail create()
	{
		return new Mail();
	}
	
	public static enum SmtpSecurity
	{
		NONE, SSL, STARTTLS
	}
	
	/**
	 * Instances of the class Mail are not thread-safe.
	 *
	 * @author Felix Kirchmann
	 */
	public class Mail
	{
		HtmlEmail email;
		
		@SneakyThrows
		private Mail()
		{
			email = new HtmlEmail();
			if (security == SmtpSecurity.SSL)
			{
				email.setSSLOnConnect(true);
			}
			else if (security == SmtpSecurity.STARTTLS)
			{
				email.setStartTLSRequired(true);
			}
			email.setHostName(smtpHost);
			email.setSmtpPort(smtpPort);
			email.setAuthentication(userName, password);
			email.setFrom(sender);
			email.setCharset(EmailConstants.UTF_8);
		}
		
		public Mail subject(String subject)
		{
			email.setSubject(subject);
			return this;
		}
		
		public Mail htmlText(String messageText) throws EmailException
		{
			email.setHtmlMsg(messageText);
			return this;
		}
		
		public Mail addReplyTo(String mail) throws EmailException
		{
			email.addReplyTo(mail);
			return this;
		}
		
		public Mail addRecipient(String address) throws EmailException
		{
			email.addTo(address);
			return this;
		}
		
		public Mail addBcc(String address) throws EmailException
		{
			email.addBcc(address);
			return this;
		}
		
		@SneakyThrows
		public Mail from(String name)
		{
			email.setFrom(sender, name);
			return this;
		}
		
		public void send() throws EmailException
		{
			email.send();
		}
	}
	
	public static boolean isEmailValid(String emailAddress)
	{
		try
		{
			new InternetAddress(emailAddress).validate();
			return true;
		}
		catch (AddressException e)
		{
			return false;
		}
	}
}
