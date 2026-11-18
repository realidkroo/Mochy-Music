package com.mochyy.mochymusic.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Check the selector and the Indonesian format strings against the actual resource tree. */
class LanguageResourcesTest {
    private val resDir = listOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }

    private fun children(file: File): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun values(folder: File): Map<String, Element> = folder.listFiles().orEmpty()
        .filter { it.extension == "xml" }
        .flatMap(::children)
        .filter { it.tagName in setOf("string", "plurals") && it.getAttribute("translatable") != "false" }
        .associateBy { it.getAttribute("name") }

    @Test fun `Korean covers every default resource`() {
        assertEquals(values(File(resDir, "values")).keys, values(File(resDir, "values-ko")).keys)
    }

    @Test fun `regional resources cannot hide existing Korean with English`() {
        val korean = values(File(resDir, "values-ko"))
        values(File(resDir, "values-ko-rKR")).forEach { (key, regional) ->
            if (korean[key]?.textContent?.contains(Regex("[가-힣]")) == true) {
                assertTrue("Regional resource hides Korean: $key", regional.textContent.contains(Regex("[가-힣]")))
            }
        }
    }

    @Test fun `Korean preserves default format arguments in each quantity`() {
        val english = values(File(resDir, "values"))
        val korean = values(File(resDir, "values-ko"))
        val argument = Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]")
        fun signature(element: Element) = argument.findAll(element.textContent).map { it.value }.sorted().toList()
        (english.keys intersect korean.keys).forEach { key ->
            val source = english.getValue(key)
            val translated = korean.getValue(key)
            assertEquals("Type of $key", source.tagName, translated.tagName)
            if (source.tagName == "plurals") {
                fun items(element: Element): Map<String, Element> {
                    val children = element.childNodes
                    return (0 until children.length).mapNotNull { children.item(it) as? Element }
                        .associateBy { it.getAttribute("quantity") }
                }
                val original = items(source)
                val localized = items(translated)
                assertTrue("Missing other in $key", "other" in localized)
                localized.forEach { (quantity, item) ->
                    assertEquals("Formatting in $key/$quantity", signature(original[quantity] ?: original.getValue("other")), signature(item))
                }
            } else {
                assertEquals("Formatting in $key", signature(source), signature(translated))
            }
        }
    }

    @Test fun `picker lists exactly the app translated resource locales`() {
        val array = children(File(resDir, "values/app_language_tags.xml"))
            .single { it.tagName == "string-array" && it.getAttribute("name") == "app_language_tags" }
        val nodes = array.childNodes
        val tags = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .map { it.textContent.trim() }
        val locales = resDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && values(it).isNotEmpty() }
            .map { folder ->
                val qualifier = folder.name.removePrefix("values-")
                val tag = if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").replace('+', '-')
                    else qualifier.replace("-r", "-")
                when (tag) { "in" -> "id"; "iw" -> "he"; else -> tag }
            }.toSet() + "en"
        assertEquals("Duplicate language tags", tags.size, tags.toSet().size)
        assertEquals(locales, tags.toSet())
    }

    @Test fun `Indonesian strings preserve the default format arguments`() {
        val english = values(File(resDir, "values"))
        val indonesian = values(File(resDir, "values-in"))
        val argument = Regex("%(?!%)(?:\\d+\\$)?[a-zA-Z]")
        fun placeholders(element: Element): List<String> {
            val text = if (element.tagName == "plurals") {
                val nodes = element.childNodes
                (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                    .first { it.getAttribute("quantity") == "other" }.textContent
            } else element.textContent
            return argument.findAll(text).map { it.value }.sorted().toList()
        }
        (english.keys intersect indonesian.keys).forEach { key ->
            val en = english.getValue(key)
            val id = indonesian.getValue(key)
            assertEquals("Type of $key", en.tagName, id.tagName)
            assertEquals("Formatting in $key", placeholders(en), placeholders(id))
        }
        assertTrue("Expected Indonesian resources", indonesian.size > 1000)
    }
}
