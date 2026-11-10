package fr.devsecopsforce.ongaku.playback

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Reprend la lecture quand un appareil audio choisi dans les réglages se connecte. */
class BluetoothAutoplayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
        if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED) return
        val device = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        ) ?: return
        val settings = PlaybackSettings(context)
        if (!settings.bluetoothAutoplay || device.address !in settings.bluetoothDevices) return

        val app = context.applicationContext
        val pending = goAsync()
        // Sans ce délai, le début du morceau peut sortir sur le haut-parleur du téléphone.
        Handler(Looper.getMainLooper()).postDelayed({ resume(app, pending) }, ROUTING_DELAY_MS)
    }

    private fun resume(app: Context, pending: PendingResult) {
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
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        // Une lecture déjà en cours n'est jamais interrompue ni déplacée.
                        if (!controller.isPlaying) {
                            if (controller.mediaItemCount == 0) {
                                val queue = ResumeQueue.load(app)
                                controller.setMediaItems(queue.mediaItems, queue.startIndex, queue.startPositionMs)
                            }
                            controller.prepare()
                            controller.play()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Lecture automatique impossible", e)
                    }
                    // Laisse à la commande le temps d'arriver au lecteur avant de se déconnecter.
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            controller.release()
                            pending.finish()
                        },
                        RELEASE_DELAY_MS,
                    )
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private companion object {
        const val TAG = "OngakuBluetooth"
        const val ROUTING_DELAY_MS = 1500L
        const val RELEASE_DELAY_MS = 500L
    }
}
