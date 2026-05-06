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

    // --- decide() — issue #23 acceptance for per-file data-source gating ---

    @Test fun `decide skips when project has no duckdb data source`() {
        assertFalse(DuckDbDetection.decide(projectHasDuckDb = false) { null })
    }

    @Test fun `decide does not invoke console lookup when project lacks duckdb`() {
        var consoleLookupCalled = false
        DuckDbDetection.decide(projectHasDuckDb = false) {
            consoleLookupCalled = true
            true
        }
        assertFalse("console lookup must short-circuit", consoleLookupCalled)
    }

    @Test fun `decide runs for standalone files when project has duckdb`() {
        // Standalone .sql files (not attached to a console) return null and
        // must keep Phase 1 behavior: run if the project has any DuckDB source.
        assertTrue(DuckDbDetection.decide(projectHasDuckDb = true) { null })
    }

    @Test fun `decide runs when console is attached to duckdb`() {
        assertTrue(DuckDbDetection.decide(projectHasDuckDb = true) { true })
    }

    @Test fun `decide skips when console is attached to a non-duckdb source`() {
        // Mixed-data-source case: project has both DuckDB and Postgres, file
        // is the buffer of a Postgres console. Phase 2 must skip.
        assertFalse(DuckDbDetection.decide(projectHasDuckDb = true) { false })
    }
}
