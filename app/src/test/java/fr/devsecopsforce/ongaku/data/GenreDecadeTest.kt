package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenreDecadeTest {

    private fun decadeLabel(decade: Int) = "Années $decade"

    @Test
    fun `plusieurs genres dans un même tag`() {
        assertEquals(listOf("Rock", "Pop"), genreNames("Rock; Pop"))
        assertEquals(listOf("Rock", "Metal"), genreNames("Rock\u0000Metal\u0000"))
        assertEquals(listOf("Rock"), genreNames("Rock;rock; "))
        // « R&B/Soul » est un seul genre.
        assertEquals(listOf("R&B/Soul"), genreNames("R&B/Soul"))
        assertEquals(emptyList<String>(), genreNames(""))
    }

    @Test
    fun `genres triés, sans casse, un morceau dans chacun de ses genres, inconnus à la fin`() {
        val genres = listOf(
            track(1, genre = "Rock"),
            track(2, genre = "rock; Électro"),
            track(3, genre = "Jazz"),
            track(4),
        ).genres("Genre inconnu")

        assertEquals(listOf("Électro", "Jazz", "Rock", "Genre inconnu"), genres.map { it.title })
        assertEquals(listOf(1L, 2L), genres[2].tracks.map { it.id }.sorted())
        assertEquals(listOf(4L), genres.last().tracks.map { it.id })
        assertTrue(genres.all { it.isCollection })
        // Clés distinctes : chaque groupe se rouvre après une rotation.
        assertEquals(genres.size, genres.map { it.key }.toSet().size)
    }

    @Test
    fun `le nom d'un genre est son écriture la plus fréquente`() {
        val genres = listOf(track(1, genre = "Rock"), track(2, genre = "Rock"), track(3, genre = "ROCK")).genres("?")
        assertEquals(listOf("Rock"), genres.map { it.title })
    }

    @Test
    fun `dans un genre, par artiste puis par album puis par piste`() {
        val genre = listOf(
            track(1, artist = "B", album = "X", trackNumber = 1, genre = "Rock"),
            track(2, artist = "A", album = "Z", trackNumber = 1, genre = "Rock"),
            track(3, artist = "A", album = "Y", trackNumber = 2, genre = "Rock"),
            track(4, artist = "A", album = "Y", trackNumber = 1, genre = "Rock"),
        ).genres("?").single()

        assertEquals(listOf(4L, 3L, 2L, 1L), genre.tracks.map { it.id })
    }

    @Test
    fun `décennies dans l'ordre chronologique, sans année à la fin`() {
        val decades = listOf(
            track(1, year = 1994),
            track(2, year = 2003),
            track(3, year = 1990),
            track(4, year = 0),
            track(5, year = 1999),
        ).decades(::decadeLabel, "Année inconnue")

        assertEquals(listOf("Années 1990", "Années 2000", "Année inconnue"), decades.map { it.title })
        // Dans une décennie, par année.
        assertEquals(listOf(3L, 1L, 5L), decades[0].tracks.map { it.id })
    }

    @Test
    fun `une année invraisemblable compte comme absente`() {
        assertEquals(1990, decadeOf(1994))
        assertEquals(2020, decadeOf(2020))
        assertNull(decadeOf(0))
        assertNull(decadeOf(94))
        assertNull(decadeOf(20190))
    }

    @Test
    fun `un genre se lit comme une simple liste, sans en-têtes`() {
        val genre = listOf(
            track(1, album = "X", trackNumber = 1001, genre = "Rock"),
            track(2, album = "X", trackNumber = 2001, genre = "Rock"),
        ).genres("?").single()

        assertTrue(genre.rows().all { it is GroupRow.Song })
    }
}
