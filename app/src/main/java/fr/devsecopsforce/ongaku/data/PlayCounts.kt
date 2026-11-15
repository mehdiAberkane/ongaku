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

    /** Reporte les écoutes sur les nouveaux identifiants ; deux anciens vers un même morceau s'additionnent. */
    fun remap(remap: Map<Long, Long>) {
        val before = all()
        if (before.keys.none { it in remap }) return
        val after = HashMap<Long, Int>()
        before.forEach { (id, count) ->
            val target = remap[id] ?: id
            after[target] = (after[target] ?: 0) + count
        }
        prefs.edit(commit = true) {
            clear()
            after.forEach { (id, count) -> putInt(id.toString(), count) }
        }
    }
}
