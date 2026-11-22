package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Une écoute comptée : l'heure, le morceau, et ses tags au moment de l'écoute. Les tags gardent
 * les classements justes si le fichier disparaît, et permettent de retrouver le morceau sur un autre
 * téléphone ([PlaylistBackup]). [trackId] vaut 0 pour un morceau introuvable à l'import.
 */
data class Play(val timeMs: Long, val trackId: Long, val title: String, val artist: String, val album: String)

/**
 * Historique d'écoute, dans le stockage privé : les écoutes datées (`history.json`) et le temps
 * écouté par jour. Écrit par le service de lecture, lu par l'écran des statistiques, dans le même
 * processus : une seule instance ([get]) tient les deux à jour.
 */
class ListeningHistory private constructor(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val daysPrefs = context.getSharedPreferences("ongaku_listening_days", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _plays = MutableStateFlow(read())
    val plays: StateFlow<List<Play>> = _plays.asStateFlow()

    private val _days = MutableStateFlow(readDays())

    /** Temps écouté par jour (date locale), en millisecondes. */
    val days: StateFlow<Map<LocalDate, Long>> = _days.asStateFlow()

    fun record(play: Play) = update { it + play }

    /** Ajoute [listenedMs] au jour de [nowMs]. */
    fun addListening(listenedMs: Long, nowMs: Long = System.currentTimeMillis()) {
        if (listenedMs <= 0) return
        val day = dayOf(nowMs)
        synchronized(lock) {
            val total = (_days.value[day] ?: 0L) + listenedMs
            daysPrefs.edit().putLong(day.toString(), total).apply()
            _days.value = _days.value + (day to total)
        }
    }

    /** Identifiants désignés par l'historique : voir [LibraryIdentity]. */
    fun referencedIds(): Set<Long> = _plays.value.mapTo(HashSet()) { it.trackId }.apply { remove(0L) }

    fun remap(remap: Map<Long, Long>) {
        if (_plays.value.none { it.trackId in remap }) return
        update { plays -> plays.map { play -> remap[play.trackId]?.let { play.copy(trackId = it) } ?: play } }
    }

    /**
     * Ajoute une sauvegarde sans rien effacer : une écoute déjà présente (même heure, même titre)
     * n'est pas doublée ; pour un jour connu des deux côtés, le plus grand temps l'emporte (c'est le
     * même jour vu par deux sauvegardes, pas deux écoutes à additionner).
     */
    fun merge(imported: List<Play>, importedDays: Map<LocalDate, Long>) {
        update { plays ->
            val known = plays.mapTo(HashSet()) { it.timeMs to it.title.searchKey() }
            (plays + imported.filter { (it.timeMs to it.title.searchKey()) !in known }).sortedBy { it.timeMs }
        }
        synchronized(lock) {
            val merged = _days.value.toMutableMap()
            importedDays.forEach { (day, ms) -> merged[day] = maxOf(merged[day] ?: 0L, ms) }
            val editor = daysPrefs.edit()
            merged.forEach { (day, ms) -> editor.putLong(day.toString(), ms) }
            editor.apply()
            _days.value = merged
        }
    }

    private fun update(transform: (List<Play>) -> List<Play>) = synchronized(lock) {
        val updated = transform(_plays.value)
        _plays.value = updated
        try {
            SafeFile.write(file, serialize(updated).toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            // Gardé en mémoire : le prochain enregistrement réessaiera tout le fichier.
            Log.w(TAG, "historique non enregistré", e)
        }
    }

    private fun read(): List<Play> {
        if (!file.exists()) return emptyList()
        return try {
            parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            // Illisible : on le met de côté plutôt que de l'écraser au prochain enregistrement.
            Log.w(TAG, "historique illisible", e)
            file.renameTo(File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}"))
            emptyList()
        }
    }

    private fun readDays(): Map<LocalDate, Long> = daysPrefs.all.entries.mapNotNull { (key, value) ->
        val day = runCatching { LocalDate.parse(key) }.getOrNull()
        if (day != null && value is Long) day to value else null
    }.toMap()

    companion object {
        private const val TAG = "OngakuHistory"
        private const val FILE_NAME = "history.json"
        private const val VERSION = 1

        @Volatile
        private var instance: ListeningHistory? = null

        fun get(context: Context): ListeningHistory = instance ?: synchronized(this) {
            instance ?: ListeningHistory(context.applicationContext).also { instance = it }
        }

        /** Pour les tests : relit les fichiers au prochain [get]. */
        internal fun resetForTests() {
            instance = null
        }

        fun dayOf(timeMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()

        /** Format compact : une écoute par tableau [heure, id, titre, artiste, album]. */
        fun serialize(plays: List<Play>): String = JSONObject()
            .put("version", VERSION)
            .put(
                "plays",
                JSONArray(plays.map { JSONArray().put(it.timeMs).put(it.trackId).put(it.title).put(it.artist).put(it.album) }),
            )
            .toString()

        fun parse(text: String): List<Play> {
            val array = JSONObject(text).getJSONArray("plays")
            return List(array.length()) { index ->
                val entry = array.getJSONArray(index)
                Play(entry.getLong(0), entry.getLong(1), entry.getString(2), entry.getString(3), entry.getString(4))
            }
        }
    }
}
