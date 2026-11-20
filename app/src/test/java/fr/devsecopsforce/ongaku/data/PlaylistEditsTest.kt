package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEditsTest {

    private val playlists = listOf(Playlist("1", "Route"), Playlist("2", "Sport"))

    @Test
    fun `un nom déjà pris est refusé sans tenir compte de la casse ni des espaces`() {
        assertTrue(PlaylistEdits.isNameTaken(playlists, " route "))
        assertTrue(PlaylistEdits.isNameTaken(playlists, "SPORT"))
        assertFalse(PlaylistEdits.isNameTaken(playlists, "Soirée"))
    }

    @Test
    fun `renommer une liste sous son propre nom est permis`() {
        assertFalse(PlaylistEdits.isNameTaken(playlists, "ROUTE", exceptId = "1"))
        assertTrue(PlaylistEdits.isNameTaken(playlists, "Sport", exceptId = "1"))
    }

    @Test
    fun `l'ajout groupé garde l'ordre et ignore les morceaux déjà présents`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), PlaylistEdits.append(listOf(1L, 2L), listOf(2L, 3L, 3L, 1L, 4L)))
    }

    @Test
    fun `déplacer un morceau vers le bas ou vers le haut`() {
        assertEquals(listOf(2, 3, 1, 4), PlaylistEdits.move(listOf(1, 2, 3, 4), 0, 2))
        assertEquals(listOf(4, 1, 2, 3), PlaylistEdits.move(listOf(1, 2, 3, 4), 3, 0))
        assertEquals(listOf(1, 2), PlaylistEdits.move(listOf(1, 2), 0, 5))
    }

    @Test
    fun `les morceaux masqués gardent leur place quand on réordonne les autres`() {
        // 9 est introuvable : seuls 1, 2 et 3 sont affichés, et 3 passe en tête.
        val all = listOf(1L, 9L, 2L, 3L)

        assertEquals(listOf(3L, 9L, 1L, 2L), PlaylistEdits.reorderVisible(all, listOf(3L, 1L, 2L)))
    }
}
