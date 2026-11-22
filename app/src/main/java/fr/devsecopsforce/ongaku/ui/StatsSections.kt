// Statistiques d'écoute : temps par période, classements, bilan annuel et historique.
@file:OptIn(ExperimentalLayoutApi::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.ListeningHistory
import fr.devsecopsforce.ongaku.data.ListeningStats
import fr.devsecopsforce.ongaku.data.Ranked
import fr.devsecopsforce.ongaku.data.StatsPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RankKind { TRACKS, ARTISTS, ALBUMS }

/** Nombre d'écoutes affichées dans l'historique. */
private const val HISTORY_SHOWN = 30

@Composable
internal fun ListeningSections() {
    val context = LocalContext.current
    val history = remember(context) { ListeningHistory.get(context) }
    val plays by history.plays.collectAsStateWithLifecycle()
    val days by history.days.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    val totals = remember(days, today) { ListeningStats.totals(days, today) }
    StatRow(stringResource(R.string.stats_today), formatLongDuration(totals.today))
    StatRow(stringResource(R.string.stats_this_week), formatLongDuration(totals.week))
    StatRow(stringResource(R.string.stats_this_month), formatLongDuration(totals.month))
    StatRow(stringResource(R.string.stats_this_year), formatLongDuration(totals.year))

    if (plays.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.stats_history_starts))
        return
    }

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.stats_rankings))
    var period by rememberSaveable { mutableStateOf(StatsPeriod.LAST_30_DAYS) }
    var kind by rememberSaveable { mutableStateOf(RankKind.TRACKS) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsPeriod.entries.forEach { option ->
            FilterChip(selected = option == period, onClick = { period = option }, label = { Text(periodLabel(option)) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RankKind.entries.forEach { option ->
            FilterChip(selected = option == kind, onClick = { kind = option }, label = { Text(kindLabel(option)) })
        }
    }
    val ranking = remember(plays, period, kind, today) {
        val inPeriod = ListeningStats.inPeriod(plays, period, today)
        when (kind) {
            RankKind.TRACKS -> ListeningStats.topTracks(inPeriod)
            RankKind.ARTISTS -> ListeningStats.topArtists(inPeriod)
            RankKind.ALBUMS -> ListeningStats.topAlbums(inPeriod)
        }
    }
    if (ranking.isEmpty()) Hint(stringResource(R.string.stats_no_plays_period))
    ranking.forEachIndexed { index, entry -> RankRow(index + 1, entry) }

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.stats_year_review))
    val years = remember(plays, days) { ListeningStats.years(plays, days) }
    var year by rememberSaveable { mutableStateOf(years.firstOrNull() ?: today.year) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        years.forEach { option ->
            FilterChip(selected = option == year, onClick = { year = option }, label = { Text(option.toString()) })
        }
    }
    val review = remember(plays, days, year) { ListeningStats.yearReview(plays, days, year) }
    StatRow(stringResource(R.string.stats_listened_time), formatLongDuration(review.listenedMs))
    StatRow(stringResource(R.string.stats_plays), review.plays.toString())
    StatRow(stringResource(R.string.stats_distinct_tracks), review.distinctTracks.toString())
    review.topTrack?.let { StatRow(stringResource(R.string.stats_top_track), "${it.title} — ${it.subtitle}") }
    review.topArtist?.let { StatRow(stringResource(R.string.stats_top_artist), it.title) }
    review.topAlbum?.let { StatRow(stringResource(R.string.stats_top_album), it.title) }

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.stats_history))
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.FRENCH) }
    plays.asReversed().take(HISTORY_SHOWN).forEach { play ->
        val time = Instant.ofEpochMilli(play.timeMs).atZone(ZoneId.systemDefault()).format(formatter)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(time, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOf(play.title, play.artist).filter { it.isNotBlank() }.joinToString(" — "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RankRow(rank: Int, entry: Ranked) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$rank.", Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            listOf(entry.title, entry.subtitle).filter { it.isNotBlank() }.joinToString(" — "),
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.library_play_count, entry.plays, entry.plays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.LAST_7_DAYS -> R.string.stats_period_7
        StatsPeriod.LAST_30_DAYS -> R.string.stats_period_30
        StatsPeriod.THIS_YEAR -> R.string.stats_this_year
        StatsPeriod.ALL -> R.string.stats_period_all
    },
)

@Composable
private fun kindLabel(kind: RankKind): String = stringResource(
    when (kind) {
        RankKind.TRACKS -> R.string.stats_kind_tracks
        RankKind.ARTISTS -> R.string.stats_artists
        RankKind.ALBUMS -> R.string.stats_albums
    },
)
