package fr.devsecopsforce.ongaku.data

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * Sauvegarde complète : une archive ZIP qui contient la description de tout ([JSON_NAME], format de
 * [PlaylistBackup]) et les images de fond ([IMAGES_DIR]). Une ancienne sauvegarde, simple fichier
 * JSON, se lit aussi.
 */
object BackupArchive {
    const val JSON_NAME = "ongaku.json"
    const val IMAGES_DIR = "images/"

    /** Plafonds de lecture : une archive trafiquée ne peut pas saturer la mémoire du téléphone. */
    const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    const val MAX_ENTRIES = 5_000

    /** Contenu lu : le texte de la sauvegarde et les images, par chemin dans l'archive. */
    class Contents(val json: String, val images: Map<String, ByteArray>)

    /** Ce qu'il faut savoir d'une archive refusée, pour le dire à l'utilisateur. */
    class TooLargeException(message: String) : IOException(message)

    /** [images] : chemin dans l'archive → fichier, copié en flux (jamais chargé en entier). */
    fun write(output: OutputStream, json: String, images: Map<String, File>) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            images.forEach { (path, file) ->
                require(isSafeImagePath(path)) { "chemin d'image inattendu : $path" }
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Lit une archive ou un ancien fichier JSON. Les entrées inattendues sont ignorées ; un chemin
     * qui tenterait de sortir du dossier des images aussi.
     */
    fun read(input: InputStream): Contents {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val magic = ByteArray(4)
        val count = buffered.read(magic)
        buffered.reset()
        val isZip = count == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() &&
            magic[2] == 3.toByte() && magic[3] == 4.toByte()
        if (!isZip) {
            val bytes = ImportLimits.readLimited(buffered, ImportLimits.MAX_BACKUP_BYTES)
                ?: throw TooLargeException("sauvegarde JSON trop lourde")
            return Contents(String(bytes, Charsets.UTF_8), emptyMap())
        }
        var json: String? = null
        val images = HashMap<String, ByteArray>()
        var total = 0L
        var entries = 0
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw TooLargeException("trop d'éléments dans l'archive")
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == JSON_NAME -> {
                        val bytes = ImportLimits.readLimited(zip, ImportLimits.MAX_BACKUP_BYTES)
                            ?: throw TooLargeException("description trop lourde")
                        total += bytes.size
                        json = String(bytes, Charsets.UTF_8)
                    }
                    isSafeImagePath(name) -> {
                        val bytes = ImportLimits.readLimited(zip, MAX_IMAGE_BYTES)
                            ?: throw TooLargeException("image trop lourde : $name")
                        total += bytes.size
                        images[name] = bytes
                    }
                }
                if (total > MAX_TOTAL_BYTES) throw TooLargeException("archive trop lourde")
            }
        }
        return Contents(json ?: throw IOException("archive sans $JSON_NAME"), images)
    }

    /** Un nom simple sous [IMAGES_DIR] : ni sous-dossier, ni « .. », ni chemin absolu. */
    fun isSafeImagePath(name: String): Boolean {
        if (!name.startsWith(IMAGES_DIR)) return false
        val file = name.removePrefix(IMAGES_DIR)
        return file.isNotEmpty() && file.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } && file != "." && file != ".."
    }
}

/**
 * Préférences sauvegardées telles quelles, avec leur type : {"clé": ["b", true]}. Seuls les types
 * des préférences Android sont admis ; une valeur d'un autre type est ignorée à la relecture.
 */
object PrefsCodec {
    fun encode(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) ->
            val encoded = when (value) {
                is Boolean -> org.json.JSONArray().put("b").put(value)
                is Int -> org.json.JSONArray().put("i").put(value)
                is Long -> org.json.JSONArray().put("l").put(value)
                is Float -> org.json.JSONArray().put("f").put(value.toDouble())
                is String -> org.json.JSONArray().put("s").put(value)
                is Set<*> -> org.json.JSONArray().put("set").put(org.json.JSONArray(value.filterIsInstance<String>()))
                else -> null
            }
            if (encoded != null) put(key, encoded)
        }
    }

    fun decode(json: JSONObject): Map<String, Any> = buildMap {
        json.keys().forEach { key ->
            val entry = json.optJSONArray(key) ?: return@forEach
            val value: Any? = when (entry.optString(0)) {
                "b" -> entry.opt(1) as? Boolean
                "i" -> (entry.opt(1) as? Number)?.toInt()
                "l" -> (entry.opt(1) as? Number)?.toLong()
                "f" -> (entry.opt(1) as? Number)?.toFloat()
                "s" -> entry.opt(1) as? String
                "set" -> entry.optJSONArray(1)?.let { array -> List(array.length()) { array.optString(it) }.toSet() }
                else -> null
            }
            if (value != null) put(key, value)
        }
    }
}
