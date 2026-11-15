package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LibraryOrderTest {

    @Test
    fun `la forme de comparaison ignore accents, majuscules et espaces autour`() {
        assertEquals("ete a l'ecole", "  Été à l'École ".searchKey())
        assertEquals("francoise", "FRANÇOISE".searchKey())
    }

    @Test
    fun `lettre d'index, avec « # » pour ce qui n'est pas une lettre latine`() {
        assertEquals('E', "Été".indexLetter())
        assertEquals('Z', "zèbre".indexLetter())
        assertEquals('#', "\"Positive Vibe\"".indexLetter())
        assertEquals('#', "2 Unlimited".indexLetter())
        assertEquals('#', "夜に駆ける".indexLetter())
        assertEquals('#', "".indexLetter())
    }

    private val library = listOf(
        track(1, title = "Été", artist = "Zazie", album = "Cyclo", trackNumber = 2, durationMs = 200_000, dateAddedSeconds = 30),
        track(2, title = "avril", artist = "Bénabar", album = "Bouteille", durationMs = 300_000, dateAddedSeconds = 10),
        track(3, title = "Zoo", artist = "Abba", album = "Cyclo", trackNumber = 1, durationMs = 100_000, dateAddedSeconds = 20),
    )

    private fun List<Track>.ids() = map { it.id }

    @Test
    fun `tri des titres sans tenir compte des accents ni de la casse`() {
        assertEquals(listOf(2L, 1L, 3L), library.sorted(TrackSort.TITLE, emptyMap()).ids())
    }

    @Test
    fun `tri par artiste, puis par album en suivant les numéros de piste`() {
        assertEquals(listOf(3L, 2L, 1L), library.sorted(TrackSort.ARTIST, emptyMap()).ids())
        assertEquals(listOf(2L, 3L, 1L), library.sorted(TrackSort.ALBUM, emptyMap()).ids())
    }

    @Test
    fun `tri par date d'ajout, durée et écoutes, du plus grand au plus petit`() {
        assertEquals(listOf(1L, 3L, 2L), library.sorted(TrackSort.DATE_ADDED, emptyMap()).ids())
        assertEquals(listOf(2L, 1L, 3L), library.sorted(TrackSort.DURATION, emptyMap()).ids())
        // Sans écoute, l'ordre des titres départage.
        assertEquals(listOf(3L, 2L, 1L), library.sorted(TrackSort.PLAYS, mapOf(3L to 5, 2L to 1)).ids())
    }

    @Test
    fun `tri des albums par nom, artiste et année, sans année à la fin`() {
        val albums = listOf(
            track(1, album = "Bleu", artist = "Zed", albumId = 1, year = 2001),
            track(2, album = "alpha", artist = "Moi", albumId = 2),
            track(3, album = "Cyan", artist = "Abba", albumId = 3, year = 2019),
        ).albums()

        assertEquals(listOf("alpha", "Bleu", "Cyan"), albums.sorted(AlbumSort.NAME).map { it.title })
        assertEquals(listOf("Cyan", "alpha", "Bleu"), albums.sorted(AlbumSort.ARTIST).map { it.title })
        assertEquals(listOf("Cyan", "Bleu", "alpha"), albums.sorted(AlbumSort.YEAR).map { it.title })
    }

    @Test
    fun `l'année d'un album est la plus récente de ses morceaux`() {
        val album = listOf(
            track(1, album = "Live", year = 1998),
            track(2, album = "Live", year = 2003),
            track(3, album = "Live"),
        ).albums().single()

        assertEquals(2003, album.year)
    }

    @Test
    fun `chaque lettre pointe sur sa première entrée, « # » en tête`() {
        val titles = listOf("\"Intro\"", "Abc", "Alpha", "Été", "Zoo", "夜")

        val letters = titles.letterPositions { it }

        assertEquals(listOf('#' to 0, 'A' to 1, 'E' to 3, 'Z' to 4), letters)
    }

    @Test
    fun `la recherche trouve chaque mot, dans n'importe quel ordre, sans accents`() {
        val tracks = listOf(
            track(1, title = "Été indien", artist = "Joe Dassin", album = "Le Jardin"),
            track(2, title = "Indien", artist = "Autre", album = "Été"),
            track(3, title = "Hiver", artist = "Joe Dassin", album = "Le Jardin"),
        )
        val results = search("dassin ETE", tracks, tracks.albums(), tracks.artists())

        assertEquals(listOf(1L), results.tracks.ids())
        assertTrue(results.albums.isEmpty())
        assertTrue(results.artists.isEmpty())
    }

    @Test
    fun `la recherche groupe titres, albums et artistes`() {
        val tracks = listOf(
            track(1, title = "Jardin d'hiver", artist = "Salvador", album = "Best of", albumId = 1),
            track(2, title = "Autre", artist = "Joe Dassin", album = "Le Jardin", albumId = 2),
        )
        val results = search("jardin", tracks, tracks.albums(), tracks.artists())

        assertEquals(listOf(1L, 2L), results.tracks.ids())
        assertEquals(listOf("Le Jardin"), results.albums.map { it.title })
        assertTrue(results.artists.isEmpty())
        assertEquals(listOf("Joe Dassin"), search("joe", tracks, tracks.albums(), tracks.artists()).artists.map { it.title })
    }

    @Test
    fun `une recherche vide ne renvoie rien`() {
        assertTrue(search("   ", library, library.albums(), library.artists()).isEmpty)
    }

    @Test
    fun `le tri choisi est retrouvé au prochain lancement`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(TrackSort.TITLE, LibrarySortStore(context).trackSort)
        assertEquals(AlbumSort.NAME, LibrarySortStore(context).albumSort)

        LibrarySortStore(context).trackSort = TrackSort.PLAYS
        LibrarySortStore(context).albumSort = AlbumSort.YEAR

        assertEquals(TrackSort.PLAYS, LibrarySortStore(context).trackSort)
        assertEquals(AlbumSort.YEAR, LibrarySortStore(context).albumSort)
    }
}
