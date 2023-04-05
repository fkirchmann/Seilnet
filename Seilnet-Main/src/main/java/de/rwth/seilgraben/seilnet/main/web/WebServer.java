/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web;

import java.io.InputStream;
import java.lang.reflect.Field;

import de.rwth.seilgraben.seilnet.main.db.DatabaseExt;
import lombok.SneakyThrows;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpStatus;

import com.esotericsoftware.minlog.Log;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.Loader;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.main.LogCategory;
import de.rwth.seilgraben.seilnet.main.SeilnetMain;
import de.rwth.seilgraben.seilnet.main.db.Database;
import de.rwth.seilgraben.seilnet.util.Func;
import lombok.NonNull;
import lombok.Synchronized;
import org.eclipse.jetty.server.session.SessionHandler;
import spark.ExceptionMapper;
import spark.Spark;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.EmbeddedServers;
import spark.embeddedserver.jetty.EmbeddedJettyFactory;
import spark.embeddedserver.jetty.EmbeddedJettyServer;
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;

/**
 *
 * @author Felix Kirchmann
 */
public enum WebServer
{
	INSTANCE;
	
	private boolean			started	= false;
	private boolean			running	= false;
	@SuppressWarnings("unused")
	private DatabaseExt		db;
	private PebbleEngine	engine;

	/**
	 * Modified embedded webserver that prevents CSRF by setting the SameSite attribute on cookies
	 */
	private class StrictSameSiteJettyFactory extends EmbeddedJettyFactory {
		@SneakyThrows
		public EmbeddedServer create(Routes routeMatcher,
									 StaticFilesConfiguration staticFilesConfiguration,
									 ExceptionMapper exceptionMapper,
									 boolean hasMultipleHandler) {

			EmbeddedJettyServer server = (EmbeddedJettyServer)
					super.create(routeMatcher, staticFilesConfiguration, exceptionMapper, hasMultipleHandler);

			// This is an ugly, but necessary evil to set the same-site cookie in the least invasive way
			Field handlerField = EmbeddedJettyServer.class.getDeclaredField("handler");
			handlerField.setAccessible(true);
			SessionHandler handler = (SessionHandler) handlerField.get(server);
			handler.setSameSite(HttpCookie.SameSite.LAX);

			return server;
		}
	}
							
	@Synchronized
	public void start(@NonNull DatabaseExt db, String addr, int port)
	{
		if (started || running) { throw new IllegalStateException("Web server already started"); }
		started = true;
		running = true;
		this.db = db;
		// Since the first call to the Spark class will start Spark's embedded webserver, we need to add our
		// modified embedded webserver first
		// This modified embedded webserver is neces
		EmbeddedServers.add(EmbeddedServers.Identifiers.JETTY, new StrictSameSiteJettyFactory());

		Spark.staticFileLocation(SeilnetMain.getClasspathPrefix() + "wwwroot");
		Spark.ipAddress(addr);
		Spark.port(port);
		/*Spark.staticFiles.location("wwwroot");
		Spark.staticFiles.expireTime(Constants.STATIC_CACHE_EXPIRE_TIME);*/
		final String[] prefixes = Constants.CACHEABLE_PATHS_PREFIXES();
		Spark.before((request, response) -> {
			for (String prefix : prefixes)
			{
				if (request.pathInfo().startsWith(prefix))
				{
					//Log.debug("  Cache: " + request.pathInfo());
					response.header("Cache-Control", "max-age=" + Constants.STATIC_CACHE_EXPIRE_TIME);
					return;
				}
			}
			//Log.debug("NoCache: " + request.pathInfo());
			//response.type("text/html");
			response.header("cache-control", "no-store");
		});
		Loader<String> loader = new ClasspathLoader(
				new JarCompatibleProxyClassLoader(this.getClass().getClassLoader()));
		loader.setPrefix("templates");
		this.engine = new PebbleEngine.Builder().loader(loader).extension(new FunctionsExtension()).build();
		//Spark.staticFiles.header("Content-Type", "application/octet-stream");
		
		Spark.exception(Exception.class, (e, req, resp) -> {
			// If an exception occured while generating a page, log it
			Log.error(LogCategory.WEB, "Error occured in routing", e);
			// And server a 500 Internal Server Error Page
			// Including the stacktrace, if the relevant config option is set.
			resp.status(HttpStatus.INTERNAL_SERVER_ERROR_500);
			resp.body("500 Internal Server Error"
					+ (SeilnetMain.getConfig().isWebDebugShowStacktrace() ? "\r\n\r\n" + Func.object2string(e) : ""));
			resp.type("text/plain");
		});
		WebPage.initializeAllPages(db, engine);
	}
	
	@Synchronized
	public void stop()
	{
		if (!started || !running) { throw new IllegalStateException("Web server not running"); }
		running = false;
		Spark.stop();
	}
	
	private static class JarCompatibleProxyClassLoader extends ClassLoader
	{
		private final ClassLoader parent;
		
		public JarCompatibleProxyClassLoader(ClassLoader parent)
		{
			this.parent = parent;
		}
		
		@Override
		public InputStream getResourceAsStream(String name)
		{
			InputStream in = parent.getResourceAsStream(name);
			if (in != null) { return in; }
			in = parent.getResourceAsStream("resources/" + name);
			return in;
		}
	}
}
