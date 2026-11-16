package fr.devsecopsforce.ongaku.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Ordre de lecture de la file. Sans lecture aléatoire, c'est l'ordre de la liste ; avec, c'est un
 * ordre mélangé que Media3 garde à part. Les fonctions de ce fichier travaillent sur cet ordre :
 * une liste de positions dans la file (0 = premier morceau ajouté).
 */
object QueueOrder {

    /**
     * Place [added] juste après [anchor] (« Lire ensuite »), ou à la fin si [anchor] est null
     * (« Ajouter à la file »), dans l'ordre où ils ont été ajoutés.
     */
    fun place(order: List<Int>, added: List<Int>, anchor: Int?): List<Int> {
        val addedSet = added.toSet()
        val rest = order.filter { it !in addedSet }
        if (anchor == null || anchor in addedSet) return rest + added
        val at = rest.indexOf(anchor)
        if (at < 0) return rest + added
        return rest.subList(0, at + 1) + added + rest.subList(at + 1, rest.size)
    }

    /** Déplace l'élément en position [from] vers la position [to]. */
    fun move(order: List<Int>, from: Int, to: Int): List<Int> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}

/** Positions de la file dans l'ordre où elles seront jouées. */
fun Player.playOrder(): List<Int> {
    val timeline = currentTimeline
    if (timeline.isEmpty) return emptyList()
    val shuffled = shuffleModeEnabled
    val order = ArrayList<Int>(timeline.windowCount)
    var index = timeline.getFirstWindowIndex(shuffled)
    while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
        order += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    }
    return if (order.size == timeline.windowCount) order else List(timeline.windowCount) { it }
}
