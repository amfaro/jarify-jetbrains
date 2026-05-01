package com.amfaro.jarify.formatter

import com.amfaro.jarify.cli.JarifyCli
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.psi.PsiFile
import java.util.EnumSet

/**
 * Pipes the active document through `jarify fmt --stdin-filename <file> -` and
 * replaces the buffer with stdout. Hooks into Code > Reformat Code (Ctrl+Alt+L)
 * and the IDE's format-on-save action.
 */
class JarifyAsyncFormattingService : AsyncDocumentFormattingService() {

    override fun getFeatures(): MutableSet<FormattingService.Feature> =
        EnumSet.of(FormattingService.Feature.FORMAT_FRAGMENTS)

    override fun canFormat(file: PsiFile): Boolean {
        // Walk the language hierarchy — covers SQL dialects (MySQL, PostgreSQL, etc.)
        // that extend SqlLanguage but have their own IDs.
        var lang: com.intellij.lang.Language? = file.language
        while (lang != null) {
            if (lang.id.equals("SQL", ignoreCase = true)) return true
            lang = lang.baseLanguage
        }
        // Fallback: match by file extension for plain-text .sql files.
        val ext = file.virtualFile?.extension?.lowercase() ?: return false
        return ext in setOf("sql", "ddl")
    }

    override fun getName(): String = "Jarify"

    override fun getNotificationGroupId(): String = "Jarify"

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val ioFile = request.ioFile ?: return null
        val text = request.documentText

        return object : FormattingTask {
            @Volatile
            private var cancelled = false

            override fun run() {
                if (cancelled) return
                val args = listOf(
                    "fmt",
                    "--stdin-filename", ioFile.absolutePath,
                    "-",
                ) + JarifyCli.buildConfigArgs()
                val result = JarifyCli.runWithStdin(args, text)
                if (cancelled) return
                if (result.exitCode == 0) {
                    request.onTextReady(result.stdout)
                } else {
                    val msg = result.stderr.trim()
                        .ifBlank { "jarify fmt exited with code ${result.exitCode}" }
                    request.onError("Jarify", msg)
                }
            }

            override fun cancel(): Boolean {
                cancelled = true
                return true
            }

            override fun isRunUnderProgress(): Boolean = true
        }
    }
}
