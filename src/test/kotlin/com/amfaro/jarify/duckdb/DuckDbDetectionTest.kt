package com.amfaro.jarify.duckdb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the DuckDB driver/url matcher. Project-wide and
 * console-attached lookups are exercised by manual IDE testing — they need
 * a live `DbPsiFacade` / `JdbcConsole` and aren't worth mocking for a
 * one-line conjunction.
 */
class DuckDbDetectionTest {

    @Test fun `matches official duckdb jdbc driver class`() {
        assertTrue(DuckDbDetection.isDuckDb("org.duckdb.DuckDBDriver", null))
    }

    @Test fun `matches duckdb url scheme`() {
        assertTrue(DuckDbDetection.isDuckDb(null, "jdbc:duckdb:/tmp/foo.db"))
    }

    @Test fun `matches case-insensitively`() {
        assertTrue(DuckDbDetection.isDuckDb("ORG.DuckDB.DUCKDBDRIVER", null))
        assertTrue(DuckDbDetection.isDuckDb(null, "JDBC:DuckDB:memory"))
    }

    @Test fun `does not match other drivers`() {
        assertFalse(DuckDbDetection.isDuckDb("org.postgresql.Driver", "jdbc:postgresql://localhost/foo"))
        assertFalse(DuckDbDetection.isDuckDb("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/foo"))
        assertFalse(DuckDbDetection.isDuckDb("org.sqlite.JDBC", "jdbc:sqlite:foo.db"))
    }

    @Test fun `does not match url that only mentions duckdb in path`() {
        // We require the jdbc:duckdb scheme prefix, so a generic path
        // referencing "duckdb" must not be treated as a DuckDB source.
        assertFalse(DuckDbDetection.isDuckDb(null, "jdbc:postgresql://host/duckdb_clone"))
    }

    @Test fun `returns false when both inputs are null or empty`() {
        assertFalse(DuckDbDetection.isDuckDb(null, null))
        assertFalse(DuckDbDetection.isDuckDb("", ""))
    }
}
