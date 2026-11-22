// Listes automatiques : leurs noms, icônes et messages, et leur écran.
package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.SmartList
import fr.devsecopsforce.ongaku.data.Track

@Composable
internal fun smartTitle(kind: SmartList, size: Int): String = when (kind) {
    SmartList.RECENTLY_ADDED -> stringResource(R.string.main_title_recent)
    SmartList.MOST_PLAYED -> stringResource(R.string.smart_title_most_played, size)
    SmartList.RECENTLY_PLAYED -> stringResource(R.string.smart_title_recently_played)
    SmartList.NEVER_PLAYED -> stringResource(R.string.smart_title_never_played)
    SmartList.LEAST_PLAYED -> stringResource(R.string.smart_title_least_played)
    SmartList.REDISCOVER -> stringResource(R.string.smart_title_rediscover)
}

@Composable
internal fun smartSubtitle(kind: SmartList, size: Int): String = when (kind) {
    SmartList.RECENTLY_ADDED -> stringResource(R.string.playlists_recent_subtitle, size)
    SmartList.MOST_PLAYED -> stringResource(R.string.playlists_top50_subtitle, size)
    SmartList.RECENTLY_PLAYED -> stringResource(R.string.smart_subtitle_recently_played, size)
    SmartList.NEVER_PLAYED -> stringResource(R.string.smart_subtitle_never_played)
    SmartList.LEAST_PLAYED -> stringResource(R.string.smart_subtitle_least_played)
    SmartList.REDISCOVER -> stringResource(R.string.smart_subtitle_rediscover)
}

internal fun smartIcon(kind: SmartList): ImageVector = when (kind) {
    SmartList.RECENTLY_ADDED -> Icons.Rounded.Schedule
    SmartList.MOST_PLAYED -> Icons.Rounded.Star
    SmartList.RECENTLY_PLAYED -> Icons.Rounded.History
    SmartList.NEVER_PLAYED -> Icons.Rounded.HourglassEmpty
    SmartList.LEAST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingDown
    SmartList.REDISCOVER -> Icons.Rounded.Restore
}

@Composable
private fun smartEmptyMessage(kind: SmartList): String = when (kind) {
    SmartList.RECENTLY_ADDED -> ""
    SmartList.MOST_PLAYED, SmartList.LEAST_PLAYED -> stringResource(R.string.library_top50_empty)
    SmartList.RECENTLY_PLAYED -> stringResource(R.string.smart_empty_recently_played)
    SmartList.NEVER_PLAYED -> stringResource(R.string.smart_empty_never_played)
    SmartList.REDISCOVER -> stringResource(R.string.smart_empty_rediscover)
}

/** Une liste automatique ouverte : « Lire », « Lecture aléatoire », puis ses morceaux. */
@Composable
internal fun SmartListScreen(
    kind: SmartList,
    tracks: List<Track>,
    counts: Map<Long, Int>,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (tracks.isNotEmpty()) PlayAllButtons { shuffled -> onPlayAll(tracks, shuffled) }
        val showPlays = kind == SmartList.MOST_PLAYED || kind == SmartList.LEAST_PLAYED
        TrackList(
            tracks = tracks,
            currentId = currentId,
            actions = actions,
            onPlay = onPlay,
            emptyMessage = smartEmptyMessage(kind),
            extraLabel = { track ->
                if (showPlays) {
                    val plays = counts[track.id] ?: 0
                    pluralStringResource(R.plurals.library_play_count, plays, plays)
                } else {
                    null
                }
            },
        )
    }
}
