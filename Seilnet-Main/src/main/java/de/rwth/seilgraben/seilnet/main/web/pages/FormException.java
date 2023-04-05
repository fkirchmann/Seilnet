/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.web.pages;

import lombok.NonNull;

/**
 *
 * @author Felix Kirchmann
 */
public abstract class FormException extends Exception
{
	private static final long serialVersionUID = 2965313791271706326L;
	
	public static class InvalidFieldValueException extends FormException
	{
		private static final long	serialVersionUID	= 2334223207091867113L;
														
		public final String			messageName;
									
		public InvalidFieldValueException(@NonNull String messageName)
		{
			this.messageName = messageName;
		}
	}
	
	public static class MissingFieldException extends FormException
	{
		private static final long serialVersionUID = -6376057885643189635L;
		
		/**
		 * Throws a MissingFieldException if any of the fields are null or empty, or consist only of
		 * whitespace.
		 */
		public static void requireFields(String ... fields) throws MissingFieldException
		{
			for (String field : fields)
			{
				if (field == null || field.trim().length() == 0) { throw new MissingFieldException(); }
			}
		}
	}
}
