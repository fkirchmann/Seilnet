/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main;

/**
 * Intended to be implemented by Enums, so that each Enum entry can be associated with a
 * human-readable name for each language. See strings_en_US.properties for examples.
 *
 * @author Felix Kirchmann
 */
public interface I18nEnum
{
	public default String getI18nFile()
	{
		return "strings";
	}
	
	public default String getI18nTag()
	{
		return "enum_" + this.getClass().getSimpleName() + "_" + this.name();
	}
	
	public String name();
}
