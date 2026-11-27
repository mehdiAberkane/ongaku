package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dossier des fichiers .lrc : depuis Android 13, l'accès à la musique ne couvre que les fichiers
 * audio ; un .lrc posé à côté d'un morceau n'est lisible qu'à travers un dossier choisi par
 * l'utilisateur. Facultatif : sans lui, seules les paroles des tags s'affichent.
 */
class LyricsSettings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("ongaku_lyrics", Context.MODE_PRIVATE)

    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_FOLDER, value) }

    private companion object {
        const val KEY_FOLDER = "lrc_folder_uri"
    }
}

object LyricsLoader {
    private const val MAX_LRC_BYTES = 512L * 1024
    private const val STORAGE = "/storage/"

    /**
     * Identifiant, dans le dossier choisi ([treeDocumentId], par exemple « primary:Music »), du
     * fichier .lrc du morceau [relativePath] (« Music/Rock/01 Aube.flac », ou « /storage/1234-ABCD/…»
     * sur une carte SD). null si le morceau n'est pas dans ce dossier.
     */
    fun lrcDocumentId(treeDocumentId: String, relativePath: String): String? {
        val volume = treeDocumentId.substringBefore(':')
        val base = treeDocumentId.substringAfter(':', "").trim('/')
        val path = if (relativePath.startsWith(STORAGE)) {
            val rest = relativePath.removePrefix(STORAGE)
            if (rest.substringBefore('/') != volume) return null
            rest.substringAfter('/')
        } else {
            if (volume != "primary") return null
            relativePath
        }.trim('/')
        if (base.isNotEmpty() && !path.startsWith("$base/")) return null
        val dot = path.lastIndexOf('.')
        val slash = path.lastIndexOf('/')
        val stem = if (dot > slash) path.substring(0, dot) else path
        return "$volume:$stem.lrc"
    }

    /** Paroles du morceau : son .lrc s'il est accessible, sinon celles de ses tags ; null sinon. */
    suspend fun load(context: Context, track: Track): Lyrics? = withContext(Dispatchers.IO) {
        fromLrcFile(context, track) ?: fromTags(context, track)
    }

    private fun fromLrcFile(context: Context, track: Track): Lyrics? = runCatching {
        val tree = LyricsSettings(context).folderUri?.toUri() ?: return null
        val id = lrcDocumentId(DocumentsContract.getTreeDocumentId(tree), track.relativePath) ?: return null
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        context.contentResolver.openInputStream(document)?.use { input ->
            ImportLimits.readLimited(input, MAX_LRC_BYTES)?.let { LrcParser.parse(String(it, Charsets.UTF_8).removePrefix("\uFEFF")) }
        }
    }.getOrNull()

    private fun fromTags(context: Context, track: Track): Lyrics? = runCatching {
        context.contentResolver.openInputStream(track.uri)?.use(TagLyrics::read)?.let(LrcParser::parse)
    }.getOrNull()
}
