package fr.devsecopsforce.ongaku.data

/** Règles d'édition des listes, sans Android : testables telles quelles. */
object PlaylistEdits {

    private fun String.nameKey() = trim().lowercase()

    /** Une autre liste que [exceptId] porte déjà ce nom (casse et espaces ignorés). */
    fun isNameTaken(playlists: List<Playlist>, name: String, exceptId: String? = null): Boolean =
        playlists.any { it.id != exceptId && it.name.nameKey() == name.nameKey() }

    /** Ajoute à la fin les morceaux qui n'y sont pas encore, sans doublon. */
    fun append(trackIds: List<Long>, added: List<Long>): List<Long> {
        val present = trackIds.toHashSet()
        return trackIds + added.filter { present.add(it) }
    }

    /** Déplace l'élément en position [from] vers la position [to]. */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Nouvel ordre d'une liste dont seule une partie est affichée (morceaux introuvables ou masqués
     * par le filtre) : les morceaux affichés reprennent leurs places dans l'ordre [visibleOrder], les
     * autres ne bougent pas.
     */
    fun reorderVisible(trackIds: List<Long>, visibleOrder: List<Long>): List<Long> {
        val visible = visibleOrder.toHashSet()
        val next = visibleOrder.iterator()
        return trackIds.map { id -> if (id in visible && next.hasNext()) next.next() else id }
    }
}
