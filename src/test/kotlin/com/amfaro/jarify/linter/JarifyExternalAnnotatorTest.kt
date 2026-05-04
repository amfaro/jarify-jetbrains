package com.amfaro.jarify.linter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Unit tests for the private parseJson helper. Uses reflection so we don't
 * widen the visibility surface for production callers.
 */
class JarifyExternalAnnotatorTest {

    private val annotator = JarifyExternalAnnotator()
    private val parseJson: Method =
        JarifyExternalAnnotator::class.java
            .getDeclaredMethod("parseJson", String::class.java)
            .apply { isAccessible = true }
    private val toTextRange: Method =
        JarifyExternalAnnotator::class.java
            .getDeclaredMethod(
                "toTextRange",
                Document::class.java,
                Int::class.javaObjectType,
                Int::class.javaObjectType,
            )
            .apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun parse(json: String): List<JarifyExternalAnnotator.LintItem> =
        parseJson.invoke(annotator, json) as List<JarifyExternalAnnotator.LintItem>

    private fun textRange(text: String, line: Int?, column: Int?): TextRange =
        toTextRange.invoke(annotator, DocumentImpl(text), line, column) as TextRange

    @Test fun `empty string returns empty list`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   ").isEmpty())
    }

    @Test fun `malformed json returns empty list`() {
        assertTrue(parse("not json").isEmpty())
        assertTrue(parse("{bad}").isEmpty())
    }

    @Test fun `parses a single diagnostic`() {
        val json = """[{"file":"x.sql","line":3,"column":7,"message":"oops","severity":"error"}]"""
        val items = parse(json)
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(3, item.line)
        assertEquals(7, item.column)
        assertEquals("oops", item.message)
        assertEquals("error", item.severity)
    }

    @Test fun `defaults missing severity to error`() {
        val json = """[{"file":"x.sql","line":1,"column":1,"message":"m"}]"""
        assertEquals("error", parse(json).single().severity)
    }

    @Test fun `preserves diagnostic with null line and column`() {
        val json = """[{"file":"x.sql","line":null,"column":null,"message":"syntax error","severity":"error"}]"""
        val item = parse(json).single()
        assertEquals(null, item.line)
        assertEquals(null, item.column)
        assertEquals("syntax error", item.message)
        assertEquals("error", item.severity)
    }

    @Test fun `falls back to message position when json position is null`() {
        val json = """[{"file":"x.sql","line":null,"column":null,"message":"Expected table name. Line 1, Col: 11."}]"""
        val item = parse(json).single()
        assertEquals(1, item.line)
        assertEquals(11, item.column)
    }

    @Test fun `falls back to message position with case and colon variants`() {
        val json = """[{"file":"x.sql","message":"parse failed: line: 2, col 4"}]"""
        val item = parse(json).single()
        assertEquals(2, item.line)
        assertEquals(4, item.column)
    }

    @Test fun `prefers json position over message position`() {
        val json = """[{"file":"x.sql","line":3,"column":7,"message":"Line 1, Col: 11"}]"""
        val item = parse(json).single()
        assertEquals(3, item.line)
        assertEquals(7, item.column)
    }

    @Test fun `skips entries missing required message only`() {
        val json = """[{"file":"x.sql","line":1,"column":1},{"file":"x.sql","message":"no position"},{"line":1,"column":1,"message":"ok"}]"""
        val items = parse(json)
        assertEquals(2, items.size)
        assertEquals("no position", items[0].message)
        assertEquals(null, items[0].line)
        assertEquals(null, items[0].column)
        assertEquals("ok", items[1].message)
    }

    @Test fun `fallback range highlights file start when position is missing`() {
        assertEquals(TextRange(0, 1), textRange("select 1", null, null))
    }

    @Test fun `fallback range is empty at file start for empty document`() {
        assertEquals(TextRange(0, 0), textRange("", null, null))
    }
}
