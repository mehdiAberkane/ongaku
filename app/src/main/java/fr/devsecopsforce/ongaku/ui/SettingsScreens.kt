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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
) {
    var crossfade by remember { mutableFloatStateOf(settings.crossfadeSeconds.toFloat()) }
    var pauseOnNoisy by remember { mutableStateOf(settings.pauseOnNoisy) }
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
        ActivityResultContracts.CreateDocument("application/json"),
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
        SectionTitle("Lecture")
        val seconds = crossfade.roundToInt()
        Text(if (seconds == 0) "Fondu enchaîné : désactivé" else "Fondu enchaîné : $seconds s")
        Slider(
            value = crossfade,
            valueRange = 0f..PlaybackSettings.MAX_CROSSFADE_SECONDS.toFloat(),
            steps = PlaybackSettings.MAX_CROSSFADE_SECONDS - 1,
            onValueChange = {
                crossfade = it
                settings.crossfadeSeconds = it.roundToInt()
            },
        )
        Hint("S'applique quand un morceau se termine, pas quand tu passes au suivant toi-même.")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pause quand les écouteurs sont débranchés", Modifier.weight(1f))
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
            Text("Écran de lecture Ongaku sur le verrouillage", Modifier.weight(1f))
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
            Hint("Il manque l'autorisation Android « Superposition ». Elle ne donne aucun accès à tes fichiers.")
            OutlinedButton(onClick = requestOverlay) { Text("Autoriser") }
        }

        Spacer(Modifier.height(16.dp))
        BluetoothAutoplaySection(settings)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        LibraryFilterSection(allTracks, visibleCount)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        SectionTitle("Sauvegarde des listes")
        Hint("Pour Google Drive, choisis Drive dans la fenêtre qui s'ouvre.")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { exporter.launch("ongaku-sauvegarde-${LocalDate.now()}.json") }) {
                Text("Exporter")
            }
            OutlinedButton(
                onClick = {
                    importer.launch(
                        arrayOf("application/json", "application/octet-stream", "text/plain"),
                    )
                },
            ) { Text("Importer") }
        }
        Spacer(Modifier.height(8.dp))
        Hint("L'import ajoute à l'existant : il ne supprime aucune liste ni aucun favori.")
    }
}

@Composable
fun StatsScreen(tracks: List<Track>, settings: PlaybackSettings) {
    val context = LocalContext.current
    var listenedMs by remember { mutableLongStateOf(settings.listenedMs) }
    LaunchedEffect(Unit) {
        while (true) {
            listenedMs = settings.listenedMs
            delay(1000)
        }
    }
    val albumCount = remember(tracks) { tracks.albums().size }
    val artistCount = remember(tracks) { tracks.artists().size }
    val totalSize = remember(tracks) { tracks.sumOf { it.sizeBytes } }
    val totalDuration = remember(tracks) { tracks.sumOf { it.durationMs } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle("Bibliothèque")
        StatRow("Morceaux", tracks.size.toString())
        StatRow("Albums", albumCount.toString())
        StatRow("Artistes", artistCount.toString())
        StatRow("Taille totale", Formatter.formatFileSize(context, totalSize))
        StatRow("Durée totale", formatLongDuration(totalDuration))
        Spacer(Modifier.height(24.dp))
        SectionTitle("Écoute")
        StatRow("Temps écouté dans Ongaku", formatLongDuration(listenedMs))
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
private fun StatRow(label: String, value: String) {
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

private fun formatLongDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60 % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "$days j $hours h $minutes min"
        hours > 0 -> "$hours h $minutes min"
        else -> "$minutes min"
    }
}
