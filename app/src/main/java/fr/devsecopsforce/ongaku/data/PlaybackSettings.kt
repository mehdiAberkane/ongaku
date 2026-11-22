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

    /** Range « A feat. B » sous l'artiste A dans la liste des artistes. */
    var mergeFeaturing: Boolean
        get() = prefs.getBoolean(KEY_MERGE_FEATURING, true)
        set(value) = prefs.edit { putBoolean(KEY_MERGE_FEATURING, value) }

    /** Minuteur de sommeil : le volume baisse sur la dernière minute avant l'arrêt. */
    var sleepFade: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_FADE, true)
        set(value) = prefs.edit { putBoolean(KEY_SLEEP_FADE, value) }

    /** Taille des listes automatiques (ajouts récents, plus écoutés…) : voir [SmartLists.SIZES]. */
    var smartListSize: Int
        get() = SmartLists.size(prefs.getInt(KEY_SMART_SIZE, SmartLists.DEFAULT_SIZE))
        set(value) = prefs.edit { putInt(KEY_SMART_SIZE, SmartLists.size(value)) }

    var listenedMs: Long
        get() = prefs.getLong(KEY_LISTENED, 0)
        set(value) = prefs.edit { putLong(KEY_LISTENED, value) }

    companion object {
        const val MAX_CROSSFADE_SECONDS = 12
        const val KEY_PAUSE_ON_NOISY = "pause_on_noisy"
        const val KEY_CROSSFADE = "crossfade_seconds"
        private const val KEY_LISTENED = "listened_ms"
        private const val KEY_LOCK_SCREEN_PLAYER = "lock_screen_player"
        private const val KEY_BLUETOOTH_AUTOPLAY = "bluetooth_autoplay"
        private const val KEY_BLUETOOTH_DEVICES = "bluetooth_devices"
        private const val KEY_MERGE_FEATURING = "merge_featuring"
        private const val KEY_SLEEP_FADE = "sleep_fade"
        private const val KEY_SMART_SIZE = "smart_list_size"
    }
}
