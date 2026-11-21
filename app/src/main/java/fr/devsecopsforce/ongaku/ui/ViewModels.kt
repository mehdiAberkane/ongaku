// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.ui

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.LibraryCache
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.toMediaItem
import fr.devsecopsforce.ongaku.playback.PlaybackService
import fr.devsecopsforce.ongaku.playback.QueueCommands
import fr.devsecopsforce.ongaku.playback.ResumeQueue
import fr.devsecopsforce.ongaku.playback.SleepTimer
import fr.devsecopsforce.ongaku.playback.SleepTimerState
import fr.devsecopsforce.ongaku.playback.playOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    /** Morceaux visibles, après application du filtre. */
    val tracks: List<Track> = emptyList(),
    /** Tous les morceaux du téléphone : sert aux choix du filtre et à la sauvegarde. */
    val allTracks: List<Track> = emptyList(),
    val error: String? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val filterStore = LibraryFilterStore.get(application)
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var reloadJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }

    init {
        viewModelScope.launch {
            filterStore.state.collect {
                if (_state.value.loaded) _state.value = loadedState(_state.value.allTracks)
            }
        }
        application.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver,
        )
    }

    private fun loadedState(allTracks: List<Track>) = LibraryState(
        loaded = true,
        tracks = allTracks.filter(filterStore.state.value::matches),
        allTracks = allTracks,
    )

    /** Android envoie des rafales de notifications pendant une copie de fichiers. */
    private fun scheduleReload() {
        if (!_state.value.loaded) return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(RELOAD_DELAY_MS)
            load()
        }
    }

    fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _state.value = loadedState(LibraryCache.tracks(getApplication()))
            } catch (e: SecurityException) {
                _state.value = LibraryState(error = getApplication<Application>().getString(R.string.library_error_permission))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // MediaStore indisponible (carte retirée, base en reconstruction…) : l'écran le dit et propose de réessayer.
                Log.w(TAG, "bibliothèque illisible", e)
                _state.value = LibraryState(
                    error = getApplication<Application>().getString(R.string.library_error_unavailable),
                )
            }
        }
    }

    /** « Rechercher à nouveau » : relit MediaStore même si rien n'a été signalé. */
    fun refresh() {
        LibraryCache.invalidate()
        load()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
    }

    private companion object {
        const val TAG = "OngakuLibrary"
        const val RELOAD_DELAY_MS = 1500L
    }
}

/**
 * Tout l'état du lecteur sauf la position : il ne change qu'aux événements du lecteur, pas pendant
 * la lecture. L'écran principal le lit et ne se recompose donc pas toutes les demi-secondes.
 */
data class PlayerState(
    val current: MediaItem? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    /** Positions de [queue] dans l'ordre où elles seront jouées (mélangé en lecture aléatoire). */
    val playOrder: List<Int> = emptyList(),
)

/** Position dans le morceau en cours : à part, lue seulement par la barre de progression. */
data class PlayerProgress(val positionMs: Long = 0, val durationMs: Long = 0)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlayerProgress())
    val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private var restoreJob: Job? = null
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            application,
            SessionToken(application, ComponentName(application, PlaybackService::class.java)),
        ).buildAsync()

    /** La liste de la file a changé depuis la dernière copie (ajout, retrait, déplacement…). */
    private var queueChanged = true

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Une durée découverte en cours de lecture change aussi la chronologie, pas la file.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) queueChanged = true
        }

        override fun onEvents(player: Player, events: Player.Events) =
            syncState(player, orderChanged = events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED))
    }

    init {
        controllerFuture.addListener(
            {
                if (controllerFuture.isCancelled) return@addListener
                val connected = runCatching { controllerFuture.get() }.getOrNull()
                    ?: return@addListener
                controller = connected
                connected.addListener(listener)
                syncState(connected)
                restoreSavedQueue(connected)
            },
            ContextCompat.getMainExecutor(application),
        )
        // Le minuteur ne tourne que si une barre de progression est affichée : elle cesse d'être
        // lue quand l'écran passe en arrière-plan ou s'éteint.
        viewModelScope.launch {
            _progress.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { watched ->
                    if (!watched) return@collectLatest
                    controller?.let(::syncProgress)
                    while (isActive) {
                        delay(PROGRESS_EVERY_MS)
                        controller?.takeIf { it.isPlaying }?.let(::syncProgress)
                    }
                }
        }
    }

    private fun syncProgress(player: Player) {
        _progress.value = PlayerProgress(player.currentPosition, player.safeDuration())
    }

    private fun Player.safeDuration(): Long =
        duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0

    /**
     * Copie l'état du lecteur. La file (des milliers de morceaux après « Lire » sur toute la
     * bibliothèque) n'est recopiée que si elle a changé, et son ordre que s'il a pu changer.
     */
    private fun syncState(player: Player, orderChanged: Boolean = false) {
        val previous = _state.value
        val rebuildQueue = queueChanged || previous.queue.size != player.mediaItemCount
        queueChanged = false
        _state.value = PlayerState(
            current = player.currentMediaItem,
            isPlaying = player.isPlaying,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = if (rebuildQueue) List(player.mediaItemCount) { player.getMediaItemAt(it) } else previous.queue,
            currentIndex = player.currentMediaItemIndex,
            playOrder = if (rebuildQueue || orderChanged) player.playOrder() else previous.playOrder,
        )
        syncProgress(player)
    }

    /** Remet la dernière lecture en place, en pause, pour que le mini-lecteur puisse la reprendre. */
    private fun restoreSavedQueue(player: MediaController) {
        if (player.mediaItemCount > 0) return
        restoreJob = viewModelScope.launch {
            val saved = try {
                ResumeQueue.loadSaved(getApplication())
            } catch (e: SecurityException) {
                // Premier lancement : l'accès à la musique n'est pas encore accordé.
                null
            } ?: return@launch
            if (player.mediaItemCount > 0) return@launch
            player.setMediaItems(saved.mediaItems, saved.startIndex, saved.startPositionMs)
            player.prepare()
        }
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        restoreJob?.cancel()
        controller?.run {
            setMediaItems(tracks.map(Track::toMediaItem), startIndex, 0L)
            prepare()
            play()
        }
    }

    /** Remplace la lecture en cours par toute la liste, depuis le début ou au hasard. */
    fun playAll(tracks: List<Track>, shuffled: Boolean) {
        if (tracks.isEmpty()) return
        controller?.shuffleModeEnabled = shuffled
        play(tracks, if (shuffled) tracks.indices.random() else 0)
    }

    /** « Lire ensuite » : juste après le morceau en cours, même en lecture aléatoire. */
    fun playNext(tracks: List<Track>) = enqueue(tracks, next = true)

    /** « Ajouter à la file » : après tous les morceaux restants. */
    fun addToQueue(tracks: List<Track>) = enqueue(tracks, next = false)

    private fun enqueue(tracks: List<Track>, next: Boolean) {
        if (tracks.isEmpty()) return
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            play(tracks, 0)
            return
        }
        restoreJob?.cancel()
        if (!player.shuffleModeEnabled) {
            val start = if (next) player.currentMediaItemIndex + 1 else player.mediaItemCount
            player.addMediaItems(start, tracks.map(Track::toMediaItem))
            return
        }
        // En lecture aléatoire, Media3 rangerait les ajouts au hasard : le service les ajoute et les
        // range lui-même. Par paquets, et à l'envers pour « Lire ensuite » : chaque paquet se place
        // juste après le morceau en cours, devant le précédent.
        val chunks = tracks.chunked(QueueCommands.MAX_ITEMS_PER_COMMAND)
        (if (next) chunks.asReversed() else chunks).forEach { chunk ->
            player.sendCustomCommand(QueueCommands.ADD, QueueCommands.addArgs(chunk, next))
        }
    }

    /** Retire de la file le morceau à cette position de la file (pas de l'ordre de lecture). */
    fun removeFromQueue(index: Int) {
        controller?.takeIf { index in 0 until it.mediaItemCount }?.removeMediaItem(index)
    }

    /** Déplace un morceau dans l'ordre de lecture, de la position [from] à [to]. */
    fun moveInQueue(from: Int, to: Int) {
        val player = controller ?: return
        if (player.shuffleModeEnabled) {
            player.sendCustomCommand(QueueCommands.MOVE, QueueCommands.moveArgs(from, to))
        } else {
            // Sans lecture aléatoire, l'ordre de lecture est celui de la file.
            player.moveMediaItem(from, to)
        }
    }

    /** Vide la file en gardant le morceau en cours. */
    fun clearQueue() {
        controller?.run {
            val current = currentMediaItemIndex
            if (current + 1 < mediaItemCount) removeMediaItems(current + 1, mediaItemCount)
            if (current > 0) removeMediaItems(0, current)
        }
    }

    fun togglePlayPause() {
        controller?.run {
            if (isPlaying) {
                pause()
            } else {
                // Le lecteur restauré à l'ouverture peut encore être inactif.
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _progress.update { it.copy(positionMs = positionMs) }
    }

    fun playQueueItem(index: Int) {
        controller?.run {
            seekToDefaultPosition(index)
            play()
        }
    }

    fun toggleShuffle() {
        controller?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Minuteur de sommeil en cours ; tenu par le service, qui continue sans cet écran. */
    val sleepTimer: StateFlow<SleepTimerState?> = SleepTimer.state

    fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(SleepTimer.COMMAND, SleepTimer.minutesArgs(minutes))
    }

    fun sleepAtTrackEnd() {
        controller?.sendCustomCommand(SleepTimer.COMMAND, SleepTimer.trackEndArgs())
    }

    fun cancelSleepTimer() {
        controller?.sendCustomCommand(SleepTimer.COMMAND, SleepTimer.cancelArgs())
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private companion object {
        const val PROGRESS_EVERY_MS = 500L
    }
}
