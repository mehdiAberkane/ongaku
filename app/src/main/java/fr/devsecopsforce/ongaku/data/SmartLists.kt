package fr.devsecopsforce.ongaku.data

/** Listes calculées à partir de la bibliothèque et des écoutes ; jamais enregistrées. */
enum class SmartList {
    RECENTLY_ADDED,
    MOST_PLAYED,
    RECENTLY_PLAYED,
    NEVER_PLAYED,
    LEAST_PLAYED,

    /** Favoris pas écoutés depuis [SmartLists.REDISCOVER_AFTER_MS]. */
    REDISCOVER,
}

object SmartLists {
    /** Tailles proposées dans les réglages. */
    val SIZES = listOf(25, 50, 100, 200)
    const val DEFAULT_SIZE = 50

    /** Un favori redevient une redécouverte après un mois sans écoute. */
    const val REDISCOVER_AFTER_MS = 30L * 24 * 60 * 60 * 1000

    fun size(stored: Int): Int = if (stored in SIZES) stored else DEFAULT_SIZE

    /**
     * Morceaux d'une liste, au plus [size]. [counts] : nombre d'écoutes ; [lastPlayed] : heure de la
     * dernière écoute, absente pour un morceau écouté avant que cet historique n'existe.
     */
    fun build(
        kind: SmartList,
        tracks: List<Track>,
        counts: Map<Long, Int>,
        lastPlayed: Map<Long, Long>,
        favorites: Set<Long>,
        nowMs: Long,
        size: Int,
    ): List<Track> {
        val plays = { track: Track -> counts[track.id] ?: 0 }
        val last = { track: Track -> lastPlayed[track.id] ?: 0L }
        val byTitle = compareBy<Track> { it.title.searchKey() }
        val list = when (kind) {
            SmartList.RECENTLY_ADDED -> tracks.sortedWith(compareByDescending<Track> { it.dateAddedSeconds }.then(byTitle))
            SmartList.MOST_PLAYED -> tracks.filter { plays(it) > 0 }
                .sortedWith(compareByDescending(plays).then(compareByDescending(last)).then(byTitle))
            SmartList.RECENTLY_PLAYED -> tracks.filter { it.id in lastPlayed }
                .sortedWith(compareByDescending(last).then(byTitle))
            // Les plus récents d'abord : ce sont souvent ceux qu'on n'a pas encore pris le temps d'écouter.
            SmartList.NEVER_PLAYED -> tracks.filter { plays(it) == 0 }
                .sortedWith(compareByDescending<Track> { it.dateAddedSeconds }.then(byTitle))
            SmartList.LEAST_PLAYED -> tracks.filter { plays(it) > 0 }
                .sortedWith(compareBy(plays).then(compareBy(last)).then(byTitle))
            // Le plus longtemps oublié d'abord ; sans date, avant cet historique.
            SmartList.REDISCOVER -> tracks.filter { it.id in favorites && nowMs - last(it) >= REDISCOVER_AFTER_MS }
                .sortedWith(compareBy(last).then(byTitle))
        }
        return list.take(size)
    }
}
