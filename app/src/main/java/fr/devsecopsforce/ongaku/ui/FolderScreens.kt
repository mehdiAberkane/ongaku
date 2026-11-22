// Navigation par dossiers : l'arborescence du téléphone, dossier par dossier.
package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.FolderTree
import fr.devsecopsforce.ongaku.data.Track

/**
 * Un dossier : ses sous-dossiers, puis ses propres morceaux. [allTracks] : ses morceaux et ceux de
 * ses sous-dossiers, lus par « Lire » et « Lecture aléatoire » ([showPlayAll] faux à la racine, où
 * l'onglet a déjà ces boutons pour toute la bibliothèque).
 */
@Composable
internal fun FolderBrowser(
    view: FolderTree.View,
    allTracks: List<Track>,
    showPlayAll: Boolean,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    if (view.subfolders.isEmpty() && view.tracks.isEmpty()) {
        CenteredMessage(stringResource(R.string.folder_empty))
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (showPlayAll && allTracks.isNotEmpty()) {
            PlayAllButtons { shuffled -> onPlayAll(allTracks, shuffled) }
        }
        // Avec ou sans les sous-dossiers : les deux lectures ne diffèrent que s'il y en a.
        if (view.subfolders.isNotEmpty() && view.tracks.isNotEmpty()) {
            TextButton(
                onClick = { onPlay(view.tracks, 0) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(pluralStringResource(R.plurals.folder_play_only_here, view.tracks.size, view.tracks.size))
            }
        }
        // Une position de défilement par dossier : un sous-dossier s'ouvre en haut de sa liste.
        val listState = key(view.path) { rememberLazyListState() }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(view.subfolders, key = { "folder:${it.path}" }) { folder ->
                ListRow(
                    title = folder.name,
                    subtitle = countLabel(folder.trackCount),
                    artworkUri = null,
                    icon = Icons.Rounded.Folder,
                    onClick = { onOpenFolder(folder.path) },
                )
            }
            itemsIndexed(view.tracks, key = { _, track -> "track:${track.id}" }) { index, track ->
                TrackRow(track, track.id.toString() == currentId, actions) { onPlay(view.tracks, index) }
            }
        }
    }
}
