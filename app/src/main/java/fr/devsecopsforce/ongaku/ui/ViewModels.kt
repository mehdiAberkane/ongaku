package fr.devsecopsforce.ongaku.ui

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.MusicRepository
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.toMediaItem
import fr.devsecopsforce.ongaku.playback.PlaybackService
import fr.devsecopsforce.ongaku.playback.ResumeQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val repository = MusicRepository(application)
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
                _state.value = loadedState(repository.loadTracks())
            } catch (e: SecurityException) {
                _state.value = LibraryState(error = "Accès à la musique refusé.")
            }
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
    }

    private companion object {
        const val RELOAD_DELAY_MS = 1500L
    }
}

data class PlayerState(
    val current: MediaItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var restoreJob: Job? = null
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            application,
            SessionToken(application, ComponentName(application, PlaybackService::class.java)),
        ).buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState(player)
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
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                controller?.takeIf { it.isPlaying }?.let { player ->
                    _state.update { state ->
                        state.copy(
                            positionMs = player.currentPosition,
                            durationMs = player.safeDuration(),
                        )
                    }
                }
            }
        }
    }

    private fun Player.safeDuration(): Long =
        duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0

    private fun syncState(player: Player) {
        _state.value = PlayerState(
            current = player.currentMediaItem,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.safeDuration(),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = List(player.mediaItemCount) { player.getMediaItemAt(it) },
            currentIndex = player.currentMediaItemIndex,
        )
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
        _state.update { it.copy(positionMs = positionMs) }
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

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }
}
