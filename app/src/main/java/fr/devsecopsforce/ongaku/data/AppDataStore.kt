package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long> = emptyList(),
)

/** Tout ce que l'utilisateur crée dans Ongaku ; rien de tout cela n'est écrit dans les fichiers audio. */
data class AppData(
    val favorites: Set<Long> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val appBackground: String? = null,
    val playerBackground: String? = null,
    val trackBackgrounds: Map<Long, String> = emptyMap(),
    /** Image du widget : prioritaire sur l'image du morceau, la pochette et l'image par défaut. */
    val widgetBackground: String? = null,
    /** Listes par règle ; champ facultatif, absent des fichiers d'avant. */
    val ruleLists: List<RuleList> = emptyList(),
)

/**
 * Fichier des listes, favoris et images de fond (`ongaku.json`).
 *
 * - Un champ absent prend sa valeur par défaut ; un champ présent mais illisible rend le fichier
 *   « abîmé » : on ne l'interprète pas à moitié, ce qui effacerait des données sans prévenir.
 * - Le fichier porte un numéro de version ; [migrate] fait passer un ancien fichier au format actuel.
 *   Les champs inconnus (écrits par une version plus récente) sont conservés à l'enregistrement.
 * - Avant chaque enregistrement, l'état précédent est gardé dans `ongaku.json.prev`. Un fichier abîmé
 *   est mis de côté sous un nom daté (les [MAX_CORRUPT_COPIES] derniers), puis remplacé par cette
 *   copie si elle est lisible. L'utilisateur est prévenu par [pendingNotice].
 */
class AppDataStore(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, FILE_NAME)
    private val previous = File(dir, "$FILE_NAME.prev")

    /** Champs inconnus du dernier fichier lu, réécrits tels quels. */
    private var extras = JSONObject()

    /** Version du dernier fichier lu, si elle est plus récente que celle de cette application. */
    private var newerVersion: Int? = null

    fun load(): AppData {
        // Copie laissée par une ancienne version d'AtomicFile interrompue en pleine écriture.
        val legacyBackup = File(dir, "$FILE_NAME.bak")
        if (!file.exists() && legacyBackup.exists()) legacyBackup.renameTo(file)
        if (!file.exists()) return AppData()

        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            // Illisible pour l'instant (stockage occupé…) : on ne déplace rien.
            Log.w(TAG, "lecture impossible", e)
            return AppData()
        }
        return try {
            parse(text)
        } catch (e: Exception) {
            Log.w(TAG, "fichier abîmé", e)
            recover()
        }
    }

    /** Met le fichier abîmé de côté et repart de la copie précédente si elle est lisible. */
    private fun recover(): AppData {
        val setAside = setAside()
        val restored = if (previous.exists()) {
            try {
                parse(previous.readText(Charsets.UTF_8)).also { data ->
                    try {
                        SafeFile.write(file, serialize(data).toByteArray(Charsets.UTF_8))
                    } catch (e: IOException) {
                        Log.w(TAG, "copie précédente non réécrite", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "copie précédente abîmée aussi", e)
                null
            }
        } else {
            null
        }
        _pendingNotice.value = Notice(setAside?.name, restoredFromCopy = restored != null)
        return restored ?: AppData().also { extras = JSONObject() }
    }

    /** Renomme le fichier abîmé avec la date, et ne garde que les dernières copies. */
    private fun setAside(): File? {
        val prefix = "$FILE_NAME.corrupt-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        // Numéro toujours au-dessus des copies de la même seconde : un nom libéré par la suppression
        // d'une vieille copie n'est jamais repris, sinon la nouvelle passerait pour la plus ancienne.
        val next = corruptCopies(dir)
            .filter { it.name.startsWith("$prefix-") }
            .maxOfOrNull { it.name.substringAfterLast('-').toIntOrNull() ?: 0 }
            ?.plus(1) ?: 1
        val target = File(dir, "$prefix-%03d".format(Locale.ROOT, next))
        if (!file.renameTo(target)) return null
        corruptCopies(dir).drop(MAX_CORRUPT_COPIES).forEach { it.delete() }
        return target
    }

    @Throws(IOException::class)
    fun save(data: AppData) {
        // L'état actuel devient la copie de secours, s'il est lisible : on ne remplace jamais une
        // bonne copie par un fichier abîmé.
        if (file.exists() && isReadable(file)) SafeFile.write(previous, file.readBytes())
        SafeFile.write(file, serialize(data).toByteArray(Charsets.UTF_8))
    }

    private fun isReadable(source: File): Boolean = try {
        parseJson(source.readText(Charsets.UTF_8))
        true
    } catch (e: Exception) {
        false
    }

    private fun serialize(data: AppData): String {
        val json = JSONObject(extras.toString())
        return json
            .put("version", maxOf(VERSION, newerVersion ?: 0))
            .put("favorites", JSONArray(data.favorites.toList()))
            .put(
                "playlists",
                JSONArray(
                    data.playlists.map { playlist ->
                        JSONObject()
                            .put("id", playlist.id)
                            .put("name", playlist.name)
                            .put("tracks", JSONArray(playlist.trackIds))
                    },
                ),
            )
            .put("appBackground", data.appBackground ?: JSONObject.NULL)
            .put("playerBackground", data.playerBackground ?: JSONObject.NULL)
            .put("widgetBackground", data.widgetBackground ?: JSONObject.NULL)
            .put("ruleLists", JSONArray(data.ruleLists.map(::ruleListJson)))
            .put(
                "trackBackgrounds",
                JSONObject().apply {
                    data.trackBackgrounds.forEach { (id, name) -> put(id.toString(), name) }
                },
            )
            .toString()
    }

    private fun parse(text: String): AppData {
        val (data, unknown, version) = parseJson(text)
        extras = unknown
        newerVersion = version.takeIf { it > VERSION }
        return data
    }

    private data class Parsed(val data: AppData, val extras: JSONObject, val version: Int)

    @Throws(JSONException::class)
    private fun parseJson(text: String): Parsed {
        val raw = JSONObject(text)
        val version = if (raw.has("version")) raw.getInt("version") else 0
        val json = migrate(raw, version)

        val favorites = json.optArray("favorites")?.longs()?.toSet() ?: emptySet()
        val playlists = json.optArray("playlists")?.let { array ->
            List(array.length()) { index ->
                val playlist = array.getJSONObject(index)
                Playlist(
                    id = playlist.optStringStrict("id") ?: UUID.randomUUID().toString(),
                    name = playlist.optStringStrict("name") ?: "Liste sans nom",
                    trackIds = playlist.optArray("tracks")?.longs() ?: emptyList(),
                )
            }
        } ?: emptyList()
        val backgrounds = if (json.has("trackBackgrounds") && !json.isNull("trackBackgrounds")) {
            val objects = json.getJSONObject("trackBackgrounds")
            objects.keys().asSequence().associate { key ->
                (key.toLongOrNull() ?: throw JSONException("identifiant illisible : $key")) to
                    objects.getStringStrict(key)
            }
        } else {
            emptyMap()
        }
        val ruleLists = json.optArray("ruleLists")?.let { array ->
            List(array.length()) { index -> parseRuleList(array.getJSONObject(index)) }
        } ?: emptyList()
        val data = AppData(
            favorites = favorites,
            playlists = playlists,
            appBackground = json.optStringStrict("appBackground"),
            playerBackground = json.optStringStrict("playerBackground"),
            trackBackgrounds = backgrounds,
            widgetBackground = json.optStringStrict("widgetBackground"),
            ruleLists = ruleLists,
        )
        val unknown = JSONObject()
        json.keys().forEach { key -> if (key !in KNOWN_FIELDS) unknown.put(key, json.get(key)) }
        return Parsed(data, unknown, version)
    }

    /** Fait passer un fichier ancien au format actuel. Version 0 : fichiers d'avant la numérotation. */
    private fun migrate(json: JSONObject, from: Int): JSONObject {
        var current = json
        if (from < 1) {
            // 0 → 1 : même contenu, seul le numéro de version apparaît.
            current = json
        }
        // Prochaine évolution du format : ajouter ici « if (from < 2) { … } ».
        return current
    }

    private fun ruleListJson(list: RuleList) = JSONObject()
        .put("id", list.id)
        .put("name", list.name)
        .put("genre", list.genre)
        .put("yearFrom", list.yearFrom)
        .put("yearTo", list.yearTo)
        .put("minSeconds", list.minSeconds)
        .put("maxSeconds", list.maxSeconds)
        .put("folder", list.folder)

    /** Chaque condition est facultative ; présente, elle doit avoir le bon type. */
    private fun parseRuleList(json: JSONObject) = RuleList(
        id = json.optStringStrict("id") ?: UUID.randomUUID().toString(),
        name = json.optStringStrict("name") ?: "Liste sans nom",
        genre = json.optStringStrict("genre").orEmpty(),
        yearFrom = json.optIntStrict("yearFrom"),
        yearTo = json.optIntStrict("yearTo"),
        minSeconds = json.optIntStrict("minSeconds"),
        maxSeconds = json.optIntStrict("maxSeconds"),
        folder = json.optStringStrict("folder").orEmpty(),
    )

    private fun JSONObject.optIntStrict(key: String): Int =
        if (!has(key) || isNull(key)) 0 else get(key) as? Int ?: throw JSONException("$key n'est pas un nombre")

    /** Champ absent ou null : null. Présent mais d'un autre type : fichier abîmé. */
    private fun JSONObject.optArray(key: String): JSONArray? =
        if (!has(key) || isNull(key)) null else getJSONArray(key)

    private fun JSONObject.optStringStrict(key: String): String? =
        if (!has(key) || isNull(key)) null else getStringStrict(key)

    private fun JSONObject.getStringStrict(key: String): String =
        get(key) as? String ?: throw JSONException("$key n'est pas un texte")

    private fun JSONArray.longs(): List<Long> = List(length()) { index ->
        when (val value = get(index)) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw JSONException("identifiant illisible : $value")
        }
    }

    /** Fichier mis de côté ([setAsideName]) et, si c'est le cas, rétabli depuis la copie précédente. */
    data class Notice(val setAsideName: String?, val restoredFromCopy: Boolean)

    companion object {
        private const val TAG = "OngakuData"
        private const val FILE_NAME = "ongaku.json"
        const val VERSION = 1
        const val MAX_CORRUPT_COPIES = 5
        private val KNOWN_FIELDS = setOf(
            "version", "favorites", "playlists", "appBackground", "playerBackground", "trackBackgrounds",
            "widgetBackground", "ruleLists",
        )

        /**
         * Verrou de toute lecture-modification-écriture de ongaku.json : l'interface et la
         * vérification des identifiants ([LibraryIdentity]) peuvent y écrire en même temps.
         */
        val lock = Any()

        private val _pendingNotice = MutableStateFlow<Notice?>(null)

        /** Fichier abîmé mis de côté, pas encore signalé à l'utilisateur ; voir [noticeShown]. */
        val pendingNotice: StateFlow<Notice?> = _pendingNotice.asStateFlow()

        fun noticeShown() {
            _pendingNotice.value = null
        }

        /** Copies abîmées mises de côté, de la plus récente à la plus ancienne. */
        internal fun corruptCopies(dir: File): List<File> =
            dir.listFiles { candidate -> candidate.name.startsWith("$FILE_NAME.corrupt") }
                .orEmpty()
                .sortedByDescending { it.name }
    }
}
