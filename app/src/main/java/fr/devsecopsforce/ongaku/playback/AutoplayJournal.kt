package fr.devsecopsforce.ongaku.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dernières étapes de la lecture automatique (connexion reçue, sortie audio prête, relances…),
 * affichées dans les réglages : quand elle rate, on voit où. Reste dans le téléphone.
 */
object AutoplayJournal {
    private const val PREFS = "ongaku_autoplay_journal"
    private const val KEY = "entries"
    private const val SEPARATOR = "\n"
    const val MAX_ENTRIES = 40

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(context: Context, text: String, nowMs: Long = System.currentTimeMillis()) {
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(nowMs))
        val prefs = prefs(context)
        synchronized(this) {
            val updated = append(prefs.getString(KEY, null).orEmpty(), "$time  ${text.replace(SEPARATOR, " ")}")
            prefs.edit { putString(KEY, updated) }
        }
    }

    /** Du plus récent au plus ancien. */
    fun entries(context: Context): List<String> = split(prefs(context).getString(KEY, null).orEmpty()).asReversed()

    fun clear(context: Context) = prefs(context).edit { remove(KEY) }

    internal fun append(stored: String, entry: String): String =
        (split(stored) + entry).takeLast(MAX_ENTRIES).joinToString(SEPARATOR)

    private fun split(stored: String): List<String> = stored.split(SEPARATOR).filter { it.isNotBlank() }
}
