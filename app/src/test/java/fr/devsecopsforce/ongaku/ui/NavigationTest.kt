package fr.devsecopsforce.ongaku.ui

import fr.devsecopsforce.ongaku.data.SmartList
import fr.devsecopsforce.ongaku.data.albums
import fr.devsecopsforce.ongaku.data.artists
import fr.devsecopsforce.ongaku.data.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationTest {

    private fun roundTrip(detail: Detail) = DetailCodec.decode(DetailCodec.encode(detail))

    @Test
    fun `chaque écran se retrouve après une rotation`() {
        val screens = listOf(
            Detail.Search, Detail.Favorites, Detail.Smart(SmartList.RECENTLY_ADDED), Detail.Smart(SmartList.REDISCOVER),
            Detail.Settings, Detail.Stats, Detail.Appearance, Detail.Equalizer,
            Detail.PlaylistDetail("4f2c-liste"),
            Detail.Folder("Music/Anime/OST"),
            Detail.Folder(""),
            Detail.Group("album:hybrid theory|linkin park"),
        )
        screens.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `un album ouvert depuis la recherche y ramène encore`() {
        val detail = Detail.Group("artist:ke\$ha", from = Detail.Search)
        assertEquals(detail, roundTrip(detail))
    }

    @Test
    fun `une clé contenant des séparateurs reste intacte`() {
        val detail = Detail.Group("album:a|b:c / d|e", from = Detail.Group("artist:x|y"))
        assertEquals(detail, roundTrip(detail))
    }

    @Test
    fun `une forme inconnue ramène aux onglets`() {
        assertNull(DetailCodec.decode(emptyList()))
        assertNull(DetailCodec.decode(listOf("inconnu")))
        assertNull(DetailCodec.decode(listOf("playlist")))
    }

    @Test
    fun `le groupe est retrouvé par sa clé, avec les morceaux actuels`() {
        val before = listOf(track(1, album = "X", artist = "A"))
        val key = before.albums().single().key
        val after = before + track(2, album = "X", artist = "A", trackNumber = 2)

        val album = findGroup(key, after.albums(), after.artists())
        assertEquals(listOf(1L, 2L), album?.tracks?.map { it.id })
        val artist = findGroup(after.artists().single().key, after.albums(), after.artists())
        assertEquals("A", artist?.title)
    }

    @Test
    fun `un album disparu n'est plus trouvé`() {
        val tracks = listOf(track(1, album = "X"))
        assertNull(findGroup("album:disparu|a", tracks.albums(), tracks.artists()))
    }

    @Test
    fun `les anciens noms des listes automatiques se relisent`() {
        assertEquals(Detail.Smart(SmartList.RECENTLY_ADDED), DetailCodec.decode(listOf("recent")))
        assertEquals(Detail.Smart(SmartList.MOST_PLAYED), DetailCodec.decode(listOf("top50")))
        assertNull(DetailCodec.decode(listOf("smart", "INCONNUE")))
    }}
