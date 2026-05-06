package com.amfaro.jarify.linter

import com.amfaro.jarify.cli.JarifyCli
import com.amfaro.jarify.duckdb.DuckDbDetection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.io.File

/**
 * Runs `jarify lint --format json --stdin-filename <file> -` over the buffer
 * and converts each diagnostic into an IntelliJ annotation. Fires on file
 * open, edit (debounced by the platform), and after save — the standard
 * ExternalAnnotator lifecycle.
 */
class JarifyExternalAnnotator :
    ExternalAnnotator<JarifyExternalAnnotator.CollectInfo, JarifyExternalAnnotator.AnnotationResult>() {

    data class CollectInfo(val filePath: String, val text: String, val document: Document)

    data class LintItem(
        val line: Int?,
        val column: Int?,
        val message: String,
        val severity: String,
    )

    data class AnnotationResult(val items: List<LintItem>, val document: Document)

    override fun collectInformation(file: PsiFile): CollectInfo? {
        if (!DuckDbDetection.shouldRun(file.project, file)) return null
        val vfile = file.virtualFile ?: return null
        val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return null
        return CollectInfo(vfile.path, doc.text, doc)
    }

    override fun doAnnotate(info: CollectInfo): AnnotationResult {
        val configArgs = JarifyCli.buildConfigArgs()
        val args = listOf(
            "lint",
            "--format", "json",
            "--stdin-filename", info.filePath,
            "-",
        ) + configArgs
        if (LOG.isDebugEnabled) {
            LOG.debug(
                "Running jarify lint; fileName=${File(info.filePath).name}; textLength=${info.text.length}; " +
                    "configArgCount=${configArgs.size}",
            )
        }
        // jarify exits non-zero when violations are found; still parse stdout.
        val result = JarifyCli.runWithStdin(args, info.text)
        val items = parseJson(result.stdout)
        if (LOG.isDebugEnabled) {
            LOG.debug(
                "jarify lint completed; fileName=${File(info.filePath).name}; exitCode=${result.exitCode}; " +
                    "stdoutLength=${result.stdout.length}; stderrLength=${result.stderr.length}; diagnostics=${items.size}",
            )
        }
        return AnnotationResult(items, info.document)
    }

    override fun apply(file: PsiFile, result: AnnotationResult, holder: AnnotationHolder) {
        val doc = result.document
        for (item in result.items) {
            val severity = when (item.severity.lowercase()) {
                "error" -> HighlightSeverity.ERROR
                "warning", "warn" -> HighlightSeverity.WARNING
                "info" -> HighlightSeverity.INFORMATION
                "hint" -> HighlightSeverity.WEAK_WARNING
                else -> HighlightSeverity.WARNING
            }
            holder.newAnnotation(severity, item.message)
                .range(toTextRange(doc, item.line, item.column))
                .create()
        }
    }

    private fun toTextRange(doc: Document, line1: Int?, col1: Int?): TextRange {
        if (line1 == null || col1 == null) return fileStartRange(doc)
        if (doc.lineCount == 0) return TextRange(0, 0)
        val line = (line1 - 1).coerceIn(0, doc.lineCount - 1)
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val col = (col1 - 1).coerceAtLeast(0)
        val start = (lineStart + col).coerceAtMost(lineEnd)
        val end = (start + 1).coerceAtMost(lineEnd).coerceAtLeast(start)
        return TextRange(start, end)
    }

    private fun fileStartRange(doc: Document): TextRange =
        TextRange(0, minOf(1, doc.textLength))

    private fun parseJson(json: String): List<LintItem> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val raw: List<Map<String, Any?>> = Gson().fromJson(trimmed, type) ?: return emptyList()
            var skipped = 0
            var fallbackFromMessage = 0
            var noPosition = 0
            val items = raw.mapNotNull { entry ->
                val msg = entry["message"] as? String
                if (msg == null) {
                    skipped++
                    return@mapNotNull null
                }
                val jsonLine = (entry["line"] as? Number)?.toInt()
                val jsonCol = (entry["column"] as? Number)?.toInt()
                val (line, col) = if (jsonLine != null && jsonCol != null) {
                    jsonLine to jsonCol
                } else {
                    val fallback = parsePositionFromMessage(msg)
                    if (fallback != null) {
                        fallbackFromMessage++
                        fallback
                    } else {
                        noPosition++
                        null to null
                    }
                }
                val sev = entry["severity"] as? String ?: "error"
                LintItem(line, col, msg, sev)
            }
            LOG.debug(
                "Parsed jarify lint JSON; entries=${raw.size}; emitted=${items.size}; skipped=$skipped; " +
                    "fallbackFromMessage=$fallbackFromMessage; noPosition=$noPosition",
            )
            items
        } catch (e: Exception) {
            LOG.debug("Failed to parse jarify lint JSON; length=${trimmed.length}", e)
            emptyList()
        }
    }

    private fun parsePositionFromMessage(message: String): Pair<Int, Int>? {
        val match = MESSAGE_POSITION_REGEX.find(message) ?: return null
        val line = match.groupValues[1].toIntOrNull() ?: return null
        val col = match.groupValues[2].toIntOrNull() ?: return null
        return line to col
    }

    companion object {
        private val LOG = Logger.getInstance(JarifyExternalAnnotator::class.java)
        private val MESSAGE_POSITION_REGEX = Regex(
            """\bline\s*:?\s*(\d+)\s*,\s*col\s*:?\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
