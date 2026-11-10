package fr.devsecopsforce.ongaku.data

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import java.io.IOException
import org.json.JSONArray
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
)

class AppDataStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    fun load(): AppData {
        if (!file.baseFile.exists()) return AppData()
        return try {
            parse(String(file.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            // On garde le fichier illisible de côté plutôt que de l'écraser à la prochaine sauvegarde.
            file.baseFile.renameTo(File(file.baseFile.parentFile, "$FILE_NAME.corrupt"))
            AppData()
        }
    }

    @Throws(IOException::class)
    fun save(data: AppData) {
        val output = file.startWrite()
        try {
            output.write(serialize(data).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: IOException) {
            file.failWrite(output)
            throw e
        }
    }

    private fun serialize(data: AppData): String = JSONObject()
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
        .put(
            "trackBackgrounds",
            JSONObject().apply {
                data.trackBackgrounds.forEach { (id, name) -> put(id.toString(), name) }
            },
        )
        .toString()

    private fun parse(text: String): AppData {
        val json = JSONObject(text)
        val backgrounds = json.getJSONObject("trackBackgrounds")
        return AppData(
            favorites = json.getJSONArray("favorites").longs().toSet(),
            playlists = json.getJSONArray("playlists").let { array ->
                List(array.length()) { index ->
                    val playlist = array.getJSONObject(index)
                    Playlist(
                        id = playlist.getString("id"),
                        name = playlist.getString("name"),
                        trackIds = playlist.getJSONArray("tracks").longs(),
                    )
                }
            },
            appBackground = json.stringOrNull("appBackground"),
            playerBackground = json.stringOrNull("playerBackground"),
            trackBackgrounds = backgrounds.keys().asSequence()
                .associate { it.toLong() to backgrounds.getString(it) },
        )
    }

    private fun JSONArray.longs(): List<Long> = List(length()) { getLong(it) }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private companion object {
        const val FILE_NAME = "ongaku.json"
    }
}
