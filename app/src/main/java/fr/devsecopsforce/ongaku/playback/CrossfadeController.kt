package fr.devsecopsforce.ongaku.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * ExoPlayer ne sait pas superposer deux morceaux. Quelques secondes avant la fin, un second lecteur
 * éphémère reprend la fin du morceau en cours pendant que le lecteur principal, celui que voient la
 * notification et l'interface, passe déjà au suivant. Ce contrôleur compte aussi le temps d'écoute.
 */
class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer,
    private val settings: PlaybackSettings,
    private val playCounts: PlayCounts,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var outgoing: ExoPlayer? = null
    private var fading = false
    private var fadeStartMs = 0L
    private var fadeDurationMs = 0L

    private var lastTickMs = 0L
    private var pendingListenedMs = 0L
    private var currentTrackListenedMs = 0L
    private var currentTrackCounted = false

    private val tick = object : Runnable {
        override fun run() {
            trackListening()
            if (fading) updateFade() else watchForTrackEnd()
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        player.addListener(this)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) stopOutgoing()
        updateRunning()
    }

    override fun onPlaybackStateChanged(playbackState: Int) = updateRunning()

    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
        if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) stopOutgoing()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        currentTrackListenedMs = 0
        currentTrackCounted = false
        // Le lecteur préparé d'avance contient l'ancien morceau : il ne sert plus à rien.
        if (!fading) stopOutgoing()
    }

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
            stopOutgoing()
            return
        }

        val remaining = duration - player.currentPosition
        when {
            // Retour en arrière alors que le second lecteur était déjà prêt.
            remaining > fadeMs + PREPARE_LEAD_MS + 1000 -> stopOutgoing()
            remaining <= fadeMs + PREPARE_LEAD_MS && outgoing == null && remaining > fadeMs / 2 ->
                prepareOutgoing(duration - fadeMs)
            remaining <= fadeMs && outgoing?.playbackState == Player.STATE_READY ->
                startFade(remaining)
        }
    }

    private fun prepareOutgoing(startPositionMs: Long) {
        val item = player.currentMediaItem ?: return
        outgoing = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Le lecteur principal gère seul le focus audio.
                /* handleAudioFocus = */ false,
            )
            .build()
            .apply {
                setMediaItem(item, startPositionMs)
                playWhenReady = false
                prepare()
            }
    }

    private fun startFade(remainingMs: Long) {
        val tail = outgoing ?: return
        Log.d(TAG, "start, remaining=$remainingMs")
        fading = true
        fadeStartMs = SystemClock.elapsedRealtime()
        fadeDurationMs = remainingMs
        tail.seekTo(tail.duration - remainingMs)
        tail.play()
        player.volume = 0f
        player.seekToNextMediaItem()
    }

    private fun updateFade() {
        val progress = (SystemClock.elapsedRealtime() - fadeStartMs).toFloat() / fadeDurationMs
        if (progress >= 1f) {
            Log.d(TAG, "end")
            stopOutgoing()
            return
        }
        // Courbes à puissance constante : le volume perçu ne se creuse pas au milieu du fondu.
        val angle = progress * (Math.PI.toFloat() / 2)
        player.volume = sin(angle)
        outgoing?.volume = cos(angle)
    }

    private fun stopOutgoing() {
        outgoing?.release()
        outgoing = null
        if (fading) {
            fading = false
            player.volume = 1f
        }
    }

    fun release() {
        handler.removeCallbacks(tick)
        running = false
        flushListening()
        stopOutgoing()
        player.removeListener(this)
    }

    private companion object {
        const val TAG = "OngakuFade"
        const val TICK_MS = 100L
        const val PREPARE_LEAD_MS = 3000L
        const val SAVE_EVERY_MS = 10_000L
        const val COUNT_PLAY_AFTER_MS = 30_000L
    }
}
