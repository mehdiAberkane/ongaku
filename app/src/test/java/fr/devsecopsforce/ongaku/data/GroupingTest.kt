package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupingTest {

    @Test
    fun `un même album réparti dans deux dossiers est fusionné et trié par piste`() {
        val albums = listOf(
            track(1, title = "B", album = "Hybrid", albumId = 10, trackNumber = 2),
            track(2, title = "A", album = "Hybrid", albumId = 20, trackNumber = 1),
        ).albums()

        assertEquals(1, albums.size)
        assertEquals(listOf(2L, 1L), albums.single().tracks.map { it.id })
    }

    @Test
    fun `la fusion ignore la casse et les espaces autour du nom`() {
        val albums = listOf(
            track(1, album = "Hybrid ", albumId = 10),
            track(2, album = "hybrid", albumId = 20),
        ).albums()

        assertEquals(1, albums.size)
    }

    @Test
    fun `deux albums de même nom mais d'artistes différents restent séparés`() {
        val albums = listOf(
            track(1, album = "Best of", artist = "A", albumId = 10),
            track(2, album = "Best of", artist = "B", albumId = 20),
        ).albums()

        assertEquals(2, albums.size)
    }

    @Test
    fun `une compilation reste entière et prend l'artiste le plus présent`() {
        val albums = listOf(
            track(1, album = "Compil", artist = "A", albumId = 10, trackNumber = 1),
            track(2, album = "Compil", artist = "B", albumId = 10, trackNumber = 2),
            track(3, album = "Compil", artist = "B", albumId = 10, trackNumber = 3),
        ).albums()

        assertEquals(1, albums.size)
        assertEquals("B", albums.single().subtitle)
        assertEquals(3, albums.single().tracks.size)
    }

    @Test
    fun `albums triés par nom sans tenir compte de la casse`() {
        val albums = listOf(
            track(1, album = "zeta", albumId = 1),
            track(2, album = "Alpha", albumId = 2),
            track(3, album = "beta", albumId = 3),
        ).albums()

        assertEquals(listOf("Alpha", "beta", "zeta"), albums.map { it.title })
    }

    @Test
    fun `artistes regroupés, comptés et triés`() {
        val artists = listOf(
            track(1, artist = "Zed", album = "Z"),
            track(2, artist = "abba", album = "B", trackNumber = 2),
            track(3, artist = "abba", album = "B", trackNumber = 1),
        ).artists()

        assertEquals(listOf("abba", "Zed"), artists.map { it.title })
        assertEquals("2 titres", artists[0].subtitle)
        assertEquals("1 titre", artists[1].subtitle)
        assertEquals(listOf(3L, 2L), artists[0].tracks.map { it.id })
    }
}
