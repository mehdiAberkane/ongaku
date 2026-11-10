package fr.devsecopsforce.ongaku.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.MusicRepository
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** File à rejouer quand le lecteur démarre sans rien à lire. */
object ResumeQueue {
    suspend fun load(context: Context): MediaSession.MediaItemsWithStartPosition {
        val library = MusicRepository(context).loadTracks()
        // Rien à reprendre : on lance la bibliothèque, telle que filtrée dans les réglages.
        val tracks = savedTracks(context, library)
            .ifEmpty { library.filter(LibraryFilterStore.get(context).state.value::matches) }
        check(tracks.isNotEmpty()) { "aucun morceau à lire" }
        return positioned(context, tracks)
    }

    /** Dernière file enregistrée, ou null si aucune lecture n'a encore eu lieu. */
    suspend fun loadSaved(context: Context): MediaSession.MediaItemsWithStartPosition? {
        val tracks = savedTracks(context, MusicRepository(context).loadTracks())
        return if (tracks.isEmpty()) null else positioned(context, tracks)
    }

    private fun savedTracks(context: Context, library: List<Track>): List<Track> {
        val byId = library.associateBy { it.id.toString() }
        return PlaybackStateKeeper.prefs(context).getString(PlaybackStateKeeper.KEY_QUEUE, null).orEmpty()
            .split(',')
            .mapNotNull(byId::get)
    }

    private fun positioned(
        context: Context,
        tracks: List<Track>,
    ): MediaSession.MediaItemsWithStartPosition {
        val prefs = PlaybackStateKeeper.prefs(context)
        val currentId = prefs.getString(PlaybackStateKeeper.KEY_CURRENT, null)
        val index = tracks.indexOfFirst { it.id.toString() == currentId }
        return MediaSession.MediaItemsWithStartPosition(
            tracks.map { it.toMediaItem() },
            index.coerceAtLeast(0),
            if (index >= 0) prefs.getLong(PlaybackStateKeeper.KEY_POSITION, 0) else C.TIME_UNSET,
        )
    }
}

/** Reprise déclenchée par le système, par exemple depuis un casque Bluetooth. */
class ResumptionCallback(
    private val context: Context,
    private val scope: CoroutineScope,
) : MediaSession.Callback {

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            try {
                result.set(ResumeQueue.load(context))
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }
}
