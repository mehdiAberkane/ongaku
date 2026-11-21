package fr.devsecopsforce.ongaku.playback

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import fr.devsecopsforce.ongaku.data.PlaybackSettings

/**
 * Reprend la lecture quand un appareil audio choisi dans les réglages se connecte. Attend que le son
 * puisse vraiment sortir vers lui, puis confie la lecture et sa surveillance au service
 * ([AutoplayGuard]) : un autoradio met plus de temps à démarrer que le téléphone.
 */
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
        val name = deviceName(device)
        AutoplayJournal.add(app, "connexion de $name reçue")
        val pending = goAsync()
        waitForRoute(app, device.address, name, SystemClock.elapsedRealtime(), pending)
    }

    /**
     * Sans sortie audio prête, la lecture partirait sur le haut-parleur du téléphone ou se perdrait :
     * on attend qu'elle apparaisse, au plus [AutoplayRules.ROUTE_TIMEOUT_MS].
     */
    private fun waitForRoute(app: Context, address: String, name: String, since: Long, pending: PendingResult) {
        val waited = SystemClock.elapsedRealtime() - since
        when {
            AutoplayGuard.routedTo(app, address) -> {
                AutoplayJournal.add(app, "sortie audio prête après ${"%.1f".format(waited / 1000.0)} s")
                start(app, address, name, pending)
            }
            waited >= AutoplayRules.ROUTE_TIMEOUT_MS -> {
                AutoplayJournal.add(app, "sortie audio pas prête après ${waited / 1000} s : lecture tentée quand même")
                start(app, address, name, pending)
            }
            else -> Handler(Looper.getMainLooper()).postDelayed(
                { waitForRoute(app, address, name, since, pending) },
                AutoplayRules.ROUTE_POLL_MS,
            )
        }
    }

    private fun start(app: Context, address: String, name: String, pending: PendingResult) {
        val future = MediaController.Builder(
            app,
            SessionToken(app, ComponentName(app, PlaybackService::class.java)),
        ).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    AutoplayJournal.add(app, "lecteur injoignable")
                    pending.finish()
                    return@addListener
                }
                val result = controller.sendCustomCommand(AutoplayGuard.START, AutoplayGuard.startArgs(address, name))
                result.addListener(
                    {
                        controller.release()
                        pending.finish()
                    },
                    ContextCompat.getMainExecutor(app),
                )
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun deviceName(device: BluetoothDevice): String = try {
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else device.name) ?: device.address
    } catch (e: SecurityException) {
        device.address
    }
}
