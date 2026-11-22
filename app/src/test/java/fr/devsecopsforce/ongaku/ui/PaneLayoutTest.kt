package fr.devsecopsforce.ongaku.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneLayoutTest {

    @Test
    fun `un téléphone en portrait garde une seule colonne`() {
        assertFalse(PaneLayout.twoPane(widthDp = 411, heightDp = 914))
    }

    @Test
    fun `un téléphone couché passe à deux colonnes`() {
        assertTrue(PaneLayout.twoPane(widthDp = 914, heightDp = 411))
    }

    @Test
    fun `un petit écran couché reste sur une colonne`() {
        assertFalse(PaneLayout.twoPane(widthDp = 560, heightDp = 320))
    }

    @Test
    fun `une tablette a deux colonnes dans les deux sens, sauf en portrait étroit`() {
        assertTrue(PaneLayout.twoPane(widthDp = 1280, heightDp = 800))
        assertTrue(PaneLayout.twoPane(widthDp = 900, heightDp = 1200))
        assertFalse(PaneLayout.twoPane(widthDp = 800, heightDp = 1280))
    }

    @Test
    fun `la colonne du lecteur reste dans des bornes lisibles`() {
        assertEquals(383, PaneLayout.playerWidthDp(914))
        assertEquals(PaneLayout.PLAYER_MIN_DP, PaneLayout.playerWidthDp(600))
        assertEquals(PaneLayout.PLAYER_MAX_DP, PaneLayout.playerWidthDp(2000))
    }
}
