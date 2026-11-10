@file:OptIn(ExperimentalLayoutApi::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.devsecopsforce.ongaku.data.LibraryFilter
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.Track

@Composable
fun LibraryFilterSection(allTracks: List<Track>, visibleCount: Int) {
    val store = LibraryFilterStore.get(LocalContext.current)
    val filter by store.state.collectAsStateWithLifecycle()
    val extensions = remember(allTracks) {
        allTracks.groupingBy { it.extension }.eachCount().toSortedMap().toList()
    }
    val folders = remember(allTracks) {
        allTracks.groupingBy { it.folder }.eachCount().toSortedMap().toList()
    }

    SectionTitle("Morceaux affichés dans Ongaku")
    Hint("$visibleCount morceaux affichés sur ${allTracks.size}. Les autres sont masqués, jamais supprimés.")
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondsField(
            label = "Durée min (s)",
            value = filter.minSeconds,
            onChange = { value -> store.update { it.copy(minSeconds = value) } },
            modifier = Modifier.weight(1f),
        )
        SecondsField(
            label = "Durée max (s)",
            value = filter.maxSeconds,
            onChange = { value -> store.update { it.copy(maxSeconds = value) } },
            modifier = Modifier.weight(1f),
        )
    }
    Hint("Laisse vide pour ne pas mettre de limite.")

    Spacer(Modifier.height(16.dp))
    Text("Extensions de fichier")
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        extensions.forEach { (extension, count) ->
            val included = extension !in filter.excludedExtensions
            FilterChip(
                selected = included,
                onClick = {
                    store.update {
                        it.copy(
                            excludedExtensions = if (included) {
                                it.excludedExtensions + extension
                            } else {
                                it.excludedExtensions - extension
                            },
                        )
                    }
                },
                label = { Text("${extension.ifEmpty { "sans extension" }} ($count)") },
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Surveiller tous les dossiers", Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = filter.watchAllFolders,
            onCheckedChange = { all ->
                store.update { current ->
                    current.copy(
                        watchAllFolders = all,
                        // Au passage en mode manuel, on part de tout coché pour ne rien masquer d'un coup.
                        watchedFolders = if (all) {
                            current.watchedFolders
                        } else {
                            folders.map { it.first }.toSet()
                        },
                    )
                }
            },
        )
    }
    if (!filter.watchAllFolders) {
        Hint("Un nouveau dossier devra être coché ici pour apparaître.")
        Column {
            folders.forEach { (folder, count) ->
                FolderRow(folder, count, filter) { checked ->
                    store.update {
                        it.copy(
                            watchedFolders = if (checked) {
                                it.watchedFolders + folder
                            } else {
                                it.watchedFolders - folder
                            },
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { store.update { LibraryFilter() } }) { Text("Tout afficher") }
}

@Composable
private fun FolderRow(
    folder: String,
    count: Int,
    filter: LibraryFilter,
    onChange: (Boolean) -> Unit,
) {
    val checked = folder in filter.watchedFolders
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(folder.ifEmpty { "Racine" }, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(count.toString())
    }
}

@Composable
private fun SecondsField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier,
) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(MAX_DIGITS)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private const val MAX_DIGITS = 6
