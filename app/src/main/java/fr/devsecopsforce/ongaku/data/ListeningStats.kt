package fr.devsecopsforce.ongaku.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Période des classements. */
enum class StatsPeriod { LAST_7_DAYS, LAST_30_DAYS, THIS_YEAR, ALL }

/** Une ligne de classement : un morceau, un artiste ou un album, et son nombre d'écoutes. */
data class Ranked(val title: String, val subtitle: String, val plays: Int)

/** Bilan d'une année. */
data class YearReview(
    val year: Int,
    val listenedMs: Long,
    val plays: Int,
    val distinctTracks: Int,
    val topTrack: Ranked?,
    val topArtist: Ranked?,
    val topAlbum: Ranked?,
)

/** Calculs des statistiques, sans Android : testables avec des dates fixes. */
object ListeningStats {

    /** Temps écouté aujourd'hui, cette semaine (depuis lundi), ce mois-ci et cette année. */
    data class Totals(val today: Long, val week: Long, val month: Long, val year: Long)

    fun totals(days: Map<LocalDate, Long>, today: LocalDate): Totals {
        fun since(start: LocalDate) = days.filterKeys { it in start..today }.values.sum()
        return Totals(
            today = days[today] ?: 0L,
            week = since(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),
            month = since(today.withDayOfMonth(1)),
            year = since(today.withDayOfYear(1)),
        )
    }

    /** Écoutes de la période, jusqu'à [today] inclus. */
    fun inPeriod(plays: List<Play>, period: StatsPeriod, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Play> {
        val start = when (period) {
            StatsPeriod.LAST_7_DAYS -> today.minusDays(6)
            StatsPeriod.LAST_30_DAYS -> today.minusDays(29)
            StatsPeriod.THIS_YEAR -> today.withDayOfYear(1)
            StatsPeriod.ALL -> return plays
        }
        return plays.filter { ListeningHistory.dayOf(it.timeMs, zone) in start..today }
    }

    /**
     * Les [limit] morceaux les plus écoutés. Un morceau se reconnaît à son titre et à son artiste
     * (sans casse ni accents) : deux copies du même fichier comptent ensemble, un fichier disparu
     * garde ses écoutes. À égalité, le plus récemment écouté d'abord.
     */
    fun topTracks(plays: List<Play>, limit: Int = 10): List<Ranked> =
        rank(plays, limit, key = { it.title.searchKey() to it.artist.searchKey() }) { Ranked(it.title, it.artist, 0) }

    fun topArtists(plays: List<Play>, limit: Int = 10): List<Ranked> =
        rank(plays.filter { it.artist.isNotBlank() }, limit, key = { it.artist.searchKey() }) { Ranked(it.artist, "", 0) }

    fun topAlbums(plays: List<Play>, limit: Int = 10): List<Ranked> =
        rank(plays.filter { it.album.isNotBlank() }, limit, key = { it.album.searchKey() to it.artist.searchKey() }) {
            Ranked(it.album, it.artist, 0)
        }

    private fun <K> rank(plays: List<Play>, limit: Int, key: (Play) -> K, describe: (Play) -> Ranked): List<Ranked> =
        plays.groupBy(key).values
            .map { group -> group.maxBy { it.timeMs }.let { latest -> describe(latest).copy(plays = group.size) to latest.timeMs } }
            .sortedWith(compareByDescending<Pair<Ranked, Long>> { it.first.plays }.thenByDescending { it.second })
            .take(limit)
            .map { it.first }

    /** Années qui ont des écoutes ou du temps écouté, la plus récente d'abord. */
    fun years(plays: List<Play>, days: Map<LocalDate, Long>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
        (plays.map { ListeningHistory.dayOf(it.timeMs, zone).year } + days.keys.map { it.year }).distinct().sortedDescending()

    fun yearReview(plays: List<Play>, days: Map<LocalDate, Long>, year: Int, zone: ZoneId = ZoneId.systemDefault()): YearReview {
        val ofYear = plays.filter { ListeningHistory.dayOf(it.timeMs, zone).year == year }
        return YearReview(
            year = year,
            listenedMs = days.filterKeys { it.year == year }.values.sum(),
            plays = ofYear.size,
            distinctTracks = ofYear.distinctBy { it.title.searchKey() to it.artist.searchKey() }.size,
            topTrack = topTracks(ofYear, 1).firstOrNull(),
            topArtist = topArtists(ofYear, 1).firstOrNull(),
            topAlbum = topAlbums(ofYear, 1).firstOrNull(),
        )
    }
}
