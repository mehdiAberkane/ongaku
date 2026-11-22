// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.AppDataStore
import fr.devsecopsforce.ongaku.data.CarBrowser
import fr.devsecopsforce.ongaku.data.LibraryCache
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import fr.devsecopsforce.ongaku.data.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La bibliothèque vue depuis la voiture : traduit [CarBrowser] en éléments Media3, répond à la
 * recherche (y compris vocale) et transforme un choix fait dans la voiture en file de lecture.
 */
class CarLibrary(private val context: Context, private val scope: CoroutineScope) {

    private val labels by lazy {
        CarBrowser.Labels(
            favorites = context.getString(R.string.main_title_favorites),
            playlists = context.getString(R.string.library_tab_playlists),
            albums = context.getString(R.string.library_tab_albums),
            artists = context.getString(R.string.library_tab_artists),
            recent = context.getString(R.string.main_title_recent),
        )
    }

    /** Derniers résultats de recherche, par demande : la voiture les relit page par page. */
    private val searches = HashMap<String, List<MediaItem>>()

    fun root(params: LibraryParams?): LibraryResult<MediaItem> =
        LibraryResult.ofItem(folder(CarBrowser.ROOT, context.getString(R.string.app_name)), params)

    fun children(parentId: String, page: Int, pageSize: Int, params: LibraryParams?) =
        load<LibraryResult<ImmutableList<MediaItem>>> {
            val nodes = CarBrowser.children(parentId, source(), labels)
                ?: return@load LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            LibraryResult.ofItemList(nodes.page(page, pageSize).map(::toMediaItem), params)
        }

    fun item(mediaId: String) = load<LibraryResult<MediaItem>> {
        if (mediaId == CarBrowser.ROOT) return@load root(null)
        val source = source()
        val node = CarBrowser.playFrom(mediaId, source)?.let { (tracks, index) -> CarBrowser.trackNode(mediaId.substringBeforeLast('#'), tracks[index]) }
            ?: CarBrowser.root(labels).firstOrNull { it.id == mediaId }
            ?: CarBrowser.children(mediaId.substringBefore('/').let { prefix -> "${prefix}s" }, source, labels)?.firstOrNull { it.id == mediaId }
            ?: return@load LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        LibraryResult.ofItem(toMediaItem(node), null)
    }

    fun search(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?) =
        load<LibraryResult<Void>> {
            val results = CarBrowser.search(query, source()).take(MAX_RESULTS)
                .map { toMediaItem(CarBrowser.trackNode(SEARCH_PREFIX + query, it)) }
            searches[query] = results
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

    fun searchResult(query: String, page: Int, pageSize: Int, params: LibraryParams?) =
        load<LibraryResult<ImmutableList<MediaItem>>> {
            val results = searches[query] ?: CarBrowser.search(query, source()).take(MAX_RESULTS)
                .map { toMediaItem(CarBrowser.trackNode(SEARCH_PREFIX + query, it)) }
            LibraryResult.ofItemList(results.page(page, pageSize), params)
        }

    /**
     * Éléments choisis dans la voiture, qui n'ont qu'un identifiant : un morceau lance tout son
     * dossier à partir de lui ; une demande vocale lance les résultats de la recherche ; une demande
     * vide (« mets de la musique ») reprend la dernière file.
     */
    fun resolve(items: List<MediaItem>) = load<MediaSession.MediaItemsWithStartPosition> {
        val item = items.singleOrNull() ?: throw IllegalArgumentException("un seul élément attendu")
        val source = source()
        val query = item.requestMetadata.searchQuery
        val mediaId = item.mediaId
        val (tracks, index) = when {
            mediaId.startsWith(SEARCH_PREFIX) && CarBrowser.parseTrackId(mediaId) != null -> {
                val (parent, trackId) = CarBrowser.parseTrackId(mediaId)!!
                val results = CarBrowser.search(parent.removePrefix(SEARCH_PREFIX), source).take(MAX_RESULTS)
                results to results.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
            }
            mediaId.isEmpty() && query != null ->
                if (query.isBlank()) return@load ResumeQueue.load(context) else CarBrowser.search(query, source).take(MAX_RESULTS) to 0
            else -> CarBrowser.playFrom(mediaId, source)
                ?: CarBrowser.tracksOf(mediaId, source)?.let { it to 0 }
                ?: throw IllegalArgumentException("élément inconnu : $mediaId")
        }
        check(tracks.isNotEmpty()) { "rien à lire" }
        MediaSession.MediaItemsWithStartPosition(tracks.map { it.toMediaItem() }, index, C.TIME_UNSET)
    }

    /** Bibliothèque telle qu'affichée dans Ongaku (filtre compris), listes et favoris. */
    private suspend fun source(): CarBrowser.Source = withContext(Dispatchers.IO) {
        val filter = LibraryFilterStore.get(context).state.value
        CarBrowser.Source(
            tracks = LibraryCache.tracks(context).filter(filter::matches),
            data = synchronized(AppDataStore.lock) { AppDataStore(context).load() },
            mergeFeaturing = PlaybackSettings(context).mergeFeaturing,
        )
    }

    private fun <T> load(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    private fun folder(id: String, title: String) = toMediaItem(CarBrowser.Node(id, title, browsable = true))

    private fun toMediaItem(node: CarBrowser.Node): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(node.title)
            .setArtist(node.subtitle.ifEmpty { null })
            .setIsBrowsable(node.browsable)
            .setIsPlayable(!node.browsable)
            .setArtworkUri(node.artworkUri)
            .setMediaType(if (node.browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder().setMediaId(node.id).setMediaMetadata(metadata).build()
    }

    private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> =
        if (page < 0 || pageSize <= 0) this else drop(page * pageSize).take(pageSize)

    private companion object {
        /** Assez pour une demande vocale ; une recherche trop large ne remplit pas la file de milliers de morceaux. */
        const val MAX_RESULTS = 200
        const val SEARCH_PREFIX = "search/"
    }
}
