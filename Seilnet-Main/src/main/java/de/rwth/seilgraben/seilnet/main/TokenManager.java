/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.config.ExternalModule;
import de.rwth.seilgraben.seilnet.main.db.User;
import de.rwth.seilgraben.seilnet.util.Func;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;

public class TokenManager
{
	private static TokenManager INSTANCE = new TokenManager();
	
	public static TokenManager getInstance() { return INSTANCE; }
	
	private Map<String, TokenData> tokens       = new HashMap<>();
	private final Object           monitor      = new Object();
	private final Timer            cleanupTimer = new Timer(true);
	
	/**
	 * How often to purge expired tokens. Note that expired tokens are not useable even if they haven't been purged yet.
 	 */
	private final int		CLEANUP_INTERVAL_MS	= (int) Constants.EXTAPP_TOKEN_LIFETIME.toMillis() * 10;
	
	private TokenManager() {
		cleanupTimer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() { cleanup(); }
		}, 0, CLEANUP_INTERVAL_MS);
	}
	
	private void cleanup() {
		synchronized (monitor) {
			Iterator<Map.Entry<String, TokenData>> iterator = tokens.entrySet().iterator();
			tokens.entrySet().removeIf(e -> e.getValue().isExpired());
		}
	}
	
	public String newToken(@NonNull ExternalModule app, @NonNull User user) {
		TokenData tokenData = new TokenData(app, user, Constants.EXTAPP_TOKEN_LIFETIME);
		synchronized (monitor) { tokens.put(tokenData.getToken(), tokenData); }
		return tokenData.getToken();
	}
	
	public User useToken(@NonNull ExternalModule app, @NonNull String token) throws
			UnknownTokenException, PermissionDeniedException, MismatchingModuleException
	{
		TokenData tokenData;
		synchronized (monitor) {
			tokenData = tokens.get(token);
			if(tokenData == null) { throw new UnknownTokenException(); }
			if(!tokenData.getModule().equals(app)) { throw new MismatchingModuleException(); }
			if(!tokenData.getModule().isUsagePermitted(tokenData.user)) { throw new PermissionDeniedException(); }
			if(tokenData.isExpired()) {
				tokens.remove(token);
				throw new UnknownTokenException();
			}
			tokens.remove(token);
		}
		return tokenData.user;
	}
 
	public class MismatchingModuleException extends Exception {}
	public class UnknownTokenException extends Exception {}
	public class PermissionDeniedException extends Exception {}
	
	@Getter
	@EqualsAndHashCode
	private class TokenData
	{
		private final ExternalModule module;
		private final User           user;
		private final String   token    = Func.generateRandomString(Constants.EXTAPP_TOKEN_LENGTH,
										Constants.EXTAPP_TOKEN_CHARSET);
		private final Instant  creation = Instant.now();
		private final Instant  expiration;
		
		TokenData(@NonNull ExternalModule module, @NonNull User user, @NonNull Duration lifetime) {
			this.module = module;
			this.user = user;
			this.expiration = creation.plus(lifetime);
		}
		
		boolean isExpired() {
			return Instant.now().isAfter(expiration);
		}
	}
}
