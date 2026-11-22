package fr.devsecopsforce.ongaku.data

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Les identifiants Android changent d'un téléphone à l'autre : la sauvegarde décrit donc chaque
 * morceau par ses tags, et l'import le retrouve dans la bibliothèque du moment.
 */
object PlaylistBackup {
    private const val FORMAT = "ongaku-backup"
    /** 2 : sauvegarde complète (réglages, écoutes, images), dans une archive ([BackupArchive]). */
    private const val VERSION = 2

    /** Fichiers de préférences emportés : lecture, apparence, filtres, tri, égaliseur. */
    val SETTINGS_FILES = listOf(
        "ongaku_settings",
        "ongaku_appearance",
        "ongaku_library_filter",
        "ongaku_library_sort",
        "ongaku_equalizer",
    )

    /** Ce que la sauvegarde complète ajoute aux listes et à l'historique. */
    data class Extras(
        val playCounts: Map<Long, Int> = emptyMap(),
        val lastPlayed: Map<Long, Long> = emptyMap(),
        /** Préférences par fichier, parmi [SETTINGS_FILES]. */
        val settings: Map<String, Map<String, *>> = emptyMap(),
        /** Chemins des images de fond dans l'archive. */
        val appImage: String? = null,
        val playerImage: String? = null,
        val widgetImage: String? = null,
        val trackImages: Map<Long, String> = emptyMap(),
    )

    data class Imported(
        val favorites: Set<Long>,
        val playlists: List<Pair<String, List<Long>>>,
        val missingTracks: Int,
        /** Listes par règle : sans morceau, elles se recopient telles quelles. */
        val ruleLists: List<RuleList> = emptyList(),
        /** Historique d'écoute ; l'identifiant vaut 0 pour un morceau absent de ce téléphone. */
        val history: List<Play> = emptyList(),
        val listeningDays: Map<LocalDate, Long> = emptyMap(),
        /** Écoutes par morceau de ce téléphone : nombre et heure de la dernière. */
        val playCounts: Map<Long, Pair<Int, Long>> = emptyMap(),
        val settings: Map<String, Map<String, Any>> = emptyMap(),
        val appImage: String? = null,
        val playerImage: String? = null,
        val widgetImage: String? = null,
        /** Image de fond par morceau de ce téléphone : chemin dans l'archive. */
        val trackImages: Map<Long, String> = emptyMap(),
    )

    fun serialize(
        data: AppData,
        tracks: List<Track>,
        history: List<Play> = emptyList(),
        listeningDays: Map<LocalDate, Long> = emptyMap(),
        extras: Extras = Extras(),
    ): String {
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
            .put("history", historyJson(history, listeningDays))
            .put(
                "playCounts",
                JSONArray(
                    (extras.playCounts.keys + extras.lastPlayed.keys).mapNotNull(byId::get).map { track ->
                        toRef(track).put("count", extras.playCounts[track.id] ?: 0).put("last", extras.lastPlayed[track.id] ?: 0L)
                    },
                ),
            )
            .put(
                "settings",
                JSONObject().apply {
                    extras.settings.filterKeys { it in SETTINGS_FILES }.forEach { (name, values) -> put(name, PrefsCodec.encode(values)) }
                },
            )
            .put(
                "images",
                JSONObject()
                    .put("app", extras.appImage ?: JSONObject.NULL)
                    .put("player", extras.playerImage ?: JSONObject.NULL)
                    .put("widget", extras.widgetImage ?: JSONObject.NULL)
                    .put(
                        "tracks",
                        JSONArray(extras.trackImages.mapNotNull { (id, path) -> byId[id]?.let { toRef(it).put("path", path) } }),
                    ),
            )
            .toString(2)
    }

    /**
     * Forme compacte : chaque morceau écouté est décrit une fois (titre, artiste, album), et chaque
     * écoute ne garde que son heure et le numéro de ce morceau. Des années d'écoute tiennent ainsi
     * sous la limite d'import ([ImportLimits.MAX_BACKUP_BYTES]).
     */
    private fun historyJson(history: List<Play>, days: Map<LocalDate, Long>): JSONObject {
        val index = LinkedHashMap<Triple<String, String, String>, Int>()
        val plays = JSONArray()
        history.forEach { play ->
            val number = index.getOrPut(Triple(play.title, play.artist, play.album)) { index.size }
            plays.put(JSONArray().put(play.timeMs).put(number))
        }
        return JSONObject()
            .put("tracks", JSONArray(index.keys.map { JSONArray().put(it.first).put(it.second).put(it.third) }))
            .put("plays", plays)
            .put("days", JSONObject().apply { days.forEach { (day, ms) -> put(day.toString(), ms) } })
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
        // Absent des sauvegardes d'avant l'historique.
        val historyJson = root.optJSONObject("history")
        val history = historyJson?.let { json ->
            val described = json.getJSONArray("tracks").let { array ->
                List(array.length()) { index ->
                    val entry = array.getJSONArray(index)
                    Triple(entry.getString(0), entry.getString(1), entry.getString(2))
                }
            }
            json.getJSONArray("plays").let { array ->
                List(array.length()) { index ->
                    val entry = array.getJSONArray(index)
                    val (title, artist, album) = described[entry.getInt(1)]
                    val track = exact[Triple(title.norm(), artist.norm(), album.norm())] ?: loose[title.norm() to artist.norm()]
                    Play(entry.getLong(0), track?.id ?: 0L, title, artist, album)
                }
            }
        }.orEmpty()
        val days = historyJson?.optJSONObject("days")?.let { json ->
            json.keys().asSequence().mapNotNull { key ->
                runCatching { LocalDate.parse(key) to json.getLong(key) }.getOrNull()
            }.toMap()
        }.orEmpty()
        fun trackOf(ref: JSONObject): Track? {
            val title = ref.getString("title").norm()
            val artist = ref.getString("artist").norm()
            return exact[Triple(title, artist, ref.getString("album").norm())] ?: loose[title to artist]
        }
        // Absents des sauvegardes d'avant la sauvegarde complète.
        val playCounts = root.optJSONArray("playCounts")?.let { array ->
            buildMap {
                for (index in 0 until array.length()) {
                    val entry = array.getJSONObject(index)
                    val track = trackOf(entry) ?: continue
                    put(track.id, entry.optInt("count") to entry.optLong("last"))
                }
            }
        }.orEmpty()
        val settings = root.optJSONObject("settings")?.let { json ->
            SETTINGS_FILES.mapNotNull { name -> json.optJSONObject(name)?.let { name to PrefsCodec.decode(it) } }.toMap()
        }.orEmpty()
        val images = root.optJSONObject("images")
        fun imagePath(key: String) = images?.optString(key)?.takeIf { BackupArchive.isSafeImagePath(it) }
        val trackImages = images?.optJSONArray("tracks")?.let { array ->
            buildMap {
                for (index in 0 until array.length()) {
                    val entry = array.getJSONObject(index)
                    val path = entry.optString("path").takeIf { BackupArchive.isSafeImagePath(it) } ?: continue
                    val track = trackOf(entry) ?: continue
                    put(track.id, path)
                }
            }
        }.orEmpty()
        return Imported(
            favorites, playlists, missing, ruleLists, history, days,
            playCounts, settings, imagePath("app"), imagePath("player"), imagePath("widget"), trackImages,
        )
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

    /**
     * Images de fond de la sauvegarde, déjà copiées ([saved] : chemin dans l'archive → nom du
     * fichier enregistré) : elles ne prennent que les places libres, sans jamais remplacer une image
     * déjà choisie sur ce téléphone.
     */
    fun mergeImages(data: AppData, imported: Imported, saved: Map<String, String>): AppData {
        return mergeImagesInto(data, imported, saved)
    }

    /** Images de la sauvegarde qui trouveront une place libre : seules celles-ci sont copiées. */
    fun neededImages(data: AppData, imported: Imported): Set<String> = buildSet {
        if (data.appBackground == null) imported.appImage?.let(::add)
        if (data.playerBackground == null) imported.playerImage?.let(::add)
        if (data.widgetBackground == null) imported.widgetImage?.let(::add)
        imported.trackImages.forEach { (id, path) -> if (id !in data.trackBackgrounds) add(path) }
    }

    private fun mergeImagesInto(data: AppData, imported: Imported, saved: Map<String, String>): AppData {
        fun pick(current: String?, path: String?) = current ?: path?.let(saved::get)
        val tracks = data.trackBackgrounds.toMutableMap()
        imported.trackImages.forEach { (id, path) -> if (id !in tracks) saved[path]?.let { tracks[id] = it } }
        return data.copy(
            appBackground = pick(data.appBackground, imported.appImage),
            playerBackground = pick(data.playerBackground, imported.playerImage),
            widgetBackground = pick(data.widgetBackground, imported.widgetImage),
            trackBackgrounds = tracks,
        )
    }

    private fun String.norm() = trim().lowercase()
}
