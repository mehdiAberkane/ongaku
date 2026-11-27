package fr.devsecopsforce.ongaku.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.SmartLists
import androidx.compose.material3.FilterChip
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.albums
import fr.devsecopsforce.ongaku.data.artists
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    settings: PlaybackSettings,
    allTracks: List<Track>,
    visibleCount: Int,
    mergeFeaturing: Boolean,
    onMergeFeaturingChange: (Boolean) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onOpenEqualizer: () -> Unit,
    onAutoBackupNow: () -> Unit,
    smartSize: Int,
    onSmartSizeChange: (Int) -> Unit,
) {
    var crossfade by remember { mutableFloatStateOf(settings.crossfadeSeconds.toFloat()) }
    var pauseOnNoisy by remember { mutableStateOf(settings.pauseOnNoisy) }
    var sleepFade by remember { mutableStateOf(settings.sleepFade) }
    var lockScreenPlayer by remember { mutableStateOf(settings.lockScreenPlayer) }
    val context = LocalContext.current
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // L'autorisation se donne dans les paramètres d'Android : on la relit au retour dans Ongaku.
    LifecycleResumeEffect(Unit) {
        overlayAllowed = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    val requestOverlay = {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            ),
        )
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(onExport) }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_playback))
        val seconds = crossfade.roundToInt()
        Text(
            if (seconds == 0) {
                stringResource(R.string.settings_crossfade_off)
            } else {
                stringResource(R.string.settings_crossfade, seconds)
            },
        )
        Slider(
            value = crossfade,
            valueRange = 0f..PlaybackSettings.MAX_CROSSFADE_SECONDS.toFloat(),
            steps = PlaybackSettings.MAX_CROSSFADE_SECONDS - 1,
            onValueChange = {
                crossfade = it
                settings.crossfadeSeconds = it.roundToInt()
            },
        )
        Hint(stringResource(R.string.settings_crossfade_note))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_pause_on_noisy), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = pauseOnNoisy,
                onCheckedChange = {
                    pauseOnNoisy = it
                    settings.pauseOnNoisy = it
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_sleep_fade), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = sleepFade,
                onCheckedChange = {
                    sleepFade = it
                    settings.sleepFade = it
                },
            )
        }
        Hint(stringResource(R.string.settings_sleep_fade_note))

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenEqualizer) { Text(stringResource(R.string.eq_title)) }
        Hint(stringResource(R.string.settings_equalizer_note))

        Spacer(Modifier.height(16.dp))
        LyricsSection()

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_smart_size))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartLists.SIZES.forEach { size ->
                FilterChip(
                    selected = size == smartSize,
                    onClick = { onSmartSizeChange(size) },
                    label = { Text(size.toString()) },
                )
            }
        }
        Hint(stringResource(R.string.settings_smart_size_note))

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_lock_screen_player), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = lockScreenPlayer,
                onCheckedChange = {
                    lockScreenPlayer = it
                    settings.lockScreenPlayer = it
                    if (it && !Settings.canDrawOverlays(context)) requestOverlay()
                },
            )
        }
        if (lockScreenPlayer && !overlayAllowed) {
            Hint(stringResource(R.string.settings_overlay_missing))
            OutlinedButton(onClick = requestOverlay) { Text(stringResource(R.string.common_allow)) }
        }

        Spacer(Modifier.height(16.dp))
        BluetoothAutoplaySection(settings)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.settings_artists))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_merge_featuring), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = mergeFeaturing, onCheckedChange = onMergeFeaturingChange)
        }
        Hint(stringResource(R.string.settings_merge_featuring_note))

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        LibraryFilterSection(allTracks, visibleCount)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.settings_backup))
        Hint(stringResource(R.string.settings_backup_note))
        Hint(stringResource(R.string.settings_backup_drive_note))
        Spacer(Modifier.height(16.dp))
        AutoBackupSection(onAutoBackupNow)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { exporter.launch("ongaku-sauvegarde-${LocalDate.now()}.zip") }) {
                Text(stringResource(R.string.settings_export))
            }
            OutlinedButton(
                onClick = {
                    importer.launch(
                        // Archive complète, ou ancienne sauvegarde JSON.
                        arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain"),
                    )
                },
            ) { Text(stringResource(R.string.settings_import)) }
        }
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.settings_import_note))
    }
}

@Composable
fun StatsScreen(tracks: List<Track>, settings: PlaybackSettings, mergeFeaturing: Boolean) {
    val context = LocalContext.current
    var listenedMs by remember { mutableLongStateOf(settings.listenedMs) }
    LaunchedEffect(Unit) {
        while (true) {
            listenedMs = settings.listenedMs
            delay(1000)
        }
    }
    val albumCount = remember(tracks) { tracks.albums().size }
    val artistCount = remember(tracks, mergeFeaturing) { tracks.artists(mergeFeaturing).size }
    val totalSize = remember(tracks) { tracks.sumOf { it.sizeBytes } }
    val totalDuration = remember(tracks) { tracks.sumOf { it.durationMs } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle(stringResource(R.string.stats_library))
        StatRow(stringResource(R.string.stats_tracks), tracks.size.toString())
        StatRow(stringResource(R.string.stats_albums), albumCount.toString())
        StatRow(stringResource(R.string.stats_artists), artistCount.toString())
        StatRow(stringResource(R.string.stats_total_size), Formatter.formatFileSize(context, totalSize))
        StatRow(stringResource(R.string.stats_total_duration), formatLongDuration(totalDuration))
        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.stats_listening))
        StatRow(stringResource(R.string.stats_listened_time), formatLongDuration(listenedMs))
        ListeningSections()
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value)
    }
}

@Composable
internal fun formatLongDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60 % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> stringResource(R.string.stats_duration_days, days, hours, minutes)
        hours > 0 -> stringResource(R.string.stats_duration_hours, hours, minutes)
        else -> stringResource(R.string.stats_duration_minutes, minutes)
    }
}
