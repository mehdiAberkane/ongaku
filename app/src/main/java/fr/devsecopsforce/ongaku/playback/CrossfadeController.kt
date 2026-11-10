package fr.devsecopsforce.ongaku.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import kotlin.math.sin

/**
 * ExoPlayer ne sait pas superposer deux morceaux : le fondu enchaîné utilise donc deux lecteurs qui
 * jouent à tour de rôle. Quelques secondes avant la fin, un second lecteur se prépare sur le morceau
 * suivant. Pendant le fondu, le lecteur actif termine lui-même son morceau en baissant pendant que
 * le second démarre le suivant en montant : aucun son ne change de lecteur en cours de route. À la
 * fin, le second devient le lecteur actif, celui de la session que suivent la notification et
 * l'interface, et l'ancien est libéré.
 *
 * Ce contrôleur compte aussi le temps d'écoute.
 */
class CrossfadeController(
    initialPlayer: ExoPlayer,
    private val settings: PlaybackSettings,
    private val playCounts: PlayCounts,
    /** Crée un lecteur de relève, sans focus audio : le lecteur actif le garde pendant le fondu. */
    private val createPlayer: () -> ExoPlayer,
    /** Le lecteur de relève devient le lecteur actif : la session doit le suivre. */
    private val onPlayerSwapped: (ExoPlayer) -> Unit,
) : Player.Listener {

    /** Lecteur actif, celui de la session. */
    var player: ExoPlayer = initialPlayer
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** Lecteur de relève, préparé sur le morceau suivant. */
    private var incoming: ExoPlayer? = null
    private var fading = false
    private var fadeStartMs = 0L
    private var fadeDurationMs = 0L
    private var fadeEndPositionMs = 0L

    private var lastTickMs = 0L
    private var pendingListenedMs = 0L
    private var currentTrackListenedMs = 0L
    private var currentTrackCounted = false

    private val tick = object : Runnable {
        override fun run() {
            trackListening()
            if (fading) updateFade() else watchForTrackEnd()
            // Pas fins pendant le fondu : le volume glisse au lieu de monter par paliers.
            handler.postDelayed(this, if (fading) FADE_TICK_MS else TICK_MS)
        }
    }

    init {
        player.addListener(this)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            // Le lecteur actif s'arrête de lui-même à la fin de son morceau pendant le fondu.
            if (fading && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                finishFade()
            } else {
                cancelIncoming()
            }
        }
        updateRunning()
    }

    override fun onPlaybackStateChanged(playbackState: Int) = updateRunning()

    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
        if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) cancelIncoming()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        resetTrackListening()
        // Morceau suivant ou précédent demandé à la main : la relève ne correspond plus.
        cancelIncoming()
    }

    // La file a changé : le lecteur de relève ne joue peut-être plus le bon morceau suivant.
    override fun onTimelineChanged(timeline: Timeline, reason: Int) = cancelIncoming()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = cancelIncoming()

    override fun onRepeatModeChanged(repeatMode: Int) = cancelIncoming()

    private fun updateRunning() {
        val shouldRun = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY ||
                player.playbackState == Player.STATE_BUFFERING)
        if (shouldRun == running) return
        running = shouldRun
        if (running) {
            lastTickMs = SystemClock.elapsedRealtime()
            handler.post(tick)
        } else {
            handler.removeCallbacks(tick)
            flushListening()
        }
    }

    private fun trackListening() {
        val now = SystemClock.elapsedRealtime()
        if (player.isPlaying) {
            pendingListenedMs += now - lastTickMs
            currentTrackListenedMs += now - lastTickMs
            countPlayIfDue()
        }
        lastTickMs = now
        if (pendingListenedMs >= SAVE_EVERY_MS) flushListening()
    }

    private fun resetTrackListening() {
        currentTrackListenedMs = 0
        currentTrackCounted = false
    }

    /** Une écoute compte une seule fois par lecture : 30 s, ou la moitié d'un morceau plus court. */
    private fun countPlayIfDue() {
        if (currentTrackCounted) return
        val duration = player.duration
        val threshold = if (duration == C.TIME_UNSET) {
            COUNT_PLAY_AFTER_MS
        } else {
            minOf(COUNT_PLAY_AFTER_MS, duration / 2)
        }
        if (currentTrackListenedMs < threshold) return
        currentTrackCounted = true
        player.currentMediaItem?.mediaId?.toLongOrNull()?.let(playCounts::increment)
    }

    private fun flushListening() {
        if (pendingListenedMs == 0L) return
        settings.listenedMs += pendingListenedMs
        pendingListenedMs = 0
    }

    private fun watchForTrackEnd() {
        val fadeMs = settings.crossfadeSeconds * 1000L
        val duration = player.duration
        val canFade = fadeMs > 0 && duration != C.TIME_UNSET && duration >= fadeMs * 2 &&
            player.isPlaying && player.hasNextMediaItem() &&
            player.repeatMode != Player.REPEAT_MODE_ONE
        if (!canFade) {
            cancelIncoming()
            return
        }

        val remaining = duration - player.currentPosition
        val next = incoming
        when {
            // Retour en arrière alors que la relève était déjà prête.
            remaining > fadeMs + PREPARE_LEAD_MS + 1000 -> cancelIncoming()
            next == null -> if (remaining <= fadeMs + PREPARE_LEAD_MS && remaining > fadeMs / 2) {
                prepareIncoming()
            }
            remaining <= fadeMs && next.playbackState == Player.STATE_READY ->
                startFade(remaining, duration)
        }
    }

    /** Prépare la relève sur le morceau suivant, avec la même file, au volume nul et à l'arrêt. */
    @OptIn(UnstableApi::class)
    private fun prepareIncoming() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        val shuffleOrder = currentShuffleOrder()
        incoming = createPlayer().apply {
            volume = 0f
            setMediaItems(items, nextIndex, 0L)
            repeatMode = player.repeatMode
            // Même ordre aléatoire que le lecteur actif, pour que « suivant » reste le même.
            if (shuffleOrder != null) setShuffleOrder(shuffleOrder)
            shuffleModeEnabled = player.shuffleModeEnabled
            playWhenReady = false
            prepare()
        }
    }

    /** Ordre de lecture aléatoire du lecteur actif, relu morceau par morceau. */
    @OptIn(UnstableApi::class)
    private fun currentShuffleOrder(): ShuffleOrder? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(/* shuffleModeEnabled = */ true)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        if (order.size != timeline.windowCount) return null
        return ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), SystemClock.elapsedRealtime())
    }

    private fun startFade(remainingMs: Long, durationMs: Long) {
        val next = incoming ?: return
        fading = true
        fadeStartMs = SystemClock.elapsedRealtime()
        fadeDurationMs = remainingMs.coerceAtLeast(1L)
        fadeEndPositionMs = durationMs
        // Le lecteur actif s'arrête à la fin de son morceau au lieu d'enchaîner lui-même le suivant,
        // que la relève est en train de jouer.
        player.pauseAtEndOfMediaItems = true
        next.play()
    }

    private fun updateFade() {
        val next = incoming
        val remaining = fadeEndPositionMs - player.currentPosition
        val elapsed = SystemClock.elapsedRealtime() - fadeStartMs
        when {
            next == null -> cancelIncoming()
            // Retour en arrière pendant le fondu.
            remaining > fadeDurationMs + 1000 -> cancelIncoming()
            // Fin atteinte, ou garde-fou si le lecteur actif n'avance plus.
            remaining <= 0 || elapsed > fadeDurationMs + FADE_TIMEOUT_MARGIN_MS -> finishFade()
            else -> {
                // La progression suit la position du morceau qui se termine, pas l'horloge.
                val progress = (1f - remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                // Courbes en S : chaque morceau part et arrive en pente douce. Les deux volumes se
                // croisent à mi-chemin et totalisent toujours 1.
                val fadeIn = sin(progress * (Math.PI.toFloat() / 2)).let { it * it }
                next.volume = fadeIn
                player.volume = 1f - fadeIn
            }
        }
    }

    /** La relève devient le lecteur actif ; l'ancien, arrivé au bout de son morceau, est libéré. */
    private fun finishFade() {
        val next = incoming ?: return cancelIncoming()
        val old = player
        fading = false
        incoming = null

        old.removeListener(this)
        next.volume = 1f
        player = next
        next.addListener(this)
        resetTrackListening()
        onPlayerSwapped(next)
        old.release()
        // L'ancien lecteur a rendu le focus audio en se libérant : la relève le reprend.
        next.setAudioAttributes(next.audioAttributes, /* handleAudioFocus = */ true)
        next.setHandleAudioBecomingNoisy(settings.pauseOnNoisy)
        updateRunning()
    }

    /** Abandonne la relève : le lecteur actif reprend son volume et enchaînera lui-même. */
    private fun cancelIncoming() {
        incoming?.release()
        incoming = null
        if (fading) {
            fading = false
            player.pauseAtEndOfMediaItems = false
            player.volume = 1f
        }
    }

    fun release() {
        handler.removeCallbacks(tick)
        running = false
        flushListening()
        cancelIncoming()
        player.removeListener(this)
    }

    private companion object {
        const val TICK_MS = 100L
        const val FADE_TICK_MS = 25L
        const val FADE_TIMEOUT_MARGIN_MS = 3000L
        const val PREPARE_LEAD_MS = 3000L
        const val SAVE_EVERY_MS = 10_000L
        const val COUNT_PLAY_AFTER_MS = 30_000L
    }
}
