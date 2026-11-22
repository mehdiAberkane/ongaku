package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import fr.devsecopsforce.ongaku.playback.PlaybackStateKeeper
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Garde les listes, favoris, images par morceau, compteurs d'écoutes et la file enregistrée sur les
 * bons fichiers quand Android redistribue ses identifiants (restauration du téléphone, base
 * multimédia effacée, carte SD). Voir [TrackIdentity] pour la manière de retrouver un morceau.
 */
object LibraryIdentity {
    private const val TAG = "OngakuIdentity"
    private const val FILE_NAME = "track_index.json"
    private const val VERSION = 1

    /** Empreinte déjà lue dans ce processus : évite de relire le fichier à chaque chargement. */
    private var cached: Map<Long, TrackKey>? = null

    private val _generation = MutableStateFlow(0)

    /** Change à chaque correction du fichier des listes : l'interface doit alors le relire. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    data class Report(val recovered: Int, val missing: Int)

    private val _pendingReport = MutableStateFlow<Report?>(null)

    /** Dernière correction, pas encore montrée à l'utilisateur ; voir [reportShown]. */
    val pendingReport: StateFlow<Report?> = _pendingReport.asStateFlow()

    fun reportShown() {
        _pendingReport.value = null
    }

    fun check(context: Context, library: List<Track>): Unit = synchronized(AppDataStore.lock) {
        val file = File(context.filesDir, FILE_NAME)
        val previous = cached ?: readIndex(file)
        // Stockage pas encore monté au démarrage : rien ne prouve que les morceaux ont disparu.
        if (library.isEmpty() && !previous.isNullOrEmpty()) return

        val store = AppDataStore(context)
        val data = store.load()
        val counts = PlayCounts(context)
        val resume = PlaybackStateKeeper.prefs(context)
        val queue = resume.getString(PlaybackStateKeeper.KEY_QUEUE, null).orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }
        val current = resume.getString(PlaybackStateKeeper.KEY_CURRENT, null)?.toLongOrNull()
        val referenced = data.referencedIds() + counts.referencedIds() + queue + listOfNotNull(current)

        val result = TrackIdentity.reconcile(previous, library, referenced)

        if (result.remap.isNotEmpty()) {
            Log.i(TAG, "corrections ${result.remap.size}, retrouvés ${result.recovered}, introuvables ${result.missing}")
            try {
                store.save(data.remapped(result.remap))
            } catch (e: IOException) {
                // Sans le fichier des listes corrigé, on ne touche à rien d'autre : nouvel essai au
                // prochain chargement, l'empreinte n'étant pas mise à jour.
                Log.w(TAG, "fichier des listes non corrigé", e)
                return
            }
            counts.remap(result.remap)
            resume.edit {
                if (queue.isNotEmpty()) {
                    putString(PlaybackStateKeeper.KEY_QUEUE, queue.map { result.remap[it] ?: it }.joinToString(","))
                }
                if (current != null) {
                    putString(PlaybackStateKeeper.KEY_CURRENT, (result.remap[current] ?: current).toString())
                }
            }
            _generation.value++
            _pendingReport.value = Report(result.recovered, result.missing)
        }
        if (result.index != previous) writeIndex(file, result.index)
        cached = result.index
    }

    private fun readIndex(file: File): Map<Long, TrackKey>? {
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val tracks = root.getJSONArray("tracks")
            buildMap {
                for (i in 0 until tracks.length()) {
                    val entry = tracks.getJSONArray(i)
                    put(
                        entry.getLong(0),
                        TrackKey(
                            path = entry.getString(1),
                            size = entry.getLong(2),
                            title = entry.getString(3),
                            artist = entry.getString(4),
                            album = entry.getString(5),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            // Empreinte illisible : on repart de la bibliothèque actuelle, comme au premier lancement.
            Log.w(TAG, "empreinte illisible", e)
            null
        }
    }

    private fun writeIndex(file: File, index: Map<Long, TrackKey>) {
        val tracks = JSONArray()
        index.entries.sortedBy { it.key }.forEach { (id, key) ->
            tracks.put(JSONArray().put(id).put(key.path).put(key.size).put(key.title).put(key.artist).put(key.album))
        }
        val text = JSONObject().put("version", VERSION).put("tracks", tracks).toString()
        try {
            SafeFile.write(file, text.toByteArray(Charsets.UTF_8))
        } catch (e: IOException) {
            Log.w(TAG, "empreinte non enregistrée", e)
        }
    }

    /** Pour les tests : oublie l'empreinte gardée en mémoire. */
    internal fun resetForTests() {
        cached = null
        _generation.value = 0
        _pendingReport.value = null
    }
}

/** Tous les identifiants de morceaux que désignent les données de l'utilisateur. */
fun AppData.referencedIds(): Set<Long> =
    favorites + playlists.flatMap { it.trackIds } + trackBackgrounds.keys

fun AppData.remapped(remap: Map<Long, Long>): AppData {
    if (remap.isEmpty()) return this
    fun Long.mapped() = remap[this] ?: this
    return copy(
        favorites = favorites.map { it.mapped() }.toSet(),
        playlists = playlists.map { playlist ->
            playlist.copy(trackIds = playlist.trackIds.map { it.mapped() }.distinct())
        },
        trackBackgrounds = trackBackgrounds.entries.associate { (id, name) -> id.mapped() to name },
    )
}
