package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmartListsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private val tracks = listOf(
        track(1, title = "Un", dateAddedSeconds = 10),
        track(2, title = "Deux", dateAddedSeconds = 30),
        track(3, title = "Trois", dateAddedSeconds = 20),
        track(4, title = "Quatre", dateAddedSeconds = 40),
    )
    private val counts = mapOf(1L to 5, 2L to 1, 3L to 1)
    private val lastPlayed = mapOf(1L to now - 2 * day, 2L to now - 40 * day, 3L to now - day)

    private fun ids(kind: SmartList, favorites: Set<Long> = emptySet(), size: Int = 50) =
        SmartLists.build(kind, tracks, counts, lastPlayed, favorites, now, size).map { it.id }

    @Test
    fun `ajouts récents, du plus récent au plus ancien`() {
        assertEquals(listOf(4L, 2L, 3L, 1L), ids(SmartList.RECENTLY_ADDED))
    }

    @Test
    fun `les plus écoutés, à égalité le plus récemment écouté d'abord`() {
        assertEquals(listOf(1L, 3L, 2L), ids(SmartList.MOST_PLAYED))
    }

    @Test
    fun `écoutés récemment, par heure de la dernière écoute`() {
        assertEquals(listOf(3L, 1L, 2L), ids(SmartList.RECENTLY_PLAYED))
    }

    @Test
    fun `jamais écoutés`() {
        assertEquals(listOf(4L), ids(SmartList.NEVER_PLAYED))
    }

    @Test
    fun `les moins écoutés, à égalité le plus longtemps oublié d'abord`() {
        assertEquals(listOf(2L, 3L, 1L), ids(SmartList.LEAST_PLAYED))
    }

    @Test
    fun `redécouvertes, favoris pas écoutés depuis un mois`() {
        // 2 : 40 jours ; 4 : jamais écouté depuis le début de l'historique ; 1 et 3 : récents.
        assertEquals(listOf(4L, 2L), ids(SmartList.REDISCOVER, favorites = setOf(1, 2, 3, 4)))
        assertEquals(emptyList<Long>(), ids(SmartList.REDISCOVER, favorites = emptySet()))
    }

    @Test
    fun `la taille limite chaque liste`() {
        assertEquals(listOf(4L, 2L), ids(SmartList.RECENTLY_ADDED, size = 2))
    }

    @Test
    fun `une taille inconnue revient à 50`() {
        assertEquals(100, SmartLists.size(100))
        assertEquals(50, SmartLists.size(37))
    }
}
