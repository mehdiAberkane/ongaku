package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectionRulesTest {

    @Test
    fun `la sélection garde l'ordre des choix, un second appui retire`() {
        var selected = emptyList<Long>()
        selected = SelectionRules.toggle(selected, 3)
        selected = SelectionRules.toggle(selected, 1)
        selected = SelectionRules.toggle(selected, 2)
        assertEquals(listOf(3L, 1L, 2L), selected)
        assertEquals(listOf(3L, 2L), SelectionRules.toggle(selected, 1))
    }

    @Test
    fun `le cœur ajoute les morceaux qui manquent aux favoris`() {
        assertEquals(setOf(1L, 2L, 3L), SelectionRules.favoritesAfter(setOf(1L), listOf(2L, 3L)))
        assertEquals(setOf(1L, 2L, 9L), SelectionRules.favoritesAfter(setOf(1L, 9L), listOf(1L, 2L)))
    }

    @Test
    fun `si tous sont déjà favoris, le cœur les retire`() {
        assertTrue(SelectionRules.allFavorites(setOf(1L, 2L, 9L), listOf(1L, 2L)))
        assertEquals(setOf(9L), SelectionRules.favoritesAfter(setOf(1L, 2L, 9L), listOf(1L, 2L)))
        assertFalse(SelectionRules.allFavorites(setOf(1L), emptyList()))
    }

    @Test
    fun `les morceaux disparus sont ignorés, l'ordre de la sélection est gardé`() {
        val byId = listOf(track(1), track(2), track(3)).associateBy { it.id }
        assertEquals(listOf(3L, 1L), SelectionRules.tracks(listOf(3L, 99L, 1L), byId).map { it.id })
    }
}
