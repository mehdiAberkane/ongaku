package fr.devsecopsforce.ongaku.ui

import fr.devsecopsforce.ongaku.data.AppData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackActionsTest {

    @Test
    fun `sur l'écran du verrouillage, seul le favori agit`() {
        val toggled = mutableListOf<Long>()
        val actions = TrackActions.favoriteOnly(AppData(favorites = setOf(7)), toggled::add)

        actions.toggleFavorite(3)
        // Les autres actions ne font rien : elles ouvriraient la bibliothèque ou les listes.
        actions.addToPlaylist(3)
        actions.playNext(3)
        actions.addToQueue(3)
        actions.pickBackground(3)
        actions.removeBackground(3)

        assertEquals(listOf(3L), toggled)
        assertEquals(setOf(7L), actions.data.favorites)
        assertNull(actions.removeFromPlaylist)
    }
}
