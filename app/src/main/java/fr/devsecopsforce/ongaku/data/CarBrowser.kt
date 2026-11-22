package fr.devsecopsforce.ongaku.data

import android.net.Uri

/**
 * Arborescence montrée à la voiture (Android Auto) : favoris, listes, albums, artistes et ajouts
 * récents, puis leurs morceaux. Sans Android ni Media3 : le service ne fait que la traduire.
 *
 * Identifiants : un dossier est « favorites », « playlists », « playlist/<id> », « album/<clé> »…
 * Un morceau est « <dossier>#<identifiant du morceau> » : la voiture le renvoie quand on le
 * touche, et la lecture reprend alors tout le dossier à partir de ce morceau.
 */
object CarBrowser {
    const val ROOT = "root"
    const val FAVORITES = "favorites"
    const val PLAYLISTS = "playlists"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val RECENT = "recent"

    /** Taille de « Ajouts récents » dans la voiture. */
    const val RECENT_SIZE = 50

    data class Labels(
        val favorites: String,
        val playlists: String,
        val albums: String,
        val artists: String,
        val recent: String,
    )

    data class Node(
        val id: String,
        val title: String,
        val subtitle: String = "",
        val browsable: Boolean,
        val artworkUri: Uri? = null,
        /** Morceau à lire ; null pour un dossier. */
        val track: Track? = null,
    )

    /** Ce que la voiture peut parcourir : la bibliothèque affichée et les données de l'utilisateur. */
    data class Source(
        val tracks: List<Track>,
        val data: AppData,
        val mergeFeaturing: Boolean,
    ) {
        val albums by lazy { tracks.albums() }
        val artists by lazy { tracks.artists(mergeFeaturing) }
        val byId by lazy { tracks.associateBy { it.id } }
    }

    fun root(labels: Labels): List<Node> = listOf(
        Node(FAVORITES, labels.favorites, browsable = true),
        Node(PLAYLISTS, labels.playlists, browsable = true),
        Node(ALBUMS, labels.albums, browsable = true),
        Node(ARTISTS, labels.artists, browsable = true),
        Node(RECENT, labels.recent, browsable = true),
    )

    /** Enfants d'un dossier ; null pour un identifiant inconnu. */
    fun children(parentId: String, source: Source, labels: Labels): List<Node>? = when {
        parentId == ROOT -> root(labels)
        parentId == PLAYLISTS -> source.data.playlists.map { Node("playlist/${it.id}", it.name, browsable = true) }
        parentId == ALBUMS -> source.albums.map { Node("album/${it.key}", it.title, it.subtitle, true, it.artworkUri) }
        parentId == ARTISTS -> source.artists.map { Node("artist/${it.key}", it.title, browsable = true, artworkUri = it.artworkUri) }
        else -> tracksOf(parentId, source)?.map { trackNode(parentId, it) }
    }

    /** Morceaux d'un dossier de morceaux, dans l'ordre de lecture ; null s'il n'en est pas un. */
    fun tracksOf(parentId: String, source: Source): List<Track>? {
        val arg = parentId.substringAfter('/', "")
        return when {
            parentId == FAVORITES -> source.tracks.filter { it.id in source.data.favorites }
            parentId == RECENT -> source.tracks.sortedByDescending { it.dateAddedSeconds }.take(RECENT_SIZE)
            parentId.startsWith("playlist/") ->
                source.data.playlists.firstOrNull { it.id == arg }?.trackIds?.mapNotNull(source.byId::get)
            parentId.startsWith("album/") -> source.albums.firstOrNull { it.key == arg }?.tracks
            parentId.startsWith("artist/") -> source.artists.firstOrNull { it.key == arg }?.tracks
            else -> null
        }
    }

    fun trackNode(parentId: String, track: Track) =
        Node("$parentId#${track.id}", track.title, track.artist, browsable = false, artworkUri = track.artworkUri, track = track)

    /** Dossier et morceau d'un identifiant de morceau ; null pour un dossier. */
    fun parseTrackId(mediaId: String): Pair<String, Long>? {
        val separator = mediaId.lastIndexOf('#')
        if (separator <= 0) return null
        val id = mediaId.substring(separator + 1).toLongOrNull() ?: return null
        return mediaId.substring(0, separator) to id
    }

    /**
     * Ce qu'il faut lire quand la voiture choisit [mediaId] : tout son dossier, à partir de ce
     * morceau. null si le dossier ou le morceau n'existe plus.
     */
    fun playFrom(mediaId: String, source: Source): Pair<List<Track>, Int>? {
        val (parentId, trackId) = parseTrackId(mediaId) ?: return null
        val tracks = tracksOf(parentId, source) ?: return null
        val index = tracks.indexOfFirst { it.id == trackId }
        return if (index < 0) null else tracks to index
    }

    /**
     * Recherche, y compris vocale (« mets Daft Punk ») : morceaux dont le titre, l'artiste ou
     * l'album contient la demande. Les morceaux d'un artiste ou d'un album nommé exactement passent
     * devant.
     */
    fun search(query: String, source: Source): List<Track> {
        val key = query.searchKey()
        if (key.isEmpty()) return emptyList()
        fun Track.exact() = artist.searchKey() == key || album.searchKey() == key
        return source.tracks
            .filter { it.title.searchKey().contains(key) || it.artist.searchKey().contains(key) || it.album.searchKey().contains(key) }
            .sortedWith(compareByDescending<Track> { it.exact() }.thenBy { it.artist.searchKey() }.thenBy { it.album.searchKey() }.thenBy { it.trackNumber })
    }
}
