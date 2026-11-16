package fr.devsecopsforce.ongaku.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {

    // Ordre mélangé d'une file de 5 morceaux, auquel Media3 a ajouté 5 et 6 au hasard.
    private val shuffled = listOf(3, 5, 0, 4, 6, 1, 2)

    @Test
    fun `lire ensuite place les ajouts juste après le morceau en cours, dans leur ordre`() {
        assertEquals(listOf(3, 0, 5, 6, 4, 1, 2), QueueOrder.place(shuffled, added = listOf(5, 6), anchor = 0))
    }

    @Test
    fun `ajouter à la file les place à la fin`() {
        assertEquals(listOf(3, 0, 4, 1, 2, 5, 6), QueueOrder.place(shuffled, added = listOf(5, 6), anchor = null))
    }

    @Test
    fun `lire ensuite sur le dernier morceau les place à la fin`() {
        assertEquals(listOf(3, 0, 4, 1, 2, 5, 6), QueueOrder.place(shuffled, added = listOf(5, 6), anchor = 2))
    }

    @Test
    fun `sans morceau en cours connu, les ajouts vont à la fin`() {
        assertEquals(listOf(3, 0, 4, 1, 2, 5, 6), QueueOrder.place(shuffled, added = listOf(5, 6), anchor = 9))
    }

    @Test
    fun `déplacer vers le bas, vers le haut, ou nulle part`() {
        val order = listOf(10, 11, 12, 13)
        assertEquals(listOf(11, 12, 10, 13), QueueOrder.move(order, from = 0, to = 2))
        assertEquals(listOf(13, 10, 11, 12), QueueOrder.move(order, from = 3, to = 0))
        assertEquals(order, QueueOrder.move(order, from = 1, to = 1))
        assertEquals(order, QueueOrder.move(order, from = 1, to = 9))
    }
}
