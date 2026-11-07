// the modules people can find from the modules page, read from modules/catalog.json in mochy's repo so the list grows without an app update

package com.mochyy.mochymusic.modules

import com.mochyy.mochymusic.module.ModuleFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogModule(
    val name: String,
    val description: String,
    /** the module's link, what "add" checks and installs */
    val url: String,
    val adds: Set<ModuleFeature>,
    val author: String?,
    /** a link to its icon, shown on its row before it's added */
    val icon: String? = null,
)

@Singleton
class ModuleCatalog @Inject constructor() {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    suspend fun load(): Result<List<CatalogModule>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = client.newCall(Request.Builder().url(CatalogUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
            val modules = JSONObject(text).optJSONArray("modules") ?: return@runCatching emptyList()
            (0 until modules.length()).mapNotNull { index ->
                val item = modules.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val adds = item.optJSONArray("adds")?.let { list ->
                    (0 until list.length()).mapNotNull { featureOf(list.optString(it)) }.toSet()
                }.orEmpty()
                CatalogModule(
                    name = item.optString("name").ifBlank { url },
                    description = item.optString("description"),
                    url = url,
                    adds = adds,
                    author = item.optString("author").takeIf { it.isNotBlank() },
                    icon = item.optString("icon").takeIf { it.startsWith("https://") },
                )
            }
        }
    }

    private fun featureOf(name: String): ModuleFeature? = when (name.lowercase()) {
        "audio", "source", "songs" -> ModuleFeature.AudioSource
        "lyrics" -> ModuleFeature.Lyrics
        "look", "theme" -> ModuleFeature.Look
        "metadata", "info" -> ModuleFeature.Metadata
        else -> null
    }

    private companion object {
        const val CatalogUrl = "https://raw.githubusercontent.com/realidkroo/Mochy-Music/main/modules/catalog.json"
    }
}
