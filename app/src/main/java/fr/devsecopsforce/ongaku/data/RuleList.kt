package fr.devsecopsforce.ongaku.data

/**
 * Liste par règle : ses morceaux sont ceux de la bibliothèque qui remplissent toutes ses conditions,
 * recalculés à chaque fois. Elle ne garde aucun identifiant de morceau. Une condition vide (texte
 * vide, nombre à 0) ne filtre rien.
 */
data class RuleList(
    val id: String,
    val name: String,
    /** Genre exact, sans tenir compte de la casse ([genreNames]). */
    val genre: String = "",
    val yearFrom: Int = 0,
    val yearTo: Int = 0,
    val minSeconds: Int = 0,
    val maxSeconds: Int = 0,
    /** Dossier, sous-dossiers compris ([FolderTree]). */
    val folder: String = "",
) {
    fun matches(track: Track): Boolean {
        if (genre.isNotBlank() && genreNames(track.genre).none { it.equals(genre.trim(), ignoreCase = true) }) return false
        if (yearFrom > 0 && track.year < yearFrom) return false
        if (yearTo > 0 && (track.year == 0 || track.year > yearTo)) return false
        val seconds = track.durationMs / 1000
        if (minSeconds > 0 && seconds < minSeconds) return false
        if (maxSeconds > 0 && seconds > maxSeconds) return false
        if (folder.isNotBlank() && !FolderTree.isUnder(FolderTree.normalize(track.folder), FolderTree.normalize(folder))) return false
        return true
    }

    /** Morceaux de la liste : par artiste, puis par album, puis dans l'ordre des pistes. */
    fun tracks(library: List<Track>): List<Track> =
        library.filter(::matches)
            .sortedWith(compareBy({ it.artist.searchKey() }, { it.album.searchKey() }, { it.trackNumber }, { it.title.searchKey() }))

    /** Au moins une condition : sinon la liste reprendrait toute la bibliothèque. */
    val hasRule: Boolean
        get() = genre.isNotBlank() || yearFrom > 0 || yearTo > 0 || minSeconds > 0 || maxSeconds > 0 || folder.isNotBlank()

    /** Bornes dans le bon ordre : une plage impossible donnerait une liste toujours vide. */
    val rangesValid: Boolean
        get() = (yearFrom == 0 || yearTo == 0 || yearFrom <= yearTo) &&
            LibraryFilter.isValidDuration(minSeconds, maxSeconds)
}
