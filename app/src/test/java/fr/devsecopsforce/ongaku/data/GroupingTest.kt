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
        // Le nombre de titres affiché sous l'artiste vient de la taille de sa liste.
        assertEquals(2, artists[0].tracks.size)
        assertEquals(1, artists[1].tracks.size)
        assertEquals(listOf("", ""), artists.map { it.subtitle })
        assertEquals(listOf(3L, 2L), artists[0].tracks.map { it.id })
    }

    @Test
    fun `l'artiste de l'album réunit une compilation répartie dans deux dossiers`() {
        val albums = listOf(
            track(1, album = "Compil", artist = "A", albumId = 10, albumArtist = "Divers"),
            track(2, album = "Compil", artist = "B", albumId = 20, albumArtist = "Divers"),
        ).albums()

        assertEquals(1, albums.size)
        assertEquals("Divers", albums.single().subtitle)
    }

    @Test
    fun `sans artiste d'album, rien ne change`() {
        val albums = listOf(
            track(1, album = "Best of", artist = "A", albumId = 10),
            track(2, album = "Best of", artist = "B", albumId = 20),
        ).albums()

        assertEquals(listOf("A", "B"), albums.map { it.subtitle }.sorted())
    }

    @Test
    fun `les feat sont rattachés à l'artiste principal si l'option est active`() {
        val tracks = listOf(
            track(1, artist = "Ke\$ha"),
            track(2, artist = "Ke\$ha feat. 3OH!3"),
            track(3, artist = "Ke\$ha (ft. Pitbull)"),
            track(4, artist = "Ke\$ha Featuring Iggy"),
            track(5, artist = "Ke\$ha [feat Lil Wayne]"),
        )

        assertEquals(listOf("Ke\$ha"), tracks.artists(mergeFeaturing = true).map { it.title })
        assertEquals(5, tracks.artists(mergeFeaturing = false).size)
    }

    @Test
    fun `un nom qui contient feat n'est pas coupé`() {
        assertEquals("Feather Lane", primaryArtist("Feather Lane", true))
        assertEquals("Swift Featherweight", primaryArtist("Swift Featherweight", true))
        assertEquals("Daft Punk", primaryArtist("Daft Punk", true))
        assertEquals("A", primaryArtist(" A ft. B", true))
        assertEquals("A feat. B", primaryArtist("A feat. B", false))
    }

    @Test
    fun `la casse de l'artiste ne crée pas deux artistes`() {
        val artists = listOf(track(1, artist = "Abba"), track(2, artist = "ABBA"), track(3, artist = "Abba")).artists()

        assertEquals(listOf("Abba"), artists.map { it.title })
    }

    @Test
    fun `un morceau sans artiste prend celui de l'album`() {
        val artists = listOf(track(1, artist = "Artiste inconnu", artistMissing = true, albumArtist = "Zaz")).artists()

        assertEquals(listOf("Zaz"), artists.map { it.title })
    }

    @Test
    fun `la page d'un artiste est découpée par album, du plus récent au plus ancien`() {
        val artist = listOf(
            track(1, album = "Ancien", year = 2001, trackNumber = 2),
            track(2, album = "Récent", year = 2020, trackNumber = 1),
            track(3, album = "Ancien", year = 2001, trackNumber = 1),
            track(4, album = "Sans date"),
        ).artists().single()

        assertEquals(listOf(2L, 3L, 1L, 4L), artist.tracks.map { it.id })
        val rows = artist.rows()
        assertEquals(
            listOf("Récent", "2", "Ancien", "3", "1", "Sans date", "4"),
            rows.map {
                when (it) {
                    is GroupRow.AlbumHeader -> it.title
                    is GroupRow.Song -> it.track.id.toString()
                    is GroupRow.DiscHeader -> "disque ${it.disc}"
                }
            },
        )
        val ancien = rows[2] as GroupRow.AlbumHeader
        assertEquals(2001, ancien.year)
        assertEquals(2, ancien.trackCount)
        // L'index renvoie à la liste de lecture du groupe.
        rows.filterIsInstance<GroupRow.Song>().forEach { assertEquals(it.track, artist.tracks[it.index]) }
    }

    @Test
    fun `un album à plusieurs disques affiche le numéro de chaque disque`() {
        val album = listOf(
            track(1, trackNumber = 2001),
            track(2, trackNumber = 1002),
            track(3, trackNumber = 1001),
        ).albums().single()

        assertEquals(
            listOf("disque 1", "3", "2", "disque 2", "1"),
            album.rows().map {
                when (it) {
                    is GroupRow.AlbumHeader -> it.title
                    is GroupRow.Song -> it.track.id.toString()
                    is GroupRow.DiscHeader -> "disque ${it.disc}"
                }
            },
        )
    }

    @Test
    fun `un album d'un seul disque n'a pas d'en-tête de disque`() {
        val album = listOf(track(1, trackNumber = 1001), track(2, trackNumber = 1002)).albums().single()

        assertEquals(listOf(1L, 2L), album.rows().map { (it as GroupRow.Song).track.id })
    }

    @Test
    fun `les clés des lignes sont uniques`() {
        val artist = listOf(
            track(1, album = "X", trackNumber = 1001),
            track(2, album = "X", trackNumber = 2001),
            track(3, album = "Y", trackNumber = 1001),
            track(4, album = "Y", trackNumber = 2001),
        ).artists().single()

        val keys = artist.rows().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
