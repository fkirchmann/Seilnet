/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web;

import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.attributes.AttributeResolver;
import io.pebbletemplates.pebble.extension.Extension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.extension.NodeVisitorFactory;
import io.pebbletemplates.pebble.extension.Test;
import io.pebbletemplates.pebble.operator.BinaryOperator;
import io.pebbletemplates.pebble.operator.UnaryOperator;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.pebbletemplates.pebble.tokenParser.TokenParser;

import de.rwth.seilgraben.seilnet.main.config.Constants;
import de.rwth.seilgraben.seilnet.util.Func;

/**
 *
 * @author Felix Kirchmann
 */
public class FunctionsExtension implements Extension
{
	@Override
	public Map<String, Function> getFunctions()
	{
		Map<String, Function> functions = new HashMap<>();
		functions.put("objectToString", new ObjectToStringFunction());
		functions.put("concat2", new Concat2Function());
		return functions;
	}
	
	public static class ObjectToStringFunction implements Function
	{
		@Override
		public List<String> getArgumentNames()
		{
			List<String> names = new ArrayList<>();
			names.add("object");
			return names;
		}
		
		@Override
		public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
		{
			Object o = args.get("object");
			if (o == null) { return "null"; }
			if (o instanceof LocalDate) { return Constants.DATE_FORMATTER.format((TemporalAccessor) o); }
			if (o instanceof TemporalAccessor) { return Constants.DATE_TIME_FORMATTER.format((TemporalAccessor) o); }
			return Func.object2string(o);
		}
	}
	
	public static class Concat2Function implements Function
	{
		@Override
		public List<String> getArgumentNames()
		{
			List<String> names = new ArrayList<>();
			names.add("one");
			names.add("two");
			return names;
		}
		
		@Override
		public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
		{
			String one = (String) args.get("one");
			String two = (String) args.get("two");
			if (one == null || two == null) { throw new NullPointerException(); }
			return one + two;
		}
	}
	
	@Override
	public Map<String, Filter> getFilters()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Map<String, Test> getTests()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<TokenParser> getTokenParsers()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<BinaryOperator> getBinaryOperators()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<UnaryOperator> getUnaryOperators()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Map<String, Object> getGlobalVariables()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<NodeVisitorFactory> getNodeVisitors()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<AttributeResolver> getAttributeResolver() {
		// TODO Auto-generated method stub
		return null;
	}
}
