/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db.orm;

import java.time.Instant;
import java.time.LocalDate;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.rwth.seilgraben.seilnet.main.db.orm.util.InstantPersister;
import de.rwth.seilgraben.seilnet.main.db.orm.util.LocalDatePersister;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 *
 * @author Thomas Lemmerz <thomas.lemmerz@rwth-aachen.de>
 */
@DatabaseTable(tableName = "InfoScreen_daten")
@Data
public class DBInfoScreenDaten
{
	@DatabaseField(generatedId = true, canBeNull = false)
	@Setter(AccessLevel.PRIVATE)
	private volatile int		id;
	
	@DatabaseField(canBeNull = false)
	private volatile String		ueberschrift;
	
	@DatabaseField(canBeNull = false)
	private volatile String		text;
	
	@DatabaseField
	private volatile String		kontakt;
	
	@DatabaseField(canBeNull = false, persisterClass = LocalDatePersister.class)
	private volatile LocalDate	von;
	
	@DatabaseField(canBeNull = false, persisterClass = LocalDatePersister.class)
	private volatile LocalDate	bis;
	
	@DatabaseField(canBeNull = false)
	private volatile Integer	anzeigedauer;
	
	@DatabaseField
	private volatile Integer	MO;
	
	@DatabaseField
	private volatile Integer	DI;
	
	@DatabaseField
	private volatile Integer	MI;
	
	@DatabaseField
	private volatile Integer	DO;
	
	@DatabaseField
	private volatile Integer	FR;
	
	@DatabaseField
	private volatile Integer	SA;
	
	@DatabaseField
	private volatile Integer	SO;
	
	@DatabaseField(canBeNull = false, persisterClass = InstantPersister.class)
	private volatile Instant	uhrzeit_von;
	
	@DatabaseField(canBeNull = false, persisterClass = InstantPersister.class)
	private volatile Instant	uhrzeit_bis;
	
}
