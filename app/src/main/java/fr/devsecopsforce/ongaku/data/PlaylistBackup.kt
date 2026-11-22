package fr.devsecopsforce.ongaku.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Les identifiants Android changent d'un téléphone à l'autre : la sauvegarde décrit donc chaque
 * morceau par ses tags, et l'import le retrouve dans la bibliothèque du moment.
 */
object PlaylistBackup {
    private const val FORMAT = "ongaku-backup"
    private const val VERSION = 1

    data class Imported(
        val favorites: Set<Long>,
        val playlists: List<Pair<String, List<Long>>>,
        val missingTracks: Int,
        /** Listes par règle : sans morceau, elles se recopient telles quelles. */
        val ruleLists: List<RuleList> = emptyList(),
    )

    fun serialize(data: AppData, tracks: List<Track>): String {
        val byId = tracks.associateBy { it.id }
        fun refs(ids: Iterable<Long>) = JSONArray(ids.mapNotNull(byId::get).map(::toRef))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("favorites", refs(data.favorites))
            .put(
                "playlists",
                JSONArray(
                    data.playlists.map {
                        JSONObject().put("name", it.name).put("tracks", refs(it.trackIds))
                    },
                ),
            )
            .put(
                "ruleLists",
                JSONArray(
                    data.ruleLists.map {
                        JSONObject().put("name", it.name).put("genre", it.genre)
                            .put("yearFrom", it.yearFrom).put("yearTo", it.yearTo)
                            .put("minSeconds", it.minSeconds).put("maxSeconds", it.maxSeconds)
                            .put("folder", it.folder)
                    },
                ),
            )
            .toString(2)
    }

    private fun toRef(track: Track) = JSONObject()
        .put("title", track.title)
        .put("artist", track.artist)
        .put("album", track.album)

    @Throws(JSONException::class)
    fun parse(text: String, tracks: List<Track>): Imported {
        val root = JSONObject(text)
        if (root.getString("format") != FORMAT) throw JSONException("format inconnu")

        val exact = tracks.associateBy { Triple(it.title.norm(), it.artist.norm(), it.album.norm()) }
        val loose = tracks.associateBy { it.title.norm() to it.artist.norm() }
        var missing = 0

        fun resolve(array: JSONArray): List<Long> = List(array.length()) { index ->
            val ref = array.getJSONObject(index)
            val title = ref.getString("title").norm()
            val artist = ref.getString("artist").norm()
            val track = exact[Triple(title, artist, ref.getString("album").norm())]
                ?: loose[title to artist]
            if (track == null) missing++
            track?.id
        }.filterNotNull().distinct()

        val favorites = resolve(root.getJSONArray("favorites")).toSet()
        val playlists = root.getJSONArray("playlists").let { array ->
            List(array.length()) { index ->
                val playlist = array.getJSONObject(index)
                playlist.getString("name") to resolve(playlist.getJSONArray("tracks"))
            }
        }
        // Absent des sauvegardes d'avant les listes par règle.
        val ruleLists = root.optJSONArray("ruleLists")?.let { array ->
            List(array.length()) { index ->
                val rule = array.getJSONObject(index)
                RuleList(
                    id = "",
                    name = rule.getString("name"),
                    genre = rule.optString("genre"),
                    yearFrom = rule.optInt("yearFrom"),
                    yearTo = rule.optInt("yearTo"),
                    minSeconds = rule.optInt("minSeconds"),
                    maxSeconds = rule.optInt("maxSeconds"),
                    folder = rule.optString("folder"),
                )
            }
        }.orEmpty()
        return Imported(favorites, playlists, missing, ruleLists)
    }

    /**
     * Ajoute la sauvegarde à l'existant sans rien effacer : une liste du même nom (sans tenir compte
     * de la casse ni des espaces autour) reçoit les morceaux qui lui manquent, les autres sont créées.
     */
    fun merge(data: AppData, imported: Imported, newId: () -> String): AppData {
        val playlists = data.playlists.toMutableList()
        imported.playlists.forEach { (name, ids) ->
            val index = playlists.indexOfFirst { it.name.trim().equals(name.trim(), ignoreCase = true) }
            if (index >= 0) {
                playlists[index] = playlists[index].let {
                    it.copy(trackIds = (it.trackIds + ids).distinct())
                }
            } else {
                playlists += Playlist(newId(), name.trim(), ids)
            }
        }
        // Une liste par règle du même nom existe déjà : on la garde telle quelle.
        val ruleLists = data.ruleLists + imported.ruleLists
            .filter { rule -> data.ruleLists.none { it.name.trim().equals(rule.name.trim(), ignoreCase = true) } }
            .map { it.copy(id = newId(), name = it.name.trim()) }
        return data.copy(favorites = data.favorites + imported.favorites, playlists = playlists, ruleLists = ruleLists)
    }

    private fun String.norm() = trim().lowercase()
}
