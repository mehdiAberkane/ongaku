package fr.devsecopsforce.ongaku.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fr.devsecopsforce.ongaku.widget.WidgetDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Retient la file d'attente pour la reprise et tient le widget à jour. */
class PlaybackStateKeeper(
    private val context: Context,
    private var player: ExoPlayer,
    private val scope: CoroutineScope,
) : Player.Listener {

    private val prefs: SharedPreferences = prefs(context)
    private val handler = Handler(Looper.getMainLooper())

    // Si Android coupe brutalement l'application, on ne perd que quelques secondes.
    private val periodicSave = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    init {
        player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
        player.repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
        player.addListener(this)
    }

    /** Le fondu enchaîné a passé la main à un autre lecteur, qui joue déjà le morceau suivant. */
    fun switchTo(newPlayer: ExoPlayer) {
        player.removeListener(this)
        player = newPlayer
        newPlayer.addListener(this)
        onIsPlayingChanged(newPlayer.isPlaying)
        savePosition()
        publish(newPlayer.isPlaying || newPlayer.playWhenReady)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(periodicSave)
        if (isPlaying) handler.postDelayed(periodicSave, SAVE_EVERY_MS)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHUFFLE, shuffleModeEnabled) }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        prefs.edit { putInt(KEY_REPEAT, repeatMode) }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_TIMELINE_CHANGED)) saveQueue()
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            savePosition()
            publish(player.isPlaying || player.playWhenReady)
        }
    }

    private fun saveQueue() {
        // Une file vide n'efface rien : la reprise doit survivre à l'arrêt du lecteur.
        if (player.mediaItemCount == 0) return
        val ids = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        prefs.edit { putString(KEY_QUEUE, ids.joinToString(",")) }
    }

    private fun savePosition() {
        val current = player.currentMediaItem ?: return
        prefs.edit {
            putString(KEY_CURRENT, current.mediaId)
            putLong(KEY_POSITION, player.currentPosition)
        }
    }

    private fun publish(playing: Boolean) {
        val metadata = player.currentMediaItem?.mediaMetadata ?: return
        val title = metadata.title?.toString()
        val artist = metadata.artist?.toString()
        val artwork = metadata.artworkUri
        scope.launch(Dispatchers.IO) {
            WidgetDisplay.save(context, title, artist, artwork, playing)
            WidgetDisplay.render(context)
        }
    }

    /** Appelé avant la libération du lecteur, tant que sa position est encore lisible. */
    fun release() {
        handler.removeCallbacks(periodicSave)
        savePosition()
        player.removeListener(this)
        val metadata = player.currentMediaItem?.mediaMetadata ?: return
        WidgetDisplay.save(
            context,
            metadata.title?.toString(),
            metadata.artist?.toString(),
            metadata.artworkUri,
            false,
        )
        WidgetDisplay.render(context)
    }

    companion object {
        const val KEY_QUEUE = "queue"
        const val KEY_CURRENT = "current"
        const val KEY_POSITION = "position"
        private const val SAVE_EVERY_MS = 5_000L
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("ongaku_resume", Context.MODE_PRIVATE)
    }
}
