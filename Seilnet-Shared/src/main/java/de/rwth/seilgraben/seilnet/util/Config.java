/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.esotericsoftware.minlog.Log;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

@Getter
public abstract class Config
{
	public static final Locale CONFIG_LOCALE = Locale.ENGLISH;
	
	/**
	 * Tries to load a configuration from a given <code>configFile</code>. If the file does not
	 * exist, it is created, filled with the contents of <code>defaultConfig</code> and then loaded.
	 * Note that the {@link InputStream} <code>defaultConfig</code> is closed by this method.
	 * 
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InvalidConfigurationException
	 *             If the configuration contains an error.
	 */
	public Config(@NonNull File configFile, @NonNull InputStream defaultConfig)
			throws IOException, InvalidConfigurationException
	{
		if (configFile.exists() && !configFile.isFile()) { throw new IOException(
				"Configuration file \"" + configFile.getAbsolutePath() + "\" exists, but is not a file"); }
		if (!configFile.exists())
		{
			Files.copy(defaultConfig, configFile.toPath());
		}
		defaultConfig.close();
		try (InputStream in = new FileInputStream(configFile))
		{
			loadConfig(in);
			return;
		}
		catch (IOException e)
		{
			throw e;
		}
	}
	
	@Getter(AccessLevel.PRIVATE)
	private final Properties p = new Properties();
	
	private void loadConfig(InputStream config) throws IOException, InvalidConfigurationException
	{
		p.load(config);
		loadOptions();
	}
	
	/**
	 * Loads the configuration settings. The implementation should retrieve configuration options
	 * using methods such as {@link #option(String)} or {@link #optionInt(String)}, validate their
	 * contents and store them in Java beans, so that they can later be read by public getters.
	 * 
	 * @throws InvalidConfigurationException
	 *             If the configuration contains an error.
	 */
	protected abstract void loadOptions() throws InvalidConfigurationException;
	
	protected boolean hasOption(String name)
	{
		return p.getProperty(name) != null;
	}
	
	protected String option(String name) throws InvalidConfigurationException
	{
		String param = p.getProperty(name);
		if (param == null) { throw new InvalidConfigurationException(name, "Missing configuration option"); }
		return param;
	}
	
	protected int optionInt(String name) throws InvalidConfigurationException
	{
		try
		{
			return Integer.parseInt(option(name));
		}
		catch (NumberFormatException e)
		{
			throw new InvalidConfigurationException(name, "Not an integer", e);
		}
	}

	protected int optionIntNonNegative(String name) throws InvalidConfigurationException
	{
		int number = optionInt(name);
		if(number < 0)
		{
			throw new InvalidConfigurationException(name, "Must not be negative");
		}
		return number;
	}

	protected Inet4Address optionInet4Address(String name) throws InvalidConfigurationException
	{
		try
		{
			return (Inet4Address) InetAddress.getByName(option(name));
		}
		catch (ClassCastException | UnknownHostException e)
		{
			throw new InvalidConfigurationException(name, "Not an IPv4 Address", e);
		}
	}
	
	protected int optionPort(String name) throws InvalidConfigurationException
	{
		int port = optionInt(name);
		if (port < 0 || port > 65535) { throw new InvalidConfigurationException(name,
				"Port number " + port + " invalid, must be in range 0-65535 (inclusive)"); }
		return port;
	}
	
	protected boolean optionBool(String name) throws InvalidConfigurationException
	{
		switch (option(name).trim().toLowerCase(CONFIG_LOCALE))
		{
			case "true":
				return true;
			case "false":
				return false;
			default:
				throw new InvalidConfigurationException(name,
						"Invalid boolean \"" + option(name) + "\" (must be either \"true\" or \"false\")");
		}
	}


	protected Pattern optionRegex(String name) throws InvalidConfigurationException
	{
		try
		{
			return Pattern.compile(option(name));
		}
		catch (PatternSyntaxException e)
		{
			throw new InvalidConfigurationException(name, "Invalid Regex", e);
		}
	}
	
	protected int optionLogLevel(String name) throws InvalidConfigurationException
	{
		switch (option(name).toUpperCase())
		{
			case "NONE":
				return Log.LEVEL_NONE;
			case "TRACE":
				return Log.LEVEL_TRACE;
			case "DEBUG":
				return Log.LEVEL_DEBUG;
			case "INFO":
				return Log.LEVEL_INFO;
			case "WARN":
				return Log.LEVEL_WARN;
			case "ERROR":
				return Log.LEVEL_ERROR;
			default:
				throw new InvalidConfigurationException(name, "Unknown log level \"" + option(name) + "\"");
		}
	}
	
	protected <T extends Enum<?>> T optionEnum(String name, Class<T> enumClass) throws InvalidConfigurationException
	{
		String enumName = option(name);
		for (T t : enumClass.getEnumConstants())
		{
			if (t.name().equals(enumName)) { return t; }
		}
		throw new InvalidConfigurationException(name, "\"" + enumName + "\" is not one of {"
				+ Arrays.stream(enumClass.getEnumConstants()).map(t -> t.name()).collect(Collectors.joining(", "))
				+ " }");
	}
	
	public static class InvalidConfigurationException extends Exception
	{
		private static final long serialVersionUID = 3973318903681403461L;
		
		public InvalidConfigurationException(String option, String message)
		{
			super(optionMessage(option, message));
		}
		
		public InvalidConfigurationException(String option, Throwable cause)
		{
			super(optionMessage(option, null), cause);
		}
		
		public InvalidConfigurationException(String option, String message, Throwable cause)
		{
			super(optionMessage(option, message), cause);
		}
		
		private static String optionMessage(String option, String message)
		{
			String optionMessage = "Error in config option " + option;
			if (message != null)
			{
				optionMessage += ": " + message;
			}
			return optionMessage;
		}
	}
}
