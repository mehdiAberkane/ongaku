package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Écoutes par morceau : leur nombre, et l'heure de la dernière. Fichiers distincts de ongaku.json :
 * c'est le service de lecture qui écrit ici, pas l'interface.
 */
class PlayCounts(context: Context) {
    val prefs: SharedPreferences =
        context.getSharedPreferences("ongaku_play_counts", Context.MODE_PRIVATE)

    /** Heure (ms depuis 1970) de la dernière écoute comptée ; vide pour les écoutes d'avant cet historique. */
    val lastPlayedPrefs: SharedPreferences =
        context.getSharedPreferences("ongaku_last_played", Context.MODE_PRIVATE)

    fun increment(trackId: Long, nowMs: Long = System.currentTimeMillis()) {
        val key = trackId.toString()
        // L'heure d'abord : l'interface relit les deux quand le compteur change.
        lastPlayedPrefs.edit { putLong(key, nowMs) }
        prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
    }

    fun all(): Map<Long, Int> = prefs.all.entries.mapNotNull { (key, value) ->
        val id = key.toLongOrNull()
        if (id != null && value is Int) id to value else null
    }.toMap()

    fun lastPlayed(): Map<Long, Long> = lastPlayedPrefs.all.entries.mapNotNull { (key, value) ->
        val id = key.toLongOrNull()
        if (id != null && value is Long) id to value else null
    }.toMap()

    /** Identifiants désignés par les écoutes : voir [LibraryIdentity]. */
    fun referencedIds(): Set<Long> = all().keys + lastPlayed().keys

    /**
     * Reporte les écoutes sur les nouveaux identifiants ; deux anciens vers un même morceau
     * additionnent leurs écoutes et gardent la plus récente.
     */
    fun remap(remap: Map<Long, Long>) {
        val counts = all()
        if (counts.keys.any { it in remap }) {
            val after = HashMap<Long, Int>()
            counts.forEach { (id, count) ->
                val target = remap[id] ?: id
                after[target] = (after[target] ?: 0) + count
            }
            prefs.edit(commit = true) {
                clear()
                after.forEach { (id, count) -> putInt(id.toString(), count) }
            }
        }
        val times = lastPlayed()
        if (times.keys.any { it in remap }) {
            val after = HashMap<Long, Long>()
            times.forEach { (id, time) ->
                val target = remap[id] ?: id
                after[target] = maxOf(after[target] ?: 0L, time)
            }
            lastPlayedPrefs.edit(commit = true) {
                clear()
                after.forEach { (id, time) -> putLong(id.toString(), time) }
            }
        }
    }
}
