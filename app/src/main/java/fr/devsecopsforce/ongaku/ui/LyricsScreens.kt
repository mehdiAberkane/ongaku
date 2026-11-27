// Paroles : panneau de l'écran de lecture et choix du dossier des fichiers .lrc.
package fr.devsecopsforce.ongaku.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.LibraryCache
import fr.devsecopsforce.ongaku.data.Lyrics
import fr.devsecopsforce.ongaku.data.LyricsLoader
import fr.devsecopsforce.ongaku.data.LyricsSettings

/** Paroles en cours de chargement, trouvées, ou absentes. */
private sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

/**
 * Paroles du morceau en cours, à la place de la pochette. Synchronisées : la ligne en cours est mise
 * en avant et suivie ; toucher une ligne y fait sauter la lecture.
 */
@Composable
internal fun LyricsPanel(trackId: Long?, viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var state by remember(trackId) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    LaunchedEffect(trackId) {
        val track = trackId?.let { id -> runCatching { LibraryCache.tracks(context) }.getOrNull()?.firstOrNull { it.id == id } }
        val lyrics = track?.let { LyricsLoader.load(context, it) }
        state = if (lyrics != null) LyricsState.Found(lyrics) else LyricsState.None
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            LyricsState.Loading -> CircularProgressIndicator()
            LyricsState.None -> Text(
                stringResource(R.string.lyrics_none),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            is LyricsState.Found -> LyricsLines(current.lyrics, viewModel)
        }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, viewModel: PlayerViewModel) {
    val listState = rememberLazyListState()
    // La position n'est suivie que pour des paroles synchronisées.
    val position = if (lyrics.synced) viewModel.progress.collectAsStateWithLifecycle().value.positionMs else 0L
    val currentLine = lyrics.currentLine(position)
    LaunchedEffect(currentLine) {
        // Deux lignes déjà chantées restent visibles au-dessus.
        if (currentLine >= 0) listState.animateScrollToItem((currentLine - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == currentLine
            Text(
                line.text.ifEmpty { "♪" },
                style = if (lyrics.synced) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active || !lyrics.synced) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base -> line.timeMs?.let { time -> base.clickable { viewModel.seekTo(time) } } ?: base }
                    .padding(vertical = 6.dp, horizontal = 8.dp),
            )
        }
    }
}

/** Réglage du dossier des .lrc : sans lui, seules les paroles des tags sont lues. */
@Composable
internal fun LyricsSection() {
    val context = LocalContext.current
    val settings = remember { LyricsSettings(context) }
    var folder by remember { mutableStateOf(settings.folderUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Lecture seule : Ongaku ne modifie jamais les fichiers de ce dossier.
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.folderUri = uri.toString()
        folder = uri.toString()
    }
    SectionTitle(stringResource(R.string.settings_lyrics))
    Hint(stringResource(R.string.settings_lyrics_note))
    Spacer(Modifier.height(8.dp))
    val current = folder
    if (current == null) {
        OutlinedButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.settings_lyrics_choose)) }
        return
    }
    val label = remember(current) {
        runCatching { DocumentsContract.getTreeDocumentId(current.toUri()).substringAfter(':') }.getOrDefault(current)
    }
    Text(stringResource(R.string.auto_backup_folder, label), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.auto_backup_change)) }
    TextButton(
        onClick = {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(current.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            settings.folderUri = null
            folder = null
        },
    ) { Text(stringResource(R.string.settings_lyrics_forget)) }
}
