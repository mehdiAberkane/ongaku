package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Fichier distinct de ongaku.json : c'est le service de lecture qui écrit ici, pas l'interface. */
class PlayCounts(context: Context) {
    val prefs: SharedPreferences =
        context.getSharedPreferences("ongaku_play_counts", Context.MODE_PRIVATE)

    fun increment(trackId: Long) {
        val key = trackId.toString()
        prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
    }

    fun all(): Map<Long, Int> = prefs.all.entries.mapNotNull { (key, value) ->
        val id = key.toLongOrNull()
        if (id != null && value is Int) id to value else null
    }.toMap()
}
