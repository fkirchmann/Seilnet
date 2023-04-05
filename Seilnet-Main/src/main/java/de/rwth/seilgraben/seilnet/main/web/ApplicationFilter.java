/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import spark.servlet.SparkFilter;

public class ApplicationFilter extends SparkFilter
{
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException
	{
		String requestUrl = ((HttpServletRequest) request).getRequestURI().toString();
		
		Map<String, String> mimeMapping = new HashMap<>();
		mimeMapping.put(".css", "text/css");
		mimeMapping.put(".js", "text/javascript");
		
		for (Map.Entry<String, String> entry : mimeMapping.entrySet())
		{
			if (requestUrl.endsWith(entry.getKey()))
			{
				((HttpServletResponse) response).setHeader("Content-Type", entry.getValue());
			}
		}
		super.doFilter(request, response, chain);
	}
}