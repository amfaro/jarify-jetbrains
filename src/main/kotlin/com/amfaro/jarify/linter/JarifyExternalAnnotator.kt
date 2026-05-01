package com.amfaro.jarify.linter

import com.amfaro.jarify.cli.JarifyCli
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

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
        val line: Int,
        val column: Int,
        val message: String,
        val severity: String,
    )

    data class AnnotationResult(val items: List<LintItem>, val document: Document)

    override fun collectInformation(file: PsiFile): CollectInfo? {
        val vfile = file.virtualFile ?: return null
        val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return null
        return CollectInfo(vfile.path, doc.text, doc)
    }

    override fun doAnnotate(info: CollectInfo): AnnotationResult {
        val args = listOf(
            "lint",
            "--format", "json",
            "--stdin-filename", info.filePath,
            "-",
        ) + JarifyCli.buildConfigArgs()
        // jarify exits non-zero when violations are found; still parse stdout.
        val result = JarifyCli.runWithStdin(args, info.text)
        return AnnotationResult(parseJson(result.stdout), info.document)
    }

    override fun apply(file: PsiFile, result: AnnotationResult, holder: AnnotationHolder) {
        val doc = result.document
        for (item in result.items) {
            val severity = when (item.severity.lowercase()) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WARNING
                "info" -> HighlightSeverity.INFORMATION
                "hint" -> HighlightSeverity.WEAK_WARNING
                else -> HighlightSeverity.ERROR
            }
            holder.newAnnotation(severity, item.message)
                .range(toTextRange(doc, item.line, item.column))
                .create()
        }
    }

    private fun toTextRange(doc: Document, line1: Int, col1: Int): TextRange {
        if (doc.lineCount == 0) return TextRange(0, 0)
        val line = (line1 - 1).coerceIn(0, doc.lineCount - 1)
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val col = (col1 - 1).coerceAtLeast(0)
        val start = (lineStart + col).coerceAtMost(lineEnd)
        val end = (start + 1).coerceAtMost(lineEnd).coerceAtLeast(start)
        return TextRange(start, end)
    }

    private fun parseJson(json: String): List<LintItem> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val raw: List<Map<String, Any?>> = Gson().fromJson(trimmed, type) ?: return emptyList()
            raw.mapNotNull { entry ->
                val line = (entry["line"] as? Number)?.toInt() ?: return@mapNotNull null
                val col = (entry["column"] as? Number)?.toInt() ?: return@mapNotNull null
                val msg = entry["message"] as? String ?: return@mapNotNull null
                val sev = entry["severity"] as? String ?: "error"
                LintItem(line, col, msg, sev)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
