// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.LibraryCache
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** File à rejouer quand le lecteur démarre sans rien à lire. */
object ResumeQueue {
    suspend fun load(context: Context): MediaSession.MediaItemsWithStartPosition {
        val library = LibraryCache.tracks(context)
        // Rien à reprendre : on lance la bibliothèque, telle que filtrée dans les réglages.
        val tracks = savedTracks(context, library)
            .ifEmpty { library.filter(LibraryFilterStore.get(context).state.value::matches) }
        check(tracks.isNotEmpty()) { "aucun morceau à lire" }
        return positioned(context, tracks)
    }

    /** Dernière file enregistrée, ou null si aucune lecture n'a encore eu lieu. */
    suspend fun loadSaved(context: Context): MediaSession.MediaItemsWithStartPosition? {
        val tracks = savedTracks(context, LibraryCache.tracks(context))
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

/**
 * Réponses de la session : reprise déclenchée par le système (casque Bluetooth…), commandes de
 * file propres à Ongaku ([QueueCommands]), et bibliothèque parcourue depuis la voiture ([CarLibrary]).
 */
class ResumptionCallback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val autoplay: AutoplayGuard,
    private val sleepTimer: () -> SleepTimer?,
) : MediaLibrarySession.Callback {
    private val car = CarLibrary(context, scope)

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(car.root(params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = car.children(parentId, page, pageSize, params)

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = car.item(mediaId)

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = car.search(session, browser, query, params)

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = car.searchResult(query, page, pageSize, params)

    /**
     * Ongaku envoie des morceaux complets ; la voiture n'envoie qu'un identifiant ou une demande
     * vocale, que [CarLibrary] transforme en file.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        if (mediaItems.isNotEmpty() && mediaItems.all { it.localConfiguration != null }) {
            super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        } else {
            car.resolve(mediaItems)
        }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        // Les commandes de file ne sont proposées qu'à Ongaku lui-même ; les autres applications
        // reçoivent la réponse par défaut de Media3.
        if (controller.packageName != context.packageName) return super.onConnect(session, controller)
        return MediaSession.ConnectionResult.accept(
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(QueueCommands.ADD)
                .add(QueueCommands.MOVE)
                .add(AutoplayGuard.START)
                .add(SleepTimer.COMMAND)
                .build(),
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction == AutoplayGuard.START.customAction) {
            val address = AutoplayGuard.address(args)
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            autoplay.start(address, AutoplayGuard.name(args))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        if (customCommand.customAction == SleepTimer.COMMAND.customAction) {
            val timer = sleepTimer()
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_SESSION_DISCONNECTED))
            timer.handle(args)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        val player = session.player as? ExoPlayer
        val handled = player != null && QueueCommands.apply(player, customCommand, args)
        return Futures.immediateFuture(
            SessionResult(if (handled) SessionResult.RESULT_SUCCESS else SessionError.ERROR_NOT_SUPPORTED),
        )
    }

    /** Signale à la surveillance de la lecture automatique qui met en pause ou relance. */
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        if (playerCommand == Player.COMMAND_PLAY_PAUSE || playerCommand == Player.COMMAND_STOP) {
            autoplay.onPlayPauseRequest(controller.packageName)
        }
        return super.onPlayerCommandRequest(session, controller, playerCommand)
    }

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
