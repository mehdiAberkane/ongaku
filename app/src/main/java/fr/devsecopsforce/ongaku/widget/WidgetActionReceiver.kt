package fr.devsecopsforce.ongaku.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import fr.devsecopsforce.ongaku.playback.PlaybackService
import fr.devsecopsforce.ongaku.playback.ResumeQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Se connecte au lecteur, en le démarrant s'il le faut, puis exécute le bouton touché. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val pending = goAsync()
        val future = MediaController.Builder(
            app,
            SessionToken(app, ComponentName(app, PlaybackService::class.java)),
        ).buildAsync()

        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    pending.finish()
                    return@addListener
                }
                fun finish() {
                    // Laisse à la commande le temps d'arriver au lecteur avant de se déconnecter.
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            controller.release()
                            pending.finish()
                        },
                        RELEASE_DELAY_MS,
                    )
                }
                if (controller.mediaItemCount == 0) {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val queue = ResumeQueue.load(app)
                            controller.setMediaItems(queue.mediaItems, queue.startIndex, queue.startPositionMs)
                            controller.prepare()
                            controller.play()
                            if (action == ACTION_NEXT) controller.seekToNextMediaItem()
                        } catch (e: Exception) {
                            Log.w(TAG, "Reprise impossible", e)
                            Toast.makeText(app, "Aucun morceau à lire.", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                } else {
                    apply(controller, action)
                    finish()
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun apply(player: Player, action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> if (player.playWhenReady) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PREVIOUS -> player.seekToPrevious()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "fr.devsecopsforce.ongaku.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "fr.devsecopsforce.ongaku.widget.NEXT"
        const val ACTION_PREVIOUS = "fr.devsecopsforce.ongaku.widget.PREVIOUS"
        private const val TAG = "OngakuWidget"
        private const val RELEASE_DELAY_MS = 500L
    }
}
