package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PlaybackSettings(context: Context) {
    val prefs: SharedPreferences =
        context.getSharedPreferences("ongaku_settings", Context.MODE_PRIVATE)

    var crossfadeSeconds: Int
        get() = prefs.getInt(KEY_CROSSFADE, 0)
        set(value) = prefs.edit { putInt(KEY_CROSSFADE, value) }

    var pauseOnNoisy: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_ON_NOISY, true)
        set(value) = prefs.edit { putBoolean(KEY_PAUSE_ON_NOISY, value) }

    var lockScreenPlayer: Boolean
        get() = prefs.getBoolean(KEY_LOCK_SCREEN_PLAYER, false)
        set(value) = prefs.edit { putBoolean(KEY_LOCK_SCREEN_PLAYER, value) }

    var bluetoothAutoplay: Boolean
        get() = prefs.getBoolean(KEY_BLUETOOTH_AUTOPLAY, false)
        set(value) = prefs.edit { putBoolean(KEY_BLUETOOTH_AUTOPLAY, value) }

    /** Adresses des appareils choisis : contrairement au nom, elles ne changent jamais. */
    var bluetoothDevices: Set<String>
        get() = prefs.getStringSet(KEY_BLUETOOTH_DEVICES, null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_BLUETOOTH_DEVICES, value) }

    var listenedMs: Long
        get() = prefs.getLong(KEY_LISTENED, 0)
        set(value) = prefs.edit { putLong(KEY_LISTENED, value) }

    companion object {
        const val MAX_CROSSFADE_SECONDS = 12
        const val KEY_PAUSE_ON_NOISY = "pause_on_noisy"
        private const val KEY_CROSSFADE = "crossfade_seconds"
        private const val KEY_LISTENED = "listened_ms"
        private const val KEY_LOCK_SCREEN_PLAYER = "lock_screen_player"
        private const val KEY_BLUETOOTH_AUTOPLAY = "bluetooth_autoplay"
        private const val KEY_BLUETOOTH_DEVICES = "bluetooth_devices"
    }
}
