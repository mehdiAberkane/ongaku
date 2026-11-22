package fr.devsecopsforce.ongaku.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ListeningStatsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 22) // un dimanche

    private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun play(date: LocalDate, title: String, artist: String = "A", album: String = "X", id: Long = 1) =
        Play(at(date), id, title, artist, album)

    @Before
    fun setUp() = ListeningHistory.resetForTests()

    @Test
    fun `temps du jour, de la semaine depuis lundi, du mois et de l'année`() {
        val days = mapOf(
            today to 10L,
            today.minusDays(6) to 20L, // lundi de la même semaine
            today.minusDays(7) to 40L, // dimanche précédent
            LocalDate.of(2026, 11, 1) to 80L,
            LocalDate.of(2026, 1, 1) to 160L,
            LocalDate.of(2025, 12, 31) to 320L,
        )
        val totals = ListeningStats.totals(days, today)
        assertEquals(10L, totals.today)
        assertEquals(30L, totals.week)
        assertEquals(150L, totals.month)
        assertEquals(310L, totals.year)
    }

    @Test
    fun `périodes des classements`() {
        val plays = listOf(
            play(today, "a"),
            play(today.minusDays(6), "b"),
            play(today.minusDays(7), "c"),
            play(today.minusDays(29), "d"),
            play(LocalDate.of(2025, 6, 1), "e"),
        )
        fun titles(period: StatsPeriod) = ListeningStats.inPeriod(plays, period, today, zone).map { it.title }
        assertEquals(listOf("a", "b"), titles(StatsPeriod.LAST_7_DAYS))
        assertEquals(listOf("a", "b", "c", "d"), titles(StatsPeriod.LAST_30_DAYS))
        assertEquals(listOf("a", "b", "c", "d"), titles(StatsPeriod.THIS_YEAR))
        assertEquals(5, titles(StatsPeriod.ALL).size)
    }

    @Test
    fun `classement des morceaux, même titre et même artiste comptés ensemble`() {
        val plays = listOf(
            play(today.minusDays(2), "Song", id = 1),
            play(today.minusDays(1), "song", id = 2),
            play(today, "Other"),
            play(today.minusDays(3), "Song", artist = "B"),
        )
        val top = ListeningStats.topTracks(plays)
        assertEquals(Ranked("song", "A", 2), top[0])
        // À égalité, le plus récemment écouté d'abord.
        assertEquals(listOf("Other", "Song"), top.drop(1).map { it.title })
    }

    @Test
    fun `classements des artistes et des albums, tags vides ignorés`() {
        val plays = listOf(
            play(today, "1", artist = "A", album = "X"),
            play(today, "2", artist = "A", album = "Y"),
            play(today, "3", artist = "B", album = "X"),
            play(today, "4", artist = "", album = ""),
        )
        assertEquals(listOf(Ranked("A", "", 2), Ranked("B", "", 1)), ListeningStats.topArtists(plays))
        assertEquals(3, ListeningStats.topAlbums(plays).size) // X de A, Y de A, X de B
        assertEquals(1, ListeningStats.topTracks(plays, limit = 1).size)
    }

    @Test
    fun `bilan d'une année`() {
        val plays = listOf(
            play(LocalDate.of(2026, 3, 1), "Song", artist = "A", album = "X"),
            play(LocalDate.of(2026, 4, 1), "Song", artist = "A", album = "X"),
            play(LocalDate.of(2026, 5, 1), "Other", artist = "B", album = "Z"),
            play(LocalDate.of(2025, 5, 1), "Old", artist = "C", album = "W"),
        )
        val days = mapOf(LocalDate.of(2026, 3, 1) to 1_000L, LocalDate.of(2025, 5, 1) to 5_000L)

        assertEquals(listOf(2026, 2025), ListeningStats.years(plays, days, zone))
        val review = ListeningStats.yearReview(plays, days, 2026, zone)
        assertEquals(1_000L, review.listenedMs)
        assertEquals(3, review.plays)
        assertEquals(2, review.distinctTracks)
        assertEquals("Song", review.topTrack?.title)
        assertEquals("A", review.topArtist?.title)
        assertEquals("X", review.topAlbum?.title)
        assertNull(ListeningStats.yearReview(plays, days, 2020, zone).topTrack)
    }

    @Test
    fun `l'historique est enregistré, relu, recalé et fusionné sans doublon`() {
        val context = RuntimeEnvironment.getApplication()
        val history = ListeningHistory.get(context)
        history.record(Play(1_000, 5, "Song", "A", "X"))
        history.record(Play(2_000, 6, "Other", "B", "Y"))
        history.addListening(30_000, nowMs = at(today))
        history.addListening(15_000, nowMs = at(today, 13))

        ListeningHistory.resetForTests()
        val reread = ListeningHistory.get(context)
        assertEquals(listOf(5L, 6L), reread.plays.value.map { it.trackId })
        assertEquals(45_000L, reread.days.value[ListeningHistory.dayOf(at(today))])
        assertEquals(setOf(5L, 6L), reread.referencedIds())

        reread.remap(mapOf(5L to 50L))
        assertEquals(listOf(50L, 6L), reread.plays.value.map { it.trackId })

        // La même écoute revient d'une sauvegarde : pas de doublon ; un jour connu garde le plus grand temps.
        reread.merge(
            listOf(Play(1_000, 0, "song", "A", "X"), Play(3_000, 0, "New", "C", "Z")),
            mapOf(ListeningHistory.dayOf(at(today)) to 10_000L, today.minusDays(1) to 7_000L),
        )
        assertEquals(listOf("Song", "Other", "New"), reread.plays.value.map { it.title })
        assertEquals(45_000L, reread.days.value[ListeningHistory.dayOf(at(today))])
        assertEquals(7_000L, reread.days.value[today.minusDays(1)])
    }

    @Test
    fun `la sauvegarde emporte l'historique sous forme compacte, et le relit sur un autre téléphone`() {
        val library = listOf(track(9, title = "Song", artist = "A", album = "X"))
        val plays = listOf(Play(1_000, 5, "Song", "A", "X"), Play(2_000, 5, "Song", "A", "X"), Play(3_000, 6, "Gone", "B", "Y"))
        val days = mapOf(today to 60_000L)
        val text = PlaylistBackup.serialize(AppData(), library, plays, days)
        // Un seul descriptif par morceau.
        assertEquals(2, org.json.JSONObject(text).getJSONObject("history").getJSONArray("tracks").length())

        val imported = PlaylistBackup.parse(text, library)
        // Retrouvé par ses tags : nouvel identifiant ; absent : 0, mais les tags restent.
        assertEquals(listOf(9L, 9L, 0L), imported.history.map { it.trackId })
        assertEquals(listOf("Song", "Song", "Gone"), imported.history.map { it.title })
        assertEquals(days, imported.listeningDays)
    }}
