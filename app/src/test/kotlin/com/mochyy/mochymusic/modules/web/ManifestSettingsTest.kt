package com.mochyy.mochymusic.modules.web

import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.modules.skin.ActionTarget
import com.mochyy.mochymusic.modules.skin.PageBlock
import com.mochyy.mochymusic.modules.skin.SkinParser
import com.mochyy.mochymusic.modules.skin.appliedLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// a manifest's settings as fields, the looks and glass their picks bring, and the menu rows and settings pages a look can add
class ManifestSettingsTest {

    private fun manifest(text: String) = requireNotNull(WebModuleManifest.parse(text))

    private val glassy = manifest(
        """
        {
          "id": "liquid-glass", "name": "Liquid Glass", "icon": "icon.png", "banner": "https://example.com/banner.webp",
          "look": { "pureBlack": "false" },
          "glass": { "shader": "glass.agsl", "blur": 7, "margin": 22,
                     "uniforms": { "tintAlpha": 0.42, "spread": 0, "size": 99, "bad name": 1, "word": "x" } },
          "settings": [
            { "key": "style", "label": "Glass", "type": "select", "default": "liquid", "options": [
              { "value": "liquid", "label": "Liquid glass", "glass": { "blur": 7, "uniforms": { "tintAlpha": 0.42 } } },
              { "value": "clear", "label": "Clear glass", "glass": { "blur": 2.5, "uniforms": { "tintAlpha": 0.16 } } },
              { "value": "frost", "label": "Mochy's frost", "look": { "bottomBar": "FROSTED" } }
            ] },
            { "key": "fringe", "label": "Colour fringe", "default": true, "glass": { "uniforms": { "spread": 0.06 } } },
            { "key": "black", "label": "Pure black", "default": false, "look": { "pureBlack": "true" } },
            { "key": "token", "hint": "Paste it" }
          ],
          "pages": [
            { "id": "glass", "title": "Glass", "inSettings": true, "icon": "icon.png", "blocks": [ { "type": "setting", "key": "style" } ] }
          ],
          "actions": [
            { "title": "Read it on Genius", "link": "https://genius.com/search?q={artist} {title}" },
            { "title": "Open Glass", "page": "Glass" },
            { "title": "Live versions", "search": "{title} live" },
            { "title": "Nowhere", "link": "intent://evil" }
          ]
        }
        """,
    )

    private val files = File("/files/liquid-glass")

    private fun skin(files: File? = this.files) =
        SkinParser.parse("local.liquid-glass", "Liquid Glass", glassy.player, glassy.font, glassy.pages, files, null, glassy.actions, glassy.glass)

    @Test
    fun settingsBecomeFieldsOfTheRightKind() {
        val fields = ManifestSettings(glassy).fields
        assertEquals(listOf("style", "fringe", "black", "token"), fields.map { it.id })
        assertEquals(listOf(FieldKind.Select, FieldKind.Toggle, FieldKind.Toggle, FieldKind.Text), fields.map { it.kind })
        assertEquals(listOf("Liquid glass", "Clear glass", "Mochy's frost"), fields[0].options.map { it.label })
        assertEquals("liquid", fields[0].default)
        assertEquals("true", fields[1].default)
        assertEquals("Token", fields[3].label)
        assertEquals("Paste it", fields[3].hint)
    }

    @Test
    fun aGlassOnlyComesFromAZipAndKeepsToItsOwnUniforms() {
        assertNull(skin(files = null)?.glass)
        val glass = requireNotNull(skin()?.glass)
        assertEquals("glass.agsl", glass.shader)
        assertEquals(7f, glass.blurDp)
        assertEquals(22f, glass.marginDp)
        // mochy's own uniforms, bad names and words are left out
        assertEquals(mapOf("tintAlpha" to 0.42f, "spread" to 0f), glass.uniforms)
        assertTrue(skin()!!.restyles)
    }

    @Test
    fun thePicksChangeTheGlassAndTheLook() {
        val settings = ManifestSettings(glassy)
        val base = skin()?.glass
        val liquid = requireNotNull(settings.glass(base))
        assertEquals(0.06f, liquid.uniforms["spread"])
        assertEquals(0.42f, liquid.uniforms["tintAlpha"])
        // a glass switches the bars over to it, unless a pick says frosted
        assertEquals("MODULE", appliedLook(settings.look(glassy.lookValues), skin())["bottomBar"])

        settings.values = mapOf("style" to "clear", "fringe" to "false")
        val clear = requireNotNull(settings.glass(base))
        assertEquals(2.5f, clear.blurDp)
        assertEquals(0.16f, clear.uniforms["tintAlpha"])
        assertEquals(0f, clear.uniforms["spread"])

        settings.values = mapOf("style" to "frost", "black" to "true")
        val look = appliedLook(settings.look(glassy.lookValues), skin())
        assertEquals("FROSTED", look["bottomBar"])
        assertEquals("true", look["pureBlack"])
    }

    @Test
    fun settingsThatChangeTheLookOrGlassMakeItALook() {
        assertTrue(glassy.changesLook)
        val plain = manifest("""{ "id": "svc", "resources": ["search"], "settings": [ { "key": "region", "options": ["us", "jp"] } ] }""")
        assertFalse(plain.changesLook)
        assertTrue(plain.playsSongs)
    }

    @Test
    fun metadataIsAResource() {
        val facts = manifest("""{ "id": "facts", "resources": ["metadata"] }""")
        assertTrue(facts.hasMetadata)
        assertFalse(facts.playsSongs)
    }

    @Test
    fun menuRowsKeepToTheWebAPageOrASearch() {
        val actions = requireNotNull(skin()).actions
        assertEquals(3, actions.size)
        assertTrue(actions[0].target is ActionTarget.Link)
        assertEquals(ActionTarget.Page("glass"), actions[1].target)
        assertEquals(ActionTarget.Search("{title} live"), actions[2].target)
    }

    @Test
    fun aPageCanSitInSettingsAndHoldASetting() {
        val page = requireNotNull(skin()).pages.single()
        assertTrue(page.inSettings)
        assertEquals("icon.png", page.icon)
        assertEquals(PageBlock.Setting("style"), page.blocks.single())
    }

    @Test
    fun picturesResolveNextToTheManifestOrInTheZip() {
        assertEquals("https://example.com/liquid/icon.png", resolveModuleArt(glassy.icon, null, "https://example.com/liquid"))
        assertEquals("https://example.com/banner.webp", resolveModuleArt(glassy.banner, null, "https://example.com/liquid"))
        assertEquals(File(files, "icon.png").path, resolveModuleArt(glassy.icon, files, null))
        assertEquals(null, resolveModuleArt("../secret.png", files, null))
    }
}
