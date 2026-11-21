// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.annotation.StringRes
import androidx.media3.session.SessionCommand
import fr.devsecopsforce.ongaku.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lecture automatique à la connexion d'un appareil Bluetooth choisi, côté service : lance la lecture
 * puis la surveille quelques dizaines de secondes (voir [AutoplayRules]). Le service vit plus
 * longtemps que le récepteur de la connexion, qui ne fait que l'appeler.
 */
class AutoplayGuard(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> Player?,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val checks = mutableListOf<Runnable>()

    /** Appareil surveillé ; null hors surveillance. */
    private var deviceAddress: String? = null
    private var resumes = 0
    private var stoppedByUser = false

    fun start(address: String, name: String) {
        stop()
        val current = player() ?: return
        deviceAddress = address
        resumes = 0
        stoppedByUser = false
        if (current.mediaItemCount == 0) {
            // La file est chargée ici et non par le récepteur : Media3 exécuterait cette commande
            // avant l'ajout demandé juste avant elle.
            scope.launch {
                try {
                    val queue = ResumeQueue.load(context)
                    current.setMediaItems(queue.mediaItems, queue.startIndex, queue.startPositionMs)
                    play(current, context.getString(R.string.autoplay_log_started, name))
                } catch (e: Exception) {
                    journal(R.string.autoplay_log_nothing_to_play)
                    stop()
                    return@launch
                }
                scheduleChecks()
            }
            return
        }
        if (current.isPlaying) {
            journal(R.string.autoplay_log_already_playing)
            deviceAddress = null
            return
        }
        play(current, context.getString(R.string.autoplay_log_started, name))
        scheduleChecks()
    }

    /** Commande de lecture ou de pause reçue par la session, avec l'application qui l'envoie. */
    fun onPlayPauseRequest(controllerPackage: String) {
        if (deviceAddress == null || stoppedByUser) return
        if (AutoplayRules.userTookOver(controllerPackage, context.packageName)) {
            stoppedByUser = true
            journal(R.string.autoplay_log_user_took_over, controllerPackage)
            stop()
        } else {
            journal(R.string.autoplay_log_play_pause_from, controllerPackage)
        }
    }

    private fun play(current: Player, message: String) {
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
        current.play()
        journal(message)
    }

    private fun scheduleChecks() {
        AutoplayRules.CHECKS_MS.forEachIndexed { index, delay ->
            val check = Runnable { check(delay, last = index == AutoplayRules.CHECKS_MS.lastIndex) }
            checks += check
            handler.postDelayed(check, delay)
        }
    }

    private fun check(delayMs: Long, last: Boolean) {
        val address = deviceAddress ?: return
        val current = player() ?: return
        val routed = routedTo(address)
        if (AutoplayRules.shouldResume(current.isPlaying, stoppedByUser, resumes, routed)) {
            resumes++
            play(current, context.getString(R.string.autoplay_log_resumed, resumes, delayMs / 1000))
        } else if (!current.isPlaying && !routed) {
            journal(R.string.autoplay_log_not_routed)
        }
        if (last) {
            journal(if (current.isPlaying) R.string.autoplay_log_end_playing else R.string.autoplay_log_end_stopped)
            stop()
        }
    }

    /** Le son sort-il vers cet appareil Bluetooth ? */
    private fun routedTo(address: String): Boolean = routedTo(context, address)

    private fun journal(message: String) = AutoplayJournal.add(context, message)

    private fun journal(@StringRes message: Int, vararg args: Any) = journal(context.getString(message, *args))

    fun stop() {
        checks.forEach(handler::removeCallbacks)
        checks.clear()
        deviceAddress = null
    }

    companion object {
        private const val PREFIX = "fr.devsecopsforce.ongaku.autoplay."
        private const val ARG_ADDRESS = "address"
        private const val ARG_NAME = "name"

        /** Envoyée par [BluetoothAutoplayReceiver] quand un appareil choisi est connecté et prêt. */
        val START = SessionCommand("${PREFIX}START", Bundle.EMPTY)

        fun startArgs(address: String, name: String) = Bundle().apply {
            putString(ARG_ADDRESS, address)
            putString(ARG_NAME, name)
        }

        fun address(args: Bundle): String? = args.getString(ARG_ADDRESS)

        fun name(args: Bundle): String = args.getString(ARG_NAME).orEmpty()

        /** Sortie audio Bluetooth (A2DP) de cet appareil présente. */
        fun routedTo(context: Context, address: String): Boolean {
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                    // Avant Android 9, l'adresse n'est pas fournie : toute sortie A2DP convient.
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || device.address.equals(address, ignoreCase = true))
            }
        }
    }
}
