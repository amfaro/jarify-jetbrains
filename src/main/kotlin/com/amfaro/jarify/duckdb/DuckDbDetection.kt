package com.amfaro.jarify.duckdb

import com.amfaro.jarify.settings.JarifySettings
import com.intellij.database.console.JdbcConsole
import com.intellij.database.model.DasDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Phase 1 gate (issue #22) for the linter and formatter entry points. */
internal object DuckDbDetection {

    fun shouldRun(project: Project, file: PsiFile): Boolean {
        if (!JarifySettings.getInstance().onlyForDuckDb) return true
        if (!projectHasDuckDbDataSource(project)) {
            LOG.debug("Skipping jarify: onlyForDuckDb is on but no DuckDB data source is configured")
            return false
        }
        if (consoleAttachedIsDuckDb(project, file) == false) {
            LOG.debug("Skipping jarify: SQL console is attached to a non-DuckDB data source")
            return false
        }
        return true
    }

    fun isDuckDb(driverClass: String?, url: String?): Boolean =
        driverClass?.contains("duckdb", ignoreCase = true) == true ||
            url?.startsWith("jdbc:duckdb", ignoreCase = true) == true

    private fun isDuckDb(ds: DasDataSource?): Boolean? {
        val cfg = ds?.connectionConfig ?: return null
        return isDuckDb(cfg.driverClass, cfg.url)
    }

    // Fails open: a transient platform error here would otherwise silently disable the plugin.
    private fun projectHasDuckDbDataSource(project: Project): Boolean =
        try {
            DbPsiFacade.getInstance(project).dataSources.any { isDuckDb(it) == true }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("Failed to enumerate project data sources; allowing run", e)
            true
        }

    /**
     * `true`/`false` if the file is the buffer of an active SQL console with a
     * data source attached, or `null` when the file is not a console buffer or
     * the lookup failed — in which case the project-wide answer wins.
     */
    private fun consoleAttachedIsDuckDb(project: Project, file: PsiFile): Boolean? {
        val vfile = file.virtualFile ?: return null
        return try {
            val ds = JdbcConsole.getActiveConsoles(project)
                .firstOrNull { it.virtualFile == vfile }
                ?.dataSource
                ?: return null
            isDuckDb(ds)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("Failed to resolve console data source for file; deferring to project check", e)
            null
        }
    }

    private val LOG = Logger.getInstance(DuckDbDetection::class.java)
}
