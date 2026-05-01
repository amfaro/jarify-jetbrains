package com.amfaro.jarify.linter

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

    @Suppress("UNCHECKED_CAST")
    private fun parse(json: String): List<JarifyExternalAnnotator.LintItem> =
        parseJson.invoke(annotator, json) as List<JarifyExternalAnnotator.LintItem>

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

    @Test fun `skips entries missing required fields`() {
        val json = """[{"file":"x.sql","message":"no position"},{"line":1,"column":1,"message":"ok"}]"""
        val items = parse(json)
        assertEquals(1, items.size)
        assertEquals("ok", items.single().message)
    }
}
