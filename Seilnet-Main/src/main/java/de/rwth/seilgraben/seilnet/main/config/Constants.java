/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @author Felix Kirchmann
 */
public class Constants
{
	public static String	STANDARD_CONFIG_LOCATION	= "./seilnet-config.properties";
	public static String	DEFAULT_CONFIG_CLASSPATH	= "seilnet-default-config.properties";

	public static String	DB_CHANGELOG_CLASSPATH		= "db-changelog.sql";
	
	public static final int	SCRYPT_N					= 16384;
	public static final int	SCRYPT_R					= 8;
	public static final int	SCRYPT_P					= 1;
	
	/**
	 * All locales supported by the webinterface.
	 * 
	 * @see Locale#forLanguageTag(String)
	 */
	public enum SupportedLanguage
	{
		english("en", "US"), german("de", "DE");
		
		public final Locale									locale;
		public final String									languageTag;
		private static final Map<String, SupportedLanguage>	languageTagMap	= new HashMap<>();
		
		private SupportedLanguage(String language, String country)
		{
			locale = new Locale(language, country);
			languageTag = language + "-" + country;
		}
		
		static
		{
			for (SupportedLanguage lang : SupportedLanguage.values())
			{
				languageTagMap.put(lang.languageTag.toLowerCase(Locale.ENGLISH), lang);
			}
		}
		
		public static SupportedLanguage fromLanguageTag(String languageTag)
		{
			return languageTagMap.get(languageTag.toLowerCase(Locale.ENGLISH));
		}
	}
	
	public static final Locale				DEFAULT_LOCALE					= new Locale("en", "US");
	
	public static final String				DATE_FORMAT_PATTERN				= "d.M.yyyy";
	public static final DateTimeFormatter	DATE_FORMATTER					= DateTimeFormatter
			.ofPattern(DATE_FORMAT_PATTERN).withLocale(DEFAULT_LOCALE).withZone(ZoneId.systemDefault());
	
	public static final String				DATE_TIME_FORMAT_PATTERN		= "d.M.yyyy H:mm";
	public static final DateTimeFormatter	DATE_TIME_FORMATTER				= DateTimeFormatter
			.ofPattern(DATE_TIME_FORMAT_PATTERN).withLocale(DEFAULT_LOCALE).withZone(ZoneId.systemDefault());
	
	public static final String				TIME_FORMAT_PATTERN				= "H:mm";
	public static final DateTimeFormatter	TIME_FORMATTER					= DateTimeFormatter
			.ofPattern(TIME_FORMAT_PATTERN).withLocale(DEFAULT_LOCALE).withZone(ZoneId.systemDefault());
	
	/**
	 * When only an expiry date is set (e.g. for expiring leases), this configures the precise
	 * second of the day at which the expiration occurs.
	 */
	public static final LocalTime			DAILY_EXPIRY_TIME				= LocalTime.of(23, 59, 59);

	public static final LocalTime			DAILY_DYNAMIC_IP_CHANGE_TIME	= LocalTime.of(5, 0, 0);

	public static final String				PATH_PREFIX						= "/seilnet";
	public static final String				ADMIN_PATH_PREFIX				= "/admin";
	public static final String				MAIL_PATH_PREFIX				= "/mail";
	public static final String				API_PATH_PREFIX					= "/api";
	public static final String				NETSETTINGS_PATH_PREFIX			= "/netsettings";
	
	/**
	 * Alphanumeric, but with some characters that can be misread (o, 0, 1, l) left out
	 */
	public static final String				RADIUS_PASSWORD_CHARSET			= "abcdefghjkmnpqrstuvwxyz23456789";
	public static final int					RADIUS_PASSWORD_LENGTH			= 12; // should take 10 * RTX 3090 more than 2000 years to crack
	public static final String				PASSWORD_RESET_TOKEN_CHARSET	= "abcdefghjkmnpqrstuvwxyz23456789";
	
	/**
	 * Only requests whose path begins with this string will be cacheable.
	 */
	
	private static String[]					CACHEABLE_PATHS_PREFIXES		= new String[] { PATH_PREFIX + "/static",
			"/favicon.ico" };
	
	public static String[] CACHEABLE_PATHS_PREFIXES()
	{
		return Arrays.copyOf(CACHEABLE_PATHS_PREFIXES, CACHEABLE_PATHS_PREFIXES.length);
	}
	
	public static final long				STATIC_CACHE_EXPIRE_TIME		= 600;	// seconds

	public static final int					PASSWORD_RESET_LIMIT			= 3;
	public static final Duration			PASSWORD_RESET_LIMIT_TIMEFRAME	= Duration.ofMinutes(30);

	public static final int					LOGIN_LIMIT						= 15;
	public static final Duration			LOGIN_LIMIT_TIMEFRAME			= Duration.ofMinutes(1);

	public static final int					DEVICE_SELF_REGISTRATION_LIMIT	= 20;
	public static final Duration			DEVICE_SELF_REGISTRATION_LIMIT_TIMEFRAME
																			= Duration.ofMinutes(20);

	public static final Map<String, String>	WEB_CONSTANTS;
	public static final Map<String, String>	MIME_MAPPING;
	
	static
	{
		Map<String, String> constants = new HashMap<>();
		
		constants.put("pathprefix", PATH_PREFIX);
		constants.put("admin_pathprefix", ADMIN_PATH_PREFIX);
		constants.put("mail_pathprefix", MAIL_PATH_PREFIX);
		constants.put("api_pathprefix", API_PATH_PREFIX);
		constants.put("netsettings_pathprefix", NETSETTINGS_PATH_PREFIX);

		WEB_CONSTANTS = Collections.unmodifiableMap(constants);
		
		Map<String, String> mimeMapping = new HashMap<>();
		
		mimeMapping.put(".css", "text/css");
		mimeMapping.put(".js", "text/javascript");
		mimeMapping.put(".svg", "image/svg+xml");
		
		MIME_MAPPING = Collections.unmodifiableMap(constants);
	}
	
	public static final int		NEWSLETTER_MIN_LENGTH		= 10;																				// characters
	public static final String	NEWSLETTER_GROUP_PREFIX		= "Seilgraben ";
	public static final String	NEWSLETTER_SENDER_NAME		= "Wohnheim Seilgraben";
	public static final String	NEWSLETTER_FOOTER_SEPARATOR	= "<br><br><hr>";
	public static final String	NEWSLETTER_NO_REPLY_FOOTER	= "Bitte nicht auf diese E-Mail antworten. / Please do not reply to this E-Mail.";
	public static final String	NEWSLETTER_GROUPS_FOOTER	= "Diese E-Mail wurde an folgende Gruppen gesendet: ";
	
	public static final int			EXTAPP_TOKEN_LENGTH		= 10;
	public static final String		EXTAPP_TOKEN_CHARSET	= "abcdefghjkmnopqrstuvwxyz" + "ABCDEFGHJKMNOPQRSTUVWXYZ"
																+ "0123456789";
	public static final Duration	EXTAPP_TOKEN_LIFETIME	= Duration.ofSeconds(120);
}
