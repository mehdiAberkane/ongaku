package fr.devsecopsforce.ongaku.data

/**
 * Ce qui reconnaît un fichier audio quand Android lui donne un autre identifiant : son chemin et sa
 * taille d'abord, ses tags ensuite (le fichier a pu être déplacé ou renommé).
 */
data class TrackKey(
    val path: String,
    val size: Long,
    val title: String,
    val artist: String,
    val album: String,
)

fun Track.key() = TrackKey(relativePath, sizeBytes, title, artist, album)

/**
 * Résultat d'une vérification de la bibliothèque.
 * - [remap] : ancien identifiant → nouveau, à appliquer partout où un morceau est désigné.
 *   Un identifiant négatif marque un morceau introuvable : il ne peut plus désigner un autre fichier.
 * - [index] : empreinte à enregistrer pour la prochaine vérification.
 * - [recovered] et [missing] : morceaux référencés retrouvés ailleurs, et introuvables.
 */
data class Reconciliation(
    val remap: Map<Long, Long>,
    val index: Map<Long, TrackKey>,
    val recovered: Int,
    val missing: Int,
)

object TrackIdentity {

    private fun String.norm() = searchKey()

    private fun TrackKey.sameTags(other: TrackKey) =
        title.norm() == other.title.norm() && artist.norm() == other.artist.norm() &&
            album.norm() == other.album.norm()

    /** Même fichier : même chemin et même taille, ou mêmes tags (fichier remplacé ou retouché). */
    private fun Track.matches(key: TrackKey): Boolean {
        val mine = key()
        return (mine.path == key.path && mine.size == key.size) || mine.sameTags(key)
    }

    /**
     * Compare la bibliothèque à l'empreinte précédente ([previous], null au tout premier lancement).
     * [referenced] : identifiants utilisés par les listes, favoris, images, compteurs et la file.
     */
    fun reconcile(
        previous: Map<Long, TrackKey>?,
        library: List<Track>,
        referenced: Set<Long>,
    ): Reconciliation {
        val byId = library.associateBy { it.id }
        val remap = HashMap<Long, Long>()
        val index = HashMap<Long, TrackKey>()
        library.forEach { index[it.id] = it.key() }

        if (previous == null) {
            // Premier passage : la bibliothèque fait foi. Un identifiant référencé mais absent n'a pas
            // de clé pour être retrouvé ; on l'écarte quand même pour qu'il ne désigne jamais un autre
            // fichier qui recevrait cet identifiant.
            referenced.filter { it > 0 && it !in byId }.forEach { remap[it] = -it }
            return Reconciliation(remap, index, recovered = 0, missing = remap.size)
        }

        val claimed = HashSet<Long>()
        val unresolved = ArrayList<Pair<Long, TrackKey>>()
        previous.forEach { (id, key) ->
            val track = if (id > 0) byId[id] else null
            if (track != null && track.matches(key)) claimed += id else unresolved += id to key
        }

        if (unresolved.isNotEmpty()) {
            val byPathSize = library.groupBy { it.relativePath to it.sizeBytes }
            val byPath = library.groupBy { it.relativePath }
            val byTags = library.groupBy { Triple(it.title.norm(), it.artist.norm(), it.album.norm()) }
            val byTitleArtist = library.groupBy { it.title.norm() to it.artist.norm() }

            fun firstFree(candidates: List<Track>?) = candidates?.firstOrNull { it.id !in claimed }

            // L'ordre de l'empreinte n'importe pas : on traite les identifiants dans l'ordre pour
            // que le résultat soit le même à chaque fois.
            unresolved.sortedBy { it.first }.forEach { (id, key) ->
                val found = firstFree(byPathSize[key.path to key.size])
                    ?: firstFree(byPath[key.path].takeIf { key.path.isNotEmpty() })
                    ?: firstFree(byTags[Triple(key.title.norm(), key.artist.norm(), key.album.norm())])
                    ?: firstFree(byTitleArtist[key.title.norm() to key.artist.norm()])
                when {
                    found != null -> {
                        claimed += found.id
                        if (found.id != id) remap[id] = found.id
                    }
                    id in referenced -> {
                        // Gardé de côté avec sa clé : il sera retrouvé s'il revient (carte SD remise…).
                        val placeholder = if (id > 0) -id else id
                        if (placeholder != id) remap[id] = placeholder
                        index[placeholder] = key
                    }
                    // Plus référencé nulle part : inutile de s'en souvenir.
                }
            }
        }

        // Identifiants référencés qui n'étaient pas dans l'empreinte et n'existent plus.
        referenced.filter { it > 0 && it !in byId && it !in previous && it !in remap }
            .forEach { remap[it] = -it }

        val finalIds = referenced.map { remap[it] ?: it }
        return Reconciliation(
            remap = remap,
            index = index,
            recovered = referenced.count { it in remap && remap.getValue(it) > 0 },
            missing = finalIds.count { it < 0 },
        )
    }
}
