package fr.devsecopsforce.ongaku.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import fr.devsecopsforce.ongaku.R
import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Forme de comparaison : sans accents, en minuscules, sans espaces autour. « Été » devient « ete ». */
fun String.searchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase().trim()

/** Lettre de l'index alphabétique : A à Z, ou « # » pour tout le reste (chiffres, symboles…). */
fun String.indexLetter(): Char {
    val first = searchKey().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

enum class TrackSort(@StringRes val label: Int, val alphabetical: Boolean) {
    TITLE(R.string.sort_title, true),
    ARTIST(R.string.sort_artist, true),
    ALBUM(R.string.sort_album, true),
    DATE_ADDED(R.string.sort_date_added, false),
    DURATION(R.string.sort_duration, false),
    PLAYS(R.string.sort_plays, false),
}

enum class AlbumSort(@StringRes val label: Int, val alphabetical: Boolean) {
    NAME(R.string.sort_name, true),
    ARTIST(R.string.sort_artist, true),
    YEAR(R.string.sort_year, false),
}

private fun <T> List<T>.sortedByKey(key: (T) -> String): List<T> =
    map { key(it) to it }.sortedBy { it.first }.map { it.second }

/** Texte sur lequel porte un tri alphabétique : sert aussi à placer les lettres de l'index. */
fun Track.sortText(sort: TrackSort): String = when (sort) {
    TrackSort.ARTIST -> artist
    TrackSort.ALBUM -> album
    else -> title
}

fun List<Track>.sorted(sort: TrackSort, playCounts: Map<Long, Int>): List<Track> {
    // Départage stable des égalités : par titre.
    val byTitle = sortedByKey { it.title.searchKey() }
    return when (sort) {
        TrackSort.TITLE -> byTitle
        TrackSort.ARTIST -> byTitle.sortedByKey { it.artist.searchKey() }
        TrackSort.ALBUM -> byTitle.sortedWith(compareBy { it.trackNumber })
            .sortedByKey { it.album.searchKey() }
        TrackSort.DATE_ADDED -> byTitle.sortedByDescending { it.dateAddedSeconds }
        TrackSort.DURATION -> byTitle.sortedByDescending { it.durationMs }
        TrackSort.PLAYS -> byTitle.sortedByDescending { playCounts[it.id] ?: 0 }
    }
}

fun TrackGroup.sortText(sort: AlbumSort): String = if (sort == AlbumSort.ARTIST) subtitle else title

fun List<TrackGroup>.sorted(sort: AlbumSort): List<TrackGroup> {
    val byName = sortedByKey { it.title.searchKey() }
    return when (sort) {
        AlbumSort.NAME -> byName
        AlbumSort.ARTIST -> byName.sortedByKey { it.subtitle.searchKey() }
        // Les albums sans année vont à la fin.
        AlbumSort.YEAR -> byName.sortedByDescending { it.year ?: Int.MIN_VALUE }
    }
}

/**
 * Position de la première entrée de chaque lettre, dans l'ordre de la liste. Une liste triée sur un
 * texte n'est pas toujours rangée par lettre (« # » regroupe chiffres et écritures non latines, qui
 * tombent au début et à la fin) : seule la première apparition compte.
 */
fun <T> List<T>.letterPositions(text: (T) -> String): List<Pair<Char, Int>> {
    val seen = LinkedHashMap<Char, Int>()
    forEachIndexed { index, item -> seen.getOrPut(text(item).indexLetter()) { index } }
    return seen.entries.sortedBy { if (it.key == '#') ' ' else it.key }.map { it.key to it.value }
}

data class SearchResults(
    val tracks: List<Track>,
    val albums: List<TrackGroup>,
    val artists: List<TrackGroup>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * Recherche dans la bibliothèque. Chaque mot de la recherche doit apparaître, dans n'importe quel
 * ordre : « daft live » trouve un morceau de Daft Punk sur un album « Alive ». Les morceaux sont
 * cherchés dans leur titre, leur artiste et leur album ; les albums dans leur nom et leur artiste ;
 * les artistes dans leur nom.
 */
fun search(query: String, tracks: List<Track>, albums: List<TrackGroup>, artists: List<TrackGroup>): SearchResults {
    val words = query.searchKey().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return SearchResults(emptyList(), emptyList(), emptyList())
    fun matches(vararg fields: String): Boolean {
        val text = fields.joinToString(" ") { it.searchKey() }
        return words.all { it in text }
    }
    return SearchResults(
        tracks = tracks.filter { matches(it.title, it.artist, it.album) },
        albums = albums.filter { matches(it.title, it.subtitle) },
        artists = artists.filter { matches(it.title) },
    )
}

/** Tris choisis dans la bibliothèque, retrouvés au prochain lancement. */
class LibrarySortStore(context: Context) {
    private val prefs = context.getSharedPreferences("ongaku_library_sort", Context.MODE_PRIVATE)

    var trackSort: TrackSort
        get() = enumValueOrDefault(prefs.getString(KEY_TRACKS, null), TrackSort.TITLE)
        set(value) = prefs.edit { putString(KEY_TRACKS, value.name) }

    var albumSort: AlbumSort
        get() = enumValueOrDefault(prefs.getString(KEY_ALBUMS, null), AlbumSort.NAME)
        set(value) = prefs.edit { putString(KEY_ALBUMS, value.name) }

    /** Albums en grille de pochettes plutôt qu'en liste. */
    var albumGrid: Boolean
        get() = prefs.getBoolean(KEY_ALBUM_GRID, false)
        set(value) = prefs.edit { putBoolean(KEY_ALBUM_GRID, value) }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_TRACKS = "track_sort"
        const val KEY_ALBUMS = "album_sort"
        const val KEY_ALBUM_GRID = "album_grid"
    }
}
