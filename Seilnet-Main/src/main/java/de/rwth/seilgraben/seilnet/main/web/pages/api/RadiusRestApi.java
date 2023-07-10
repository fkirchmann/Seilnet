/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.api;

import static spark.Spark.*;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.rwth.seilgraben.seilnet.util.MacAddress;
import org.eclipse.jetty.http.HttpStatus;

import com.esotericsoftware.minlog.Log;
import com.google.gson.Gson;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.LogCategory;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.db.Room;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthResult;
import de.rwth.seilgraben.seilnet.main.db.orm.DBAuthenticationEvent.AuthType;
import de.rwth.seilgraben.seilnet.main.web.MimeType;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import spark.Filter;
import spark.Request;
import spark.Route;
import spark.Spark;

/**
 *
 * @author Felix Kirchmann
 */
public class RadiusRestApi extends WebPage
{
	private static final String REQUEST_USER_NAME = "userName",
								REQUEST_AP_MAC = "macAddressAp",
								REQUEST_AP_SSID = "ssidAp",
								REQUEST_CLIENT_MAC = "macAddressClient",
								REQUEST_AP_SSID_ROOM = "ssidRoom",
								REQUEST_SET_TUNNEL_AUTHENTICATED = "useTunnelType";

	private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}[:-]?){5}([0-9A-Fa-f]{2})$");

	private static Gson GSON = new Gson();
	
	// srsly eclipse????
	// @formatter:off
	
	@Override
	protected void initialize()
	{
		if(SeilnetMain.getConfig().getWebRadiusUser() == null)
		{
			Log.info(LogCategory.RADIUS, "FreeRADIUS API InternalModule disabled in configuration.");
			return;
		}
		
		/**
		 * Authenticate the client RADIUS server and extract the wireless client's username and MAC
		 * address, as well as the MAC address of the AP.
		 */
		Spark.before(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/radius/*/user/*/ap/*/client/*",
				prepareRequest);
		Spark.before(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/radius/*/user/*/ap//client/",
				prepareRequest);
		/**
		 * Authenticate the wireless client.
		 */
		Spark.post(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/radius/auth/user/*/ap/*/client/*", auth);
		Spark.post(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/radius/auth/user/*/ap//client/", auth);
		/**
		 * Log the auth result in the database (if we haven't done so yet).
		 */
		Spark.post(
				Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/radius/postauth/user/*/ap/*/client/*/result/*/*/",
				postAuth);
	}

	private final Filter authenticateRadiusServerRequest = (request, response) -> {
		/**
		 * Authenticate the remote RADIUS server
		 */
		final String basicAuth = request.headers("Authorization");
		if (basicAuth == null || !basicAuth.toLowerCase().startsWith("basic "))
		{
			Log.warn(LogCategory.RADIUS, "Received request with invalid HTTP Basic authentication header");
			Spark.halt(401);
		}
		final String authDecoded = new String(Base64.getMimeDecoder().decode(basicAuth.substring(6)));
		Log.trace(LogCategory.RADIUS, "Received auth header: " + authDecoded);
		if (!authDecoded
				.equals(SeilnetMain.getConfig().getWebRadiusUser() + ":" + SeilnetMain.getConfig().getWebRadiusPassword()))
		{
			Log.warn(LogCategory.RADIUS, "Received request with wrong HTTP Basic authentication username / password");
			Spark.halt(401);
		}
	};

	private final Filter prepareRequest = (request, response) -> {
		Log.trace(LogCategory.RADIUS, "############ Incoming Request ############");
		/**
		 * Decode information (user and MAC addresses, if available) from the request.
		 * Fields:
		 * 0 - method (auth or postauth)
		 * 1 - username or Client MAC address
		 * 2 - combined AP MAC and SSID
		 * 3 - Client MAC
		 */
		Log.trace(LogCategory.RADIUS, "URI: " + request.uri());

		String arg1 = request.splat()[1];
		request.attribute(REQUEST_USER_NAME, arg1);
		request.attribute(REQUEST_SET_TUNNEL_AUTHENTICATED, false);

		Log.trace(LogCategory.RADIUS, "-> Username: " + request.attribute(REQUEST_USER_NAME));
		// If the request includes the AP and client MAC, decode those too
		if(request.splat().length >= 4) {
			/**
			 * The ap URL parameter given by freeradius has the form:
			 * AA-BB-CC-DD-EE-FF:SeilgrabenWlan
			 */
			request.attribute(REQUEST_AP_MAC, request.splat()[2].split(":")[0].replace('-', ':').toLowerCase());
			request.attribute(REQUEST_AP_SSID, request.splat()[2].split(":")[1]);
			request.attribute(REQUEST_CLIENT_MAC, request.splat()[3].replaceAll("/.*", "").replace('-', ':').toLowerCase());
			String ssid = request.attribute(REQUEST_AP_SSID);
			Log.trace(LogCategory.RADIUS, "-> AP SSID: \"" + ssid + "\"");
			Pattern pattern;
			if(ssid != null && ssid.equals(SeilnetMain.getConfig().getWebRadiusUidiotSSID())) {
				Log.trace(LogCategory.RADIUS, "  -> SSID matches web_radius_uidiot_ssid, using UIDIoT mode");
				// if mac address is given, try to get username by mac address
				if (MAC_ADDRESS_PATTERN.matcher(arg1).matches()){
					Log.trace(LogCategory.RADIUS, "-> MAC address detected: " + arg1);
					Optional<User> user = Optional.empty();
					try {
						user = Optional.ofNullable(getDb().getUserByMacAddress(new MacAddress(arg1)));
					} catch (IllegalArgumentException e) {
						Log.warn("Invalid MAC address: " + arg1);
					}
					Log.trace(LogCategory.RADIUS, "-> Mapping to User: "
							+ user.map(User::getFullName).orElse("Not Found"));
					user.map(User::getRoomAssignment).ifPresent(roomAssignment -> {
						Log.trace(LogCategory.RADIUS, "-> Mapping to Room: "
								+ roomAssignment.getRoom().getRoomNumber());
						request.attribute(REQUEST_USER_NAME, roomAssignment.getRoom().getRoomNumber());
						request.attribute(REQUEST_SET_TUNNEL_AUTHENTICATED, true);
					});
				}
			} else if((pattern = SeilnetMain.getConfig().getWebRadiusRoomSSIDRegex()) != null) {
				Matcher matcher = pattern.matcher(ssid);
				if(!matcher.find()) {
					Log.trace(LogCategory.RADIUS, "  -> SSID does not match web_radius_room_ssid_regex");
				} else {
					if(matcher.groupCount() != 1) {
						Log.warn(LogCategory.RADIUS, "web_radius_room_ssid_regex matched SSID \""
								+ ssid + "\", but capture group count (" + matcher.groupCount()
								+ ") is not equal to expected group count (1). Please review configured regex.");
					} else {
						Room room = getDb().getRoomByNumber(matcher.group(1));
						if(room == null) {
							Log.warn(LogCategory.RADIUS, "Extracted Room No. \"" + matcher.group(1)
									+ "\" from SSID \"" + request.attribute(REQUEST_AP_SSID) + "\", but no such room exists. "
									+ "Please check the network SSID or the configured web_radius_room_ssid_regex");
						} else {
							Log.trace(LogCategory.RADIUS, "  -> SSID belongs to Room " + room.getRoomNumber()
									+ ", using VLAN-tagging-only mode");
							request.attribute(REQUEST_AP_SSID_ROOM, room);
						}
					}
				}
			}

			Log.trace(LogCategory.RADIUS, "-> AP MAC: " + (String) request.attribute(REQUEST_AP_MAC));
			Log.trace(LogCategory.RADIUS, "-> Client MAC: " + (String) request.attribute(REQUEST_CLIENT_MAC));
		}
	};
	
	private final Route auth = (request, response) -> {
		Log.trace(LogCategory.RADIUS, "--- MODE: Auth START ---");
		User user = null;
		Map<String, String> jsonResponse = new HashMap<>();
		synchronized(getDb())
		{
			boolean roomVlanOnlyMode = request.attribute(REQUEST_AP_SSID_ROOM) != null;

			Room room = roomVlanOnlyMode ? request.attribute(REQUEST_AP_SSID_ROOM)
					: getDb().getRoomByNumber(request.attribute(REQUEST_USER_NAME));

			if(room != null)
			{
				if((user = room.getCurrentUser()) == null)
				{
					Log.trace(LogCategory.RADIUS, "Room found, but does not have a tenant");
					getDb().logAuthEvent(null, getClientInfo(request), AuthType.WLAN, AuthResult.NO_LEASE);
					Spark.halt(HttpStatus.UNAUTHORIZED_401);
				}
			}
			else
			{
				user = getDb().getUserByEmail(request.attribute(REQUEST_USER_NAME));
				if(user == null)
				{
					Log.trace(LogCategory.RADIUS, "User EMail or room not found");
					getDb().logAuthEvent(null, getClientInfo(request), AuthType.WLAN, AuthResult.UNKNOWN_USER);
					Spark.halt(HttpStatus.UNAUTHORIZED_401);
				}
				if(user.getRoomAssignment() == null)
				{
					Log.trace(LogCategory.RADIUS, "User does not have an active room assignment");
					getDb().logAuthEvent(user, getClientInfo(request), AuthType.WLAN, AuthResult.NO_LEASE);
					Spark.halt(HttpStatus.UNAUTHORIZED_401);
				}
				room = user.getRoomAssignment().getRoom();
			}
			
			AuthResult result = user.canLogin();
			if(result != AuthResult.OK)
			{
				Log.trace(LogCategory.RADIUS, "User is not currently able to login, reason: " + result.name());
				getDb().logAuthEvent(null, getClientInfo(request), AuthType.WLAN, result);
				Spark.halt(HttpStatus.UNAUTHORIZED_401);
			}
			if(roomVlanOnlyMode) {
				jsonResponse.put("control:Cleartext-Password", request.attribute(REQUEST_USER_NAME));
			} else {
				if (user.getWlanPassword() == null) {
					Log.trace(LogCategory.RADIUS, "User does not have a WiFi password specified");
					getDb().logAuthEvent(null, getClientInfo(request), AuthType.WLAN, AuthResult.WRONG_PASSWORD);
					Spark.halt(HttpStatus.UNAUTHORIZED_401);
				}
				if (request.attribute(REQUEST_SET_TUNNEL_AUTHENTICATED)){
					// prepare response for UidIot
					// See https://mistererwin.github.io/UniFiPPSK/ for details
					jsonResponse.put("Tunnel-Password", user.getWlanPassword());
					jsonResponse.put("request:User-Password", user.getWlanPassword());
				}
				jsonResponse.put("control:Cleartext-Password", user.getWlanPassword());
			}
			if(room.getVlan() != null)
			{
				jsonResponse.put("Tunnel-Type", "VLAN");
				jsonResponse.put("Tunnel-Medium-Type", "IEEE-802");
				jsonResponse.put("Tunnel-Private-Group-Id", room.getVlan().toString());
			}
		}
		String json = GSON.toJson(jsonResponse);
		Log.trace(LogCategory.RADIUS, "OK, returning JSON response: " + json);
		Log.trace(LogCategory.RADIUS, "--- MODE: Auth FINISH ---");
		response.type(MimeType.JSON);
		return json;
	};
	
	private final Route postAuth = (request, response) -> {
		String authResult = request.splat()[3];
		Log.trace(LogCategory.RADIUS, "-> AuthResult: " + authResult);
		/*
		 * splat()[4] contains the password that we returned to the RADIUS server during the auth request.
		 *
		 * If path[4] is empty, that means we returned null as a password earlier, in which case
		 * the authentication failure was already logged in the DB.
		 */
		Log.trace(LogCategory.RADIUS, "-> Password: " + request.splat()[4]);
		Log.trace(LogCategory.RADIUS, "-> Password (without prefix): " + request.splat()[4].replaceFirst("pw:", ""));
		Log.trace(LogCategory.RADIUS, "--- MODE: PostAuth START ---");
		if (request.splat()[4].replaceFirst("pw:", "").length() > 0)
		{
			Log.trace(LogCategory.RADIUS, "Password is not empty, logging auth result in database");
			if (authResult.equals("Access-Accept"))
			{
				Log.trace(LogCategory.RADIUS, "Authentication was successful, logging 'OK'");
				getDb().logAuthEvent(getDb().getUserByEmail(request.attribute(REQUEST_USER_NAME)), getClientInfo(request), AuthType.WLAN, AuthResult.OK);
				/*Integer vlan = getDb().getRoomByNumber(request.attribute(REQUEST_USER_NAME)).getVlan();
				if(vlan != null)
				{
					Log.trace(LogCategory.RADIUS, "Returning VLAN " + vlan + " by sending JSON:");
					Map<String, String> jsonResponse = new HashMap<>();
					jsonResponse.put("Tunnel-Type", "VLAN");
					jsonResponse.put("Tunnel-Medium-Type", "IEEE-802");
					jsonResponse.put("Tunnel-Private-Group-Id", vlan.toString());
					String json = GSON.toJson(jsonResponse);
					Log.trace(LogCategory.RADIUS, json);
					response.type(MimeType.JSON);
					return json;
				}*/
			}
			else if (authResult.equals("Access-Reject"))
			{
				Log.trace(LogCategory.RADIUS, "Authentication was unsuccessful, logging 'WrongPassword'");
				getDb().logAuthEvent(null, getClientInfo(request), AuthType.WLAN, AuthResult.WRONG_PASSWORD);
			}
			else
			{
				Log.warn(LogCategory.RADIUS, "Unknown Auth Result \"" + authResult + "\"");
				halt(500, "Unknown auth result");
			}
		}
		else
		{
			Log.trace(LogCategory.RADIUS, "Password is empty, auth attempt was already logged in DB. Not logging again.");
		}
		response.type("text/plain");
		response.status(HttpStatus.NO_CONTENT_204);
		Log.trace(LogCategory.RADIUS, "--- MODE: PostAuth FINISH ---");
		return "";
	};
	
	private String getClientInfo(Request request)
	{
		return "Client: " + request.attribute(REQUEST_CLIENT_MAC) + " - AP: " + request.attribute(REQUEST_AP_MAC)
				+ " - SSID: " + request.attribute(REQUEST_AP_SSID);
	}
}
