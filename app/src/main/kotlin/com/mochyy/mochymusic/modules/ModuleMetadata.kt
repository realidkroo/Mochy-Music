// the song facts modules that are on, asked together for about song; the first one in the modules' order to know a fact wins it

package com.mochyy.mochymusic.modules

import com.mochyy.mochymusic.module.MetadataModule
import com.mochyy.mochymusic.module.TrackMetadata
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** every module's facts laid together, and the name of the module whose "about" text it is */
data class ModuleFacts(val metadata: TrackMetadata, val aboutFrom: String?)

object ModuleMetadata {
    /** set by the registry on every refresh: the switched-on modules that add song facts, in the modules' order */
    @Volatile
    var sources: List<MetadataModule> = emptyList()

    /** null when no module is on or none knew the song; a module that takes too long is left out */
    suspend fun lookup(track: TrackQuery): ModuleFacts? {
        val asked = sources
        if (asked.isEmpty()) return null
        val answers = coroutineScope {
            asked.map { module ->
                async {
                    val found = withTimeoutOrNull(AnswerBudgetMs) {
                        runCatching { module.metadata(track) }
                            .onFailure { Timber.tag(Tag).w(it, "${module.info.name} failed for ${track.title}") }
                            .getOrNull()
                    }
                    found?.let { module.info.name to it }
                }
            }.awaitAll().filterNotNull()
        }
        if (answers.isEmpty()) return null
        val all = answers.map { it.second }
        val merged = TrackMetadata(
            album = all.firstNotNullOfOrNull { it.album },
            releaseDate = all.firstNotNullOfOrNull { it.releaseDate },
            genres = all.firstOrNull { it.genres.isNotEmpty() }?.genres.orEmpty(),
            writers = all.firstOrNull { it.writers.isNotEmpty() }?.writers.orEmpty(),
            producers = all.firstOrNull { it.producers.isNotEmpty() }?.producers.orEmpty(),
            label = all.firstNotNullOfOrNull { it.label },
            about = all.firstNotNullOfOrNull { it.about?.takeIf(String::isNotBlank) },
            coverUrl = all.firstNotNullOfOrNull { it.coverUrl },
            isrc = all.firstNotNullOfOrNull { it.isrc },
        )
        return ModuleFacts(merged, answers.firstOrNull { !it.second.about.isNullOrBlank() }?.first)
    }

    private const val Tag = "ModuleMetadata"
    // about song waits this long for a module before showing what the others had
    private const val AnswerBudgetMs = 12_000L
}
