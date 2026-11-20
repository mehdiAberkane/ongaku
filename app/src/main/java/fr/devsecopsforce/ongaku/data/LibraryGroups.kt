package fr.devsecopsforce.ongaku.data

import android.net.Uri

/** Un album ou un artiste : un titre affiché et les morceaux qu'il regroupe. */
data class TrackGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUri: Uri?,
    /** Dans l'ordre d'affichage : c'est aussi l'ordre de lecture. */
    val tracks: List<Track>,
    /** Année la plus récente des morceaux d'un album ; null si aucune n'est renseignée. */
    val year: Int? = null,
    /** Page d'un artiste : ses morceaux sont présentés album par album. */
    val isArtist: Boolean = false,
)

const val UNKNOWN_ARTIST = "Artiste inconnu"

/** « A feat. B », « A (ft. B) », « A featuring B » : tout ce qui suit l'artiste principal. */
private val FEATURING = Regex("""\s+[(\[]?(?:feat|ft|featuring)\b\.?\s.*$""", RegexOption.IGNORE_CASE)

/** L'artiste principal d'un tag artiste, si l'on rattache les « feat. » à lui. */
fun primaryArtist(artist: String, mergeFeaturing: Boolean): String {
    if (!mergeFeaturing) return artist.trim()
    return artist.replace(FEATURING, "").trim().ifEmpty { artist.trim() }
}

/** L'artiste sous lequel ranger un morceau : celui de l'album s'il manque le sien. */
private fun Track.groupArtist(mergeFeaturing: Boolean): String {
    val own = if (artist == UNKNOWN_ARTIST && albumArtist.isNotEmpty()) albumArtist else artist
    return primaryArtist(own, mergeFeaturing)
}

private fun <K> List<Track>.mostCommon(selector: (Track) -> K): K =
    groupingBy(selector).eachCount().maxBy { it.value }.key

/** L'artiste de l'album s'il est renseigné, sinon l'artiste le plus présent. */
private fun List<Track>.albumArtistOrMain(): String {
    val tagged = filter { it.albumArtist.isNotEmpty() }
    return if (tagged.isNotEmpty()) tagged.mostCommon { it.albumArtist } else mostCommon { it.artist }
}

private val byTrackNumber = compareBy<Track>({ it.trackNumber }, { it.title.lowercase() })

/**
 * Android crée un album par dossier : on regroupe d'abord par identifiant, pour qu'une compilation
 * reste entière, puis on fusionne ceux qui ont le même nom et le même artiste d'album (à défaut,
 * le même artiste principal).
 */
fun List<Track>.albums(): List<TrackGroup> = groupBy { it.albumId }
    .values
    .groupBy { tracks -> tracks.first().album.trim().lowercase() to tracks.albumArtistOrMain().lowercase() }
    .map { (key, parts) ->
        val sorted = parts.flatten().sortedWith(byTrackNumber)
        TrackGroup(
            key = "album:${key.first}|${key.second}",
            title = sorted.first().album,
            subtitle = sorted.albumArtistOrMain(),
            artworkUri = sorted.first().artworkUri,
            tracks = sorted,
            year = sorted.maxOf { it.year }.takeIf { it > 0 },
        )
    }
    .sortedBy { it.title.searchKey() }

/**
 * Artistes, sans tenir compte de la casse. Avec [mergeFeaturing], « A feat. B » est rangé sous A.
 * Les morceaux d'un artiste sont classés par album, du plus récent au plus ancien.
 */
fun List<Track>.artists(mergeFeaturing: Boolean = false): List<TrackGroup> =
    groupBy { it.groupArtist(mergeFeaturing).lowercase() }
        .map { (key, tracks) ->
            val sorted = tracks.byAlbum()
            TrackGroup(
                key = "artist:$key",
                title = tracks.mostCommon { it.groupArtist(mergeFeaturing) },
                subtitle = "${tracks.size} titre${if (tracks.size > 1) "s" else ""}",
                artworkUri = sorted.first().artworkUri,
                tracks = sorted,
                isArtist = true,
            )
        }
        .sortedBy { it.title.searchKey() }

private fun Track.albumKey() = album.trim().lowercase()

private fun List<Track>.byAlbum(): List<Track> = groupBy { it.albumKey() }
    .values
    .sortedWith(
        compareBy<List<Track>> { album -> album.maxOf { it.year }.let { if (it > 0) -it else Int.MAX_VALUE } }
            .thenBy { it.first().album.searchKey() },
    )
    .flatMap { it.sortedWith(byTrackNumber) }

/** Une ligne de la page d'un album ou d'un artiste. */
sealed interface GroupRow {
    val key: String

    data class AlbumHeader(
        val title: String,
        val artworkUri: Uri?,
        val year: Int?,
        val trackCount: Int,
    ) : GroupRow {
        override val key get() = "album:${title.trim().lowercase()}"
    }

    data class DiscHeader(val album: String, val disc: Int) : GroupRow {
        override val key get() = "disc:$album:$disc"
    }

    /** [index] : position du morceau dans [TrackGroup.tracks], pour lancer la lecture. */
    data class Song(val track: Track, val index: Int) : GroupRow {
        override val key get() = "track:${track.id}"
    }
}

/** Un morceau sans numéro de disque est sur le premier. */
private val Track.disc: Int get() = discNumber.coerceAtLeast(1)

/**
 * Lignes à afficher : sur la page d'un artiste, un en-tête par album ; partout, un en-tête par
 * disque quand un album en a plusieurs.
 */
fun TrackGroup.rows(): List<GroupRow> {
    val rows = mutableListOf<GroupRow>()
    var index = 0
    // Les morceaux d'un même album se suivent (voir [byAlbum]).
    val albums = if (isArtist) tracks.chunkedBy { it.albumKey() } else listOf(tracks)
    for (album in albums) {
        val first = album.first()
        if (isArtist) {
            rows += GroupRow.AlbumHeader(
                title = first.album,
                artworkUri = first.artworkUri,
                year = album.maxOf { it.year }.takeIf { it > 0 },
                trackCount = album.size,
            )
        }
        val multiDisc = album.map { it.disc }.distinct().size > 1
        var disc = -1
        for (track in album) {
            if (multiDisc && track.disc != disc) {
                disc = track.disc
                rows += GroupRow.DiscHeader(first.albumKey(), disc)
            }
            rows += GroupRow.Song(track, index++)
        }
    }
    return rows
}

/** Découpe en suites consécutives de même clé. */
private fun <K> List<Track>.chunkedBy(key: (Track) -> K): List<List<Track>> {
    val chunks = mutableListOf<MutableList<Track>>()
    var last: Any? = Any()
    for (track in this) {
        val k = key(track)
        if (chunks.isEmpty() || k != last) chunks += mutableListOf<Track>()
        chunks.last() += track
        last = k
    }
    return chunks
}
