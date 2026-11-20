package fr.devsecopsforce.ongaku.data

import java.io.InputStream

/** Plafonds des fichiers venus de l'extérieur (sauvegarde importée, image de fond choisie). */
object ImportLimits {
    /** Une sauvegarde de milliers de listes tient en quelques centaines de kilo-octets. */
    const val MAX_BACKUP_BYTES = 5L * 1024 * 1024

    /**
     * Lit tout le flux s'il ne dépasse pas [maxBytes] ; null sinon, sans lire au-delà de
     * [maxBytes] + 1 octets : un fichier énorme n'est jamais chargé en mémoire.
     */
    fun readLimited(input: InputStream, maxBytes: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * Taille d'une image de fond : réduite pour que son petit côté ne dépasse pas [maxSide] (le
     * grand côté de l'écran), ce qui suffit à couvrir l'écran dans les deux sens. Jamais agrandie.
     */
    fun fitSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val shortest = minOf(width, height)
        if (shortest <= maxSide) return width to height
        val scale = maxSide.toDouble() / shortest
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }

    /** Facteur de sous-échantillonnage du décodage (puissance de 2) qui reste au-dessus de [fitSize]. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}

/** Noms des images de fond auxquelles renvoient les données de l'utilisateur. */
fun AppData.backgroundNames(): Set<String> =
    setOfNotNull(appBackground, playerBackground, widgetBackground) + trackBackgrounds.values

/** Morceaux introuvables (voir [LibraryIdentity]) qui ont encore une image de fond. */
fun AppData.missingTrackBackgrounds(existing: Set<Long>): Map<Long, String> =
    trackBackgrounds.filterKeys { it !in existing }

/**
 * Fichiers d'images de fond que rien ne désigne plus (restes d'une copie interrompue, par exemple).
 * Seuls ceux plus anciens que [minAgeMs] sont rendus : une image en cours d'enregistrement n'est
 * pas encore inscrite dans les données.
 */
fun orphanBackgroundFiles(
    data: AppData,
    files: List<Pair<String, Long>>,
    nowMs: Long,
    minAgeMs: Long,
): List<String> {
    val used = data.backgroundNames()
    return files.filter { (name, modified) -> name !in used && nowMs - modified >= minAgeMs }.map { it.first }
}
