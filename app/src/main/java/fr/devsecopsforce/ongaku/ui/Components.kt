// Éléments communs aux écrans : lignes, pochettes, menus d'un morceau, messages.
@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.CoverShape
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.TrackGroup
import java.io.File

@Composable
internal fun BackgroundImage(file: File?, scrim: Brush? = null) {
    if (file == null) return
    val appearance = LocalAppearance.current
    // Le flou et l'assombrissement réglables concernent le fond de l'application, pas l'écran de lecture.
    val blurRadius = if (scrim == null) appearance.backgroundBlur.dp else 0.dp
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(blurRadius),
    )
    // Voile pour que le texte reste lisible quelle que soit l'image.
    Box(
        Modifier
            .fillMaxSize()
            .background(
                scrim ?: SolidColor(
                    MaterialTheme.colorScheme.background.copy(alpha = appearance.backgroundDim),
                ),
            ),
    )
}

@Composable
internal fun CenteredMessage(message: String, action: @Composable () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** « 1 titre », « 12 titres ». */
@Composable
internal fun countLabel(count: Int) = pluralStringResource(R.plurals.common_track_count, count, count)

/** « 12 titres », ou « 12 titres · 2 introuvables » quand des fichiers ont disparu. */
@Composable
internal fun countLabel(ids: Collection<Long>, existingIds: Set<Long>): String {
    val missing = ids.count { it !in existingIds }
    val label = countLabel(ids.size - missing)
    return if (missing == 0) {
        label
    } else {
        stringResource(
            R.string.common_count_with_missing,
            label,
            pluralStringResource(R.plurals.common_missing_count, missing, missing),
        )
    }
}

/** Sous-titre d'un album (son artiste) ou d'un artiste (son nombre de titres). */
@Composable
internal fun groupSubtitle(group: TrackGroup): String =
    if (group.isArtist || group.isCollection) countLabel(group.tracks.size) else group.subtitle

/**
 * Sélection de plusieurs morceaux, partagée par toutes les listes de l'écran principal. [selected]
 * dans l'ordre des choix ; vide hors du mode sélection. Absente (null) là où la sélection n'a pas
 * de sens, comme l'écran du verrouillage.
 */
internal class TrackSelection(val selected: List<Long>, val onToggle: (Long) -> Unit) {
    val active: Boolean get() = selected.isNotEmpty()
}

internal val LocalTrackSelection = compositionLocalOf<TrackSelection?> { null }

/**
 * Un morceau. Un appui long commence la sélection ; en mode sélection, un toucher ajoute ou retire
 * le morceau au lieu de le lire.
 */
@Composable
internal fun TrackRow(
    track: Track,
    highlighted: Boolean,
    actions: TrackActions,
    extraLabel: String? = null,
    onClick: () -> Unit,
) {
    val selection = LocalTrackSelection.current
    val selecting = selection?.active == true
    val selected = selection != null && track.id in selection.selected
    val haptics = LocalHapticFeedback.current
    ListRow(
        title = track.title,
        subtitle = listOfNotNull(track.artist, formatTime(track.durationMs), extraLabel)
            .joinToString(" · "),
        artworkUri = if (selected) null else track.artworkUri,
        highlighted = highlighted,
        icon = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote,
        selected = selected,
        onClick = if (selecting) {
            { if (selection != null) selection.onToggle(track.id) }
        } else {
            onClick
        },
        onLongClick = selection?.let { current ->
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                current.onToggle(track.id)
            }
        },
        trailing = {
            // En mode sélection, les actions portent sur toute la sélection (barre du haut).
            if (!selecting) {
                FavoriteButton(track.id, actions)
                TrackMenuButton(track.id, actions)
            }
        },
    )
}

@Composable
internal fun FavoriteButton(trackId: Long, actions: TrackActions) {
    val favorite = trackId in actions.data.favorites
    IconButton(onClick = { actions.toggleFavorite(trackId) }) {
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            stringResource(if (favorite) R.string.menu_favorite_remove else R.string.menu_favorite_add),
            tint = if (favorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Menu d'un album, d'un artiste ou d'une liste ouverts. */
@Composable
internal fun QueueMenuButton(onPlayNext: () -> Unit, onAddToQueue: () -> Unit, onAddToPlaylist: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu_list_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuEntry(stringResource(R.string.menu_play_next), Icons.AutoMirrored.Rounded.PlaylistPlay) {
                open = false
                onPlayNext()
            }
            MenuEntry(stringResource(R.string.menu_add_to_queue), Icons.AutoMirrored.Rounded.QueueMusic) {
                open = false
                onAddToQueue()
            }
            MenuEntry(stringResource(R.string.menu_add_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd) {
                open = false
                onAddToPlaylist()
            }
        }
    }
}

@Composable
internal fun TrackMenuButton(trackId: Long, actions: TrackActions, queueEntries: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu_track_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (queueEntries) {
                MenuEntry(stringResource(R.string.menu_play_next), Icons.AutoMirrored.Rounded.PlaylistPlay) {
                    open = false
                    actions.playNext(trackId)
                }
                MenuEntry(stringResource(R.string.menu_add_to_queue), Icons.AutoMirrored.Rounded.QueueMusic) {
                    open = false
                    actions.addToQueue(trackId)
                }
            }
            MenuEntry(stringResource(R.string.menu_add_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd) {
                open = false
                actions.addToPlaylist(trackId)
            }
            actions.removeFromPlaylist?.let { remove ->
                MenuEntry(stringResource(R.string.menu_remove_from_playlist), Icons.Rounded.Delete) {
                    open = false
                    remove(trackId)
                }
            }
            MenuEntry(stringResource(R.string.menu_pick_background), Icons.Rounded.Image) {
                open = false
                actions.pickBackground(trackId)
            }
            if (trackId in actions.data.trackBackgrounds) {
                MenuEntry(stringResource(R.string.menu_remove_background), Icons.Rounded.Delete) {
                    open = false
                    actions.removeBackground(trackId)
                }
            }
        }
    }
}

@Composable
internal fun MenuEntry(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
internal fun ListRow(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    highlighted: Boolean = false,
    icon: ImageVector = Icons.Rounded.MusicNote,
    /** Ligne choisie dans une sélection : fond teinté. */
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val compact = LocalAppearance.current.compactList
    val verticalPadding = if (compact) 0.dp else 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, top = verticalPadding, bottom = verticalPadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri,
            Modifier.size(if (compact) 36.dp else 48.dp),
            iconSize = if (compact) 18.dp else 24.dp,
            icon = icon,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
internal fun Artwork(
    uri: Uri?,
    modifier: Modifier,
    iconSize: Dp,
    icon: ImageVector = Icons.Rounded.MusicNote,
) {
    val shape = when (LocalAppearance.current.coverShape) {
        CoverShape.ROUNDED -> RoundedCornerShape(8.dp)
        CoverShape.CIRCLE -> CircleShape
        CoverShape.SQUARE -> RectangleShape
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
