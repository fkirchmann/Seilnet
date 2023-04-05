/*
 * Copyright (c) 2016-2023 Felix Kirchmann.
 * Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */

package de.rwth.seilgraben.seilnet.main.db;

import java.sql.SQLException;

/**
 * This class exists to eventually port the entire Database API from ORMLite to JOOQ.
 *
 * The plan:
 * <ul>
 *     <li>Create new Database Tables in this class</li>
 *     <li>Port over tables from the old class into this one</li>
 *     <li>Once everything has been ported over, delete the old Database class and remove the Ext suffix form this
 *     class's name</li>
 * </ul>
 */
public class DatabaseExt extends Database {
    public DatabaseExt(String host, String databaseName, String username, String password) throws SQLException {
        super(host, databaseName, username, password);
    }
}
