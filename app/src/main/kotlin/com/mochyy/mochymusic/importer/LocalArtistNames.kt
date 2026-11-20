// LocalArtistNames.kt

package com.mochyy.mochymusic.importer

/** one artist tag as the artists it names, so a song by two artists shows up on both their pages */
internal object LocalArtistNames {
    // always two artists: "A; B", "A / B", "A feat. B", "A (ft. B)"
    private val AlwaysApart = Regex("""\s*(?:;|\u0000|\s/\s|\(?\b(?:feat\.?|ft\.|featuring)\s)\s*""", RegexOption.IGNORE_CASE)

    // two artists only when each side already is one: "A, B", "A & B", "A x B" and "A and B" are just as often one band's name ("Simon & Garfunkel")
    private val MaybeApart = Regex("""\s*(?:,|\s&\s|\sx\s|\sand\s)\s*""", RegexOption.IGNORE_CASE)

    /** the artists in tag, in order; known tells whether the library already has an artist of that name, and the album's artist always counts */
    fun split(tag: String, albumArtist: String?, known: (String) -> Boolean): List<String> {
        val names = tag.split(AlwaysApart)
            .map { it.trim().trim('(', ')', '[', ']').trim() }
            .filter { it.isNotEmpty() }
            .flatMap { part ->
                val pieces = part.split(MaybeApart).map { it.trim() }.filter { it.isNotEmpty() }
                val eachIsAnArtist = pieces.size > 1 && pieces.all { it.equals(albumArtist?.trim(), ignoreCase = true) || known(it) }
                if (eachIsAnArtist) pieces else listOf(part)
            }
            .distinctBy { it.lowercase() }
        return names.ifEmpty { listOf(tag.trim()) }
    }
}
