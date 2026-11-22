package fr.devsecopsforce.ongaku.data

/**
 * Arborescence des dossiers du téléphone, reconstruite à partir des morceaux ([Track.folder]) : un
 * dossier sans musique, ni dans ses sous-dossiers, n'apparaît pas. Un chemin est une suite de noms
 * séparés par « / », sans « / » au début ni à la fin ; "" désigne la racine.
 */
object FolderTree {

    /** Un sous-dossier : son chemin complet, son nom, et ses morceaux sous-dossiers compris. */
    data class Entry(val path: String, val name: String, val trackCount: Int)

    /** Ce qu'affiche un dossier : ses sous-dossiers, puis ses propres morceaux. */
    data class View(val path: String, val subfolders: List<Entry>, val tracks: List<Track>)

    fun normalize(folder: String): String = folder.split('/').filter { it.isNotBlank() }.joinToString("/")

    fun name(path: String): String = path.substringAfterLast('/')

    /** Dossier parent ; null pour la racine. */
    fun parent(path: String): String? = if (path.isEmpty()) null else path.substringBeforeLast('/', "")

    fun isUnder(folder: String, path: String): Boolean =
        path.isEmpty() || folder == path || folder.startsWith("$path/")

    fun view(tracks: List<Track>, path: String): View {
        val prefix = if (path.isEmpty()) "" else "$path/"
        val direct = mutableListOf<Track>()
        val counts = HashMap<String, Int>()
        for (track in tracks) {
            val folder = normalize(track.folder)
            when {
                folder == path -> direct += track
                isUnder(folder, path) -> {
                    val child = folder.removePrefix(prefix).substringBefore('/')
                    counts[child] = (counts[child] ?: 0) + 1
                }
            }
        }
        val subfolders = counts.map { (name, count) -> Entry(prefix + name, name, count) }
            .sortedBy { it.name.searchKey() }
        return View(path, subfolders, direct.sortedWith(byFileName))
    }

    /**
     * Morceaux de [path] et de tous ses sous-dossiers, dans l'ordre où le dossier les montre : ses
     * propres fichiers d'abord, puis chaque sous-dossier à son tour, par ordre alphabétique.
     */
    fun tracksUnder(tracks: List<Track>, path: String): List<Track> =
        tracks.filter { isUnder(normalize(it.folder), path) }
            .sortedWith(compareBy<Track, List<String>>(segmentOrder) { normalize(it.folder).segments() }.then(byFileName))

    private fun String.segments(): List<String> = if (isEmpty()) emptyList() else split('/')

    private fun Track.fileName(): String = relativePath.substringAfterLast('/').ifEmpty { title }

    private val byFileName = compareBy<Track>({ it.fileName().searchKey() }, { it.id })

    /** Un dossier passe avant ses sous-dossiers ; entre frères, ordre alphabétique sans casse. */
    private val segmentOrder = Comparator<List<String>> { a, b ->
        for (i in 0 until minOf(a.size, b.size)) {
            val cmp = a[i].searchKey().compareTo(b[i].searchKey())
            if (cmp != 0) return@Comparator cmp
        }
        a.size.compareTo(b.size)
    }
}
