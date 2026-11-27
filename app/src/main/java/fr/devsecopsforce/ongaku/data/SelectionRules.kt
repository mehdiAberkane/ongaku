package fr.devsecopsforce.ongaku.data

/**
 * Sélection de plusieurs morceaux (appui long). La sélection garde l'ordre dans lequel les morceaux
 * ont été choisis : c'est l'ordre de lecture et d'ajout à une liste.
 */
object SelectionRules {
    /** Ajoute le morceau à la fin de la sélection, ou l'en retire s'il y était. */
    fun toggle(selected: List<Long>, id: Long): List<Long> =
        if (id in selected) selected - id else selected + id

    /**
     * Le cœur de la barre de sélection : si tous les morceaux choisis sont déjà des favoris, il les
     * retire ; sinon, il ajoute ceux qui manquent.
     */
    fun allFavorites(favorites: Set<Long>, selected: List<Long>): Boolean =
        selected.isNotEmpty() && selected.all { it in favorites }

    fun favoritesAfter(favorites: Set<Long>, selected: List<Long>): Set<Long> =
        if (allFavorites(favorites, selected)) favorites - selected.toSet() else favorites + selected

    /** Morceaux encore présents, dans l'ordre de la sélection. */
    fun tracks(selected: List<Long>, byId: Map<Long, Track>): List<Track> = selected.mapNotNull(byId::get)
}
