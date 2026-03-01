package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockRenderResolverTest {
    @Test
    fun resolve_html_language() {
        val target = CodeBlockRenderResolver.resolve(
            language = "HTML",
            code = "<div>Hello</div>"
        )
        assertNotNull(target)
        assertEquals("html", target?.normalizedLanguage)
        assertEquals(CodeBlockRenderType.HTML, target?.renderType)
    }

    @Test
    fun resolve_svg_language() {
        val target = CodeBlockRenderResolver.resolve(
            language = "svg",
            code = "<svg></svg>"
        )
        assertNotNull(target)
        assertEquals("svg", target?.normalizedLanguage)
        assertEquals(CodeBlockRenderType.SVG, target?.renderType)
    }

    @Test
    fun resolve_xml_with_svg_content() {
        val target = CodeBlockRenderResolver.resolve(
            language = "xml",
            code = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg width="100" height="100"></svg>
            """.trimIndent()
        )
        assertNotNull(target)
        assertEquals("xml", target?.normalizedLanguage)
        assertEquals(CodeBlockRenderType.SVG, target?.renderType)
    }

    @Test
    fun resolve_xml_without_svg_content() {
        val target = CodeBlockRenderResolver.resolve(
            language = "xml",
            code = "<note><to>user</to></note>"
        )
        assertNull(target)
    }

    @Test
    fun resolve_other_language_returns_null() {
        val target = CodeBlockRenderResolver.resolve(
            language = "javascript",
            code = "console.log('hello');"
        )
        assertNull(target)
    }

    @Test
    fun build_html_for_html_code_is_wrapped_and_keeps_payload() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "html",
            renderType = CodeBlockRenderType.HTML
        )
        val code = "<span>hello</span>"
        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)
        assertTrue(html.contains("<!DOCTYPE html>", ignoreCase = true))
        assertTrue(html.contains("<meta name=\"viewport\""))
        assertTrue(html.contains(CODE_BLOCK_HEIGHT_BRIDGE_NAME))
        assertTrue(html.contains("--TH-viewport-height"))
        assertTrue(html.contains(code))
    }

    @Test
    fun build_html_for_svg_code_is_wrapped_and_keeps_payload() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "svg",
            renderType = CodeBlockRenderType.SVG
        )
        val code = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg width="80" height="80"></svg>
        """.trimIndent()
        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)

        assertTrue(html.contains("<!DOCTYPE html>", ignoreCase = true))
        assertTrue(html.contains("<body>"))
        assertTrue(html.contains(code))
    }

    @Test
    fun build_html_converts_css_min_height_vh_to_viewport_variable() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "html",
            renderType = CodeBlockRenderType.HTML
        )
        val code = """
            <style>
              .box { min-height: 100vh; }
              .half { min-height: 50vh; }
            </style>
            <div class="box half"></div>
        """.trimIndent()

        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)

        assertTrue(html.contains("min-height: var(--TH-viewport-height)"))
        assertTrue(html.contains("min-height: calc(var(--TH-viewport-height) * 0.5)"))
    }

    @Test
    fun build_html_converts_inline_style_min_height_vh() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "html",
            renderType = CodeBlockRenderType.HTML
        )
        val code = "<div style=\"padding: 4px; min-height: 75vh;\">inline</div>"

        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)

        assertTrue(html.contains("min-height: calc(var(--TH-viewport-height) * 0.75)"))
    }

    @Test
    fun build_html_converts_javascript_min_height_vh() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "html",
            renderType = CodeBlockRenderType.HTML
        )
        val code = """
            <script>
              el.style.minHeight = "80vh";
              el.style.setProperty('min-height', '25vh');
            </script>
        """.trimIndent()

        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)

        assertTrue(html.contains("""el.style.minHeight = "calc(var(--TH-viewport-height) * 0.8)""""))
        assertTrue(html.contains("""setProperty('min-height', 'calc(var(--TH-viewport-height) * 0.25)')"""))
    }

    @Test
    fun build_html_does_not_convert_non_min_height_vh() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "html",
            renderType = CodeBlockRenderType.HTML
        )
        val code = """
            <style>.box{height:100vh;}</style>
            <div class="box"></div>
        """.trimIndent()

        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)

        assertTrue(html.contains("height:100vh"))
        assertFalse(html.contains("height:var(--TH-viewport-height)"))
    }

    @Test
    fun build_html_uses_render_root_height_measurement_for_short_inline_block() {
        val target = CodeBlockRenderTarget(
            normalizedLanguage = "html",
            renderType = CodeBlockRenderType.HTML
        )
        val code = """
            <span style="display: inline-block; width: 100%; max-width: 95vw; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 8px; padding: 4px 10px; margin: 3px 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; color: #fff; font-size: 0.85em; box-shadow: 0 1px 4px rgba(102, 126, 234, 0.4); white-space: normal; line-height: 1.4;">
              <strong style="margin-right: 2px;">⏰ 时间：</strong>$1
              <strong style="margin: 0 2px 0 6px;">📍 地点：</strong>$2
            </span>
        """.trimIndent()

        val html = CodeBlockRenderResolver.buildHtmlForWebView(target, code)

        assertTrue(html.contains("display: inline-block"))
        assertTrue(html.contains("var nextHeight = readContentHeight();"))
        assertTrue(html.contains("function readVisualContentHeight()"))
        assertTrue(html.contains("function readContentHeight()"))
        assertTrue(html.contains("body.getBoundingClientRect()"))
        assertFalse(html.contains("doc ? doc.offsetHeight"))
        assertFalse(html.contains("body ? body.offsetHeight"))
        assertFalse(html.contains("RH_RENDER_ROOT"))
    }
}
