// Dialogues des listes : choix d'une liste, saisie d'un nom.
@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.PlaylistEdits

@Composable
internal fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    trackCount: Int,
    onSelect: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            taken = { PlaylistEdits.isNameTaken(playlists, it) },
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (trackCount > 1) {
                    stringResource(R.string.dialog_add_tracks_to_playlist, countLabel(trackCount))
                } else {
                    stringResource(R.string.menu_add_to_playlist)
                },
            )
        },
        text = {
            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    Text(
                        playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(playlist) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { creating = true }) { Text(stringResource(R.string.playlist_new)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    /** Le nom est déjà celui d'une autre liste : l'import fusionne les listes par nom. */
    taken: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val duplicate = name.isNotBlank() && taken(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.dialog_name)) },
                isError = duplicate,
                supportingText = if (duplicate) {
                    { Text(stringResource(R.string.dialog_name_taken)) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !duplicate, onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
