/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages.api;

import com.google.gson.Gson;
import de.rwth.seilgraben.seilnet.main.TokenManager;
import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.ExternalModule;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.main.web.WebPage;
import org.eclipse.jetty.http.HttpStatus;
import spark.Route;
import spark.Spark;

import java.util.HashMap;
import java.util.Map;

public class ExternalAuthApi extends WebPage
{
	private static Gson GSON = new Gson();
	
	@Override
	protected void initialize()
	{
		Spark.post(Constants.PATH_PREFIX + Constants.API_PATH_PREFIX + "/extauth/use_token", useToken);
	}
	
	Route useToken = (request, response) -> {
		String identifier = request.queryParams("module_identifier");
		ExternalModule module = null;
		if(identifier == null || (module = ExternalModule.fromIdentifier(identifier)) == null) {
			Spark.halt(HttpStatus.BAD_REQUEST_400, "Unknown external module identifier");
		}
		
		String secret = request.queryParams("module_secret");
		if(secret == null) { Spark.halt(HttpStatus.BAD_REQUEST_400, "Missing secret"); }
		if(!module.getSecret().equals(secret)) { Spark.halt(HttpStatus.FORBIDDEN_403, "Incorrect secret"); }
		
		String token = request.queryParams("token");
		if(token == null) {
			Spark.halt(HttpStatus.BAD_REQUEST_400, "Missing authentication token");
		}
		
		User user = null;
		try {
			user = TokenManager.getInstance().useToken(module, token);
		} catch(TokenManager.PermissionDeniedException e) {
			Spark.halt(HttpStatus.FORBIDDEN_403, "User is not authorized to access this module");
		} catch(TokenManager.UnknownTokenException e) {
			Spark.halt(HttpStatus.FORBIDDEN_403, "Unknown authentication token");
		} catch(TokenManager.MismatchingModuleException e) {
			Spark.halt(HttpStatus.FORBIDDEN_403, "This token was provided for a different module");
		}
		
		Map<String, String> userInfo = new HashMap<>();
		userInfo.put("id", Integer.toString(user.getId()));
		userInfo.put("email", user.getEmail());
		userInfo.put("firstName", user.getFirstName());
		userInfo.put("lastName", user.getLastName());
		userInfo.put("locale", user.getLocale().toLanguageTag());
		
		Map<String, Object> result = new HashMap<>();
		result.put("user", userInfo);
		return GSON.toJson(result);
	};
}
