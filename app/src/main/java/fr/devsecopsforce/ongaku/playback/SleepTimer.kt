// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionCommand
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minuteur en cours, lu par l'écran de lecture (même processus que le service). */
sealed interface SleepTimerState {
    /** Arrêt à [deadlineMs], sur l'horloge [SystemClock.elapsedRealtime]. */
    data class Until(val deadlineMs: Long) : SleepTimerState

    /** Arrêt à la fin du morceau en cours. */
    data object TrackEnd : SleepTimerState
}

/**
 * Minuteur de sommeil, côté service : il continue écran éteint et application fermée. Les
 * décisions sont dans [SleepTimerRules] ; le volume passe par le fondu enchaîné
 * ([CrossfadeController.volumeScale]) pour que les deux baisses se combinent au lieu de se
 * contredire.
 */
class SleepTimer(
    private val settings: PlaybackSettings,
    private val crossfade: () -> CrossfadeController?,
) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val wakeUp = Runnable { evaluate() }

    /** Lecteur écouté ; il change quand le fondu enchaîné passe la main au lecteur de relève. */
    private var listened: Player? = null

    fun handle(args: Bundle) {
        val minutes = args.getInt(ARG_MINUTES, CANCEL)
        when {
            minutes > 0 -> start(SleepTimerState.Until(SystemClock.elapsedRealtime() + minutes * 60_000L))
            minutes == TRACK_END -> start(SleepTimerState.TrackEnd)
            else -> cancel()
        }
    }

    private fun start(timer: SleepTimerState) {
        cancel()
        val controller = crossfade() ?: return
        _state.value = timer
        controller.stopAtTrackEnd = timer == SleepTimerState.TrackEnd
        listened = controller.player.also { it.addListener(this) }
        evaluate()
    }

    fun cancel() {
        handler.removeCallbacks(wakeUp)
        listened?.removeListener(this)
        listened = null
        crossfade()?.apply {
            stopAtTrackEnd = false
            volumeScale = 1f
        }
        _state.value = null
    }

    /** Le fondu enchaîné a confié la lecture à un autre lecteur. */
    fun onPlayerSwapped(next: ExoPlayer) {
        val previous = listened ?: return
        previous.removeListener(this)
        listened = next.also { it.addListener(this) }
        evaluate()
    }

    private fun evaluate() {
        handler.removeCallbacks(wakeUp)
        val timer = _state.value ?: return
        val controller = crossfade() ?: return
        val player = controller.player
        val atTrackEnd = timer == SleepTimerState.TrackEnd
        val remaining = SleepTimerRules.remainingMs(
            atTrackEnd = atTrackEnd,
            nowMs = SystemClock.elapsedRealtime(),
            deadlineMs = (timer as? SleepTimerState.Until)?.deadlineMs ?: 0L,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeUnless { it == C.TIME_UNSET },
            speed = player.playbackParameters.speed,
        )
        if (SleepTimerRules.expired(atTrackEnd, remaining)) {
            // Arrêt d'abord, volume rétabli ensuite : la prochaine lecture repart à plein volume.
            player.pause()
            cancel()
            return
        }
        controller.volumeScale = SleepTimerRules.volume(remaining, settings.sleepFade)
        SleepTimerRules.nextWakeUpMs(remaining, settings.sleepFade, player.isPlaying, atTrackEnd)
            ?.let { handler.postDelayed(wakeUp, it) }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Arrêté par le lecteur à la fin du morceau, comme demandé : le minuteur a fait son travail.
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
            _state.value == SleepTimerState.TrackEnd
        ) {
            cancel()
            return
        }
        evaluate()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = evaluate()

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) = evaluate()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = evaluate()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = evaluate()

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = evaluate()

    fun release() = cancel()

    companion object {
        val COMMAND = SessionCommand("fr.devsecopsforce.ongaku.SLEEP_TIMER", Bundle.EMPTY)
        private const val ARG_MINUTES = "minutes"
        private const val TRACK_END = 0
        private const val CANCEL = -1

        private val _state = MutableStateFlow<SleepTimerState?>(null)
        val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

        fun minutesArgs(minutes: Int) = Bundle().apply { putInt(ARG_MINUTES, minutes) }
        fun trackEndArgs() = Bundle().apply { putInt(ARG_MINUTES, TRACK_END) }
        fun cancelArgs() = Bundle().apply { putInt(ARG_MINUTES, CANCEL) }
    }
}
