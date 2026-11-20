// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings

/**
 * ExoPlayer ne sait pas superposer deux morceaux : le fondu enchaîné utilise donc deux lecteurs qui
 * jouent à tour de rôle. Quelques secondes avant la fin, un second lecteur se prépare sur le morceau
 * suivant. Pendant le fondu, le lecteur actif termine lui-même son morceau en baissant pendant que
 * le second démarre le suivant en montant : aucun son ne change de lecteur en cours de route. À la
 * fin, le second devient le lecteur actif, celui de la session que suivent la notification et
 * l'interface, et l'ancien est libéré.
 *
 * Ce contrôleur compte aussi le temps d'écoute.
 *
 * Rien ne tourne en continu pendant la lecture : un seul réveil est programmé juste avant la zone
 * de fondu (puis au début du fondu), et reprogrammé à chaque événement du lecteur (pause, saut,
 * changement de morceau ou de file). Seul le fondu lui-même avance par pas fins. Le temps d'écoute
 * se compte aux changements d'état du lecteur.
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

    /** Lecteur de relève, préparé sur le morceau suivant. */
    private var incoming: ExoPlayer? = null

    /** Position dans la file du lecteur actif du morceau préparé par la relève. */
    private var incomingIndex = C.INDEX_UNSET
    private var fading = false
    private var fadeStartMs = 0L
    private var fadeDurationMs = 0L
    private var fadeEndPositionMs = 0L

    /** Début de la lecture en cours (horloge système) ; null à l'arrêt. */
    private var playingSinceMs: Long? = null
    private var pendingListenedMs = 0L
    private var currentTrackListenedMs = 0L
    private var currentTrackCounted = false

    private val watchWakeUp = Runnable { evaluate() }
    private val countWakeUp = Runnable {
        countPlayIfDue()
        scheduleCount()
    }
    private val fadeStep = object : Runnable {
        override fun run() {
            updateFade()
            // Pas fins pendant le fondu : le volume glisse au lieu de monter par paliers.
            if (fading) handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    /** La relève prévient quand elle est prête : le fondu peut alors être programmé. */
    private val incomingListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) evaluate()
        }
    }

    init {
        player.addListener(this)
        if (player.isPlaying) startListening()
        evaluate()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            // Le lecteur actif s'arrête de lui-même à la fin de son morceau pendant le fondu.
            if (fading && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                finishFade()
                return
            }
            cancelIncoming()
        }
        evaluate()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startListening() else stopListening()
        scheduleCount()
        evaluate()
    }

    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
        if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) cancelIncoming()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Le temps écouté jusqu'ici revient au morceau précédent.
        val playing = playingSinceMs != null
        stopListening()
        resetTrackListening()
        if (playing && player.isPlaying) startListening()
        scheduleCount()
        // Morceau suivant ou précédent demandé à la main : la relève ne correspond plus.
        cancelIncoming()
        evaluate()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) = evaluate()

    // La file a changé : le lecteur de relève ne joue peut-être plus le bon morceau suivant. La durée
    // du morceau, connue à ce moment, change aussi le seuil d'une écoute.
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        cancelIncoming()
        scheduleCount()
        evaluate()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        cancelIncoming()
        evaluate()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        cancelIncoming()
        evaluate()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = evaluate()

    /** La durée du fondu a changé dans les réglages. */
    fun onSettingsChanged() {
        if (!fading) cancelIncoming()
        evaluate()
    }

    // --- Temps d'écoute ---

    private fun startListening() {
        if (playingSinceMs == null) playingSinceMs = SystemClock.elapsedRealtime()
    }

    /** Arrêt de la lecture : le temps écouté est ajouté au morceau et au total. */
    private fun stopListening() {
        val since = playingSinceMs ?: return
        val listened = SystemClock.elapsedRealtime() - since
        playingSinceMs = null
        pendingListenedMs += listened
        currentTrackListenedMs += listened
        countPlayIfDue()
        flushListening()
    }

    private fun trackListenedMs(): Long =
        currentTrackListenedMs + (playingSinceMs?.let { SystemClock.elapsedRealtime() - it } ?: 0)

    private fun resetTrackListening() {
        currentTrackListenedMs = 0
        currentTrackCounted = false
    }

    /** Une écoute compte une seule fois par lecture : voir [CrossfadeRules.shouldCountPlay]. */
    private fun countPlayIfDue() {
        if (currentTrackCounted) return
        if (!CrossfadeRules.shouldCountPlay(trackListenedMs(), player.knownDuration())) return
        currentTrackCounted = true
        player.currentMediaItem?.mediaId?.toLongOrNull()?.let(playCounts::increment)
    }

    /** Un seul réveil, au moment où le morceau atteindra le seuil d'une écoute. */
    private fun scheduleCount() {
        handler.removeCallbacks(countWakeUp)
        if (currentTrackCounted || playingSinceMs == null) return
        val delay = CrossfadeRules.playCountDelay(trackListenedMs(), player.knownDuration())
        handler.postDelayed(countWakeUp, delay)
    }

    private fun flushListening() {
        if (pendingListenedMs == 0L) return
        settings.listenedMs += pendingListenedMs
        pendingListenedMs = 0
    }

    private fun Player.knownDuration(): Long? = duration.takeUnless { it == C.TIME_UNSET }

    // --- Fondu ---

    private fun incomingState(): CrossfadeRules.Incoming {
        val next = incoming
        return when {
            next == null -> CrossfadeRules.Incoming.NONE
            next.playbackState == Player.STATE_READY -> CrossfadeRules.Incoming.READY
            else -> CrossfadeRules.Incoming.PREPARING
        }
    }

    /** Relevé de l'état du lecteur actif, décision, puis programmation du prochain relevé utile. */
    private fun evaluate() {
        handler.removeCallbacks(watchWakeUp)
        if (fading) return
        watchForTrackEnd()
        if (fading) return
        val delay = CrossfadeRules.nextWatchDelay(
            fadeMs = settings.crossfadeSeconds * 1000L,
            durationMs = player.knownDuration(),
            positionMs = player.currentPosition,
            isPlaying = player.isPlaying,
            hasNext = player.hasNextMediaItem(),
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
            incoming = incomingState(),
            speed = player.playbackParameters.speed,
        ) ?: return
        handler.postDelayed(watchWakeUp, delay)
    }

    private fun watchForTrackEnd() {
        val duration = player.knownDuration()
        val action = CrossfadeRules.watch(
            fadeMs = settings.crossfadeSeconds * 1000L,
            durationMs = duration,
            positionMs = player.currentPosition,
            isPlaying = player.isPlaying,
            hasNext = player.hasNextMediaItem(),
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
            incoming = incomingState(),
        )
        when (action) {
            CrossfadeRules.Watch.NOTHING -> Unit
            CrossfadeRules.Watch.CANCEL -> cancelIncoming()
            CrossfadeRules.Watch.PREPARE -> prepareIncoming()
            CrossfadeRules.Watch.START ->
                if (duration != null) startFade(duration - player.currentPosition, duration)
        }
    }

    /**
     * Prépare la relève sur le seul morceau suivant, au volume nul et à l'arrêt. Le reste de la file
     * ne lui est donné qu'au moment où elle prend la main ([completeQueue]) : une relève abandonnée
     * (pause, saut, file modifiée) ne coûte pas une copie de toute la file.
     */
    private fun prepareIncoming() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        incomingIndex = nextIndex
        incoming = createPlayer().apply {
            volume = 0f
            setMediaItem(player.getMediaItemAt(nextIndex))
            playWhenReady = false
            addListener(incomingListener)
            prepare()
        }
    }

    /**
     * La relève reçoit la file complète autour du morceau qu'elle joue, avec le même ordre
     * aléatoire et le même mode de répétition que le lecteur actif, pour que « suivant » reste le
     * même. Ajouter avant et après le morceau en cours ne l'interrompt pas.
     */
    @OptIn(UnstableApi::class)
    private fun completeQueue(next: ExoPlayer, old: ExoPlayer) {
        val index = incomingIndex
        val count = old.mediaItemCount
        if (index !in 0 until count) return
        val shuffleOrder = currentShuffleOrder(old)
        if (index > 0) next.addMediaItems(0, List(index) { old.getMediaItemAt(it) })
        if (index + 1 < count) next.addMediaItems(List(count - index - 1) { old.getMediaItemAt(index + 1 + it) })
        if (shuffleOrder != null) next.setShuffleOrder(shuffleOrder)
        next.shuffleModeEnabled = old.shuffleModeEnabled
        next.repeatMode = old.repeatMode
    }

    /** Ordre de lecture aléatoire du lecteur actif, relu morceau par morceau. */
    @OptIn(UnstableApi::class)
    private fun currentShuffleOrder(player: Player): ShuffleOrder? {
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
        handler.post(fadeStep)
    }

    private fun updateFade() {
        val next = incoming ?: return cancelIncoming()
        val step = CrossfadeRules.fade(
            remainingMs = fadeEndPositionMs - player.currentPosition,
            fadeDurationMs = fadeDurationMs,
            elapsedMs = SystemClock.elapsedRealtime() - fadeStartMs,
        )
        when (step) {
            CrossfadeRules.Fade.Cancel -> {
                cancelIncoming()
                evaluate()
            }
            CrossfadeRules.Fade.Finish -> finishFade()
            is CrossfadeRules.Fade.Volumes -> {
                next.volume = step.incoming
                player.volume = step.outgoing
            }
        }
    }

    /** La relève devient le lecteur actif ; l'ancien, arrivé au bout de son morceau, est libéré. */
    private fun finishFade() {
        val next = incoming ?: return cancelIncoming()
        val old = player
        fading = false
        handler.removeCallbacks(fadeStep)
        incoming = null
        next.removeListener(incomingListener)

        // Avant d'écouter la relève : compléter sa file ne doit pas être pris pour un changement.
        completeQueue(next, old)
        // Le temps écouté jusqu'ici revient au morceau qui se termine.
        stopListening()
        old.removeListener(this)
        next.volume = 1f
        player = next
        next.addListener(this)
        resetTrackListening()
        if (next.isPlaying) startListening()
        onPlayerSwapped(next)
        old.release()
        // L'ancien lecteur a rendu le focus audio en se libérant : la relève le reprend.
        next.setAudioAttributes(next.audioAttributes, /* handleAudioFocus = */ true)
        next.setHandleAudioBecomingNoisy(settings.pauseOnNoisy)
        scheduleCount()
        evaluate()
    }

    /** Abandonne la relève : le lecteur actif reprend son volume et enchaînera lui-même. */
    private fun cancelIncoming() {
        incoming?.let {
            it.removeListener(incomingListener)
            it.release()
        }
        incoming = null
        if (fading) {
            fading = false
            handler.removeCallbacks(fadeStep)
            player.pauseAtEndOfMediaItems = false
            player.volume = 1f
        }
    }

    fun release() {
        handler.removeCallbacks(watchWakeUp)
        handler.removeCallbacks(countWakeUp)
        handler.removeCallbacks(fadeStep)
        stopListening()
        flushListening()
        cancelIncoming()
        player.removeListener(this)
    }

    private companion object {
        const val FADE_STEP_MS = 25L
    }
}
