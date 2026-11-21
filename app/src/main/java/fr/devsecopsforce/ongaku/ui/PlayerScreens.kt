// Lecteur : mini-lecteur, écran de lecture, file d'attente, écran du verrouillage.
@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.CoverShape
import fr.devsecopsforce.ongaku.playback.QueueOrder
import java.io.File
import kotlin.math.roundToInt

/** Version réduite de l'écran de lecture : uniquement les commandes, sans accès à la bibliothèque. */
@Composable
fun LockScreenPlayer(
    playerViewModel: PlayerViewModel,
    appData: AppDataRepository,
    onUnlock: () -> Unit,
) {
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val data by appData.state.collectAsStateWithLifecycle()
    val trackId = player.current?.mediaId?.toLongOrNull()
    ArtworkAccentEffect(player.current?.mediaMetadata?.artworkUri)
    FullPlayer(
        player = player,
        viewModel = playerViewModel,
        trackId = trackId,
        actions = null,
        background = appData.backgroundFile(playerBackgroundName(data, trackId)),
        onClose = onUnlock,
        onLockScreen = true,
    )
}

@Composable
internal fun MiniPlayer(
    player: PlayerState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val metadata = player.current?.mediaMetadata
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(metadata?.artworkUri, Modifier.size(60.dp), iconSize = 28.dp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    metadata?.title?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    metadata?.artist?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (player.isPlaying) R.string.player_pause else R.string.player_play),
                    Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.size(30.dp))
            }
        }
    }
}

@Composable
internal fun FullPlayer(
    player: PlayerState,
    viewModel: PlayerViewModel,
    trackId: Long?,
    actions: TrackActions?,
    background: File?,
    onClose: () -> Unit,
    onAddQueueToPlaylist: ((trackIds: List<Long>) -> Unit)? = null,
    /** Par-dessus le verrouillage : le bouton du haut ouvre le déverrouillage d'Android. */
    onLockScreen: Boolean = false,
) {
    val metadata = player.current?.mediaMetadata
    var queueOpen by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val base = MaterialTheme.colorScheme.background
            val dim = LocalAppearance.current.playerDim
            BackgroundImage(
                file = background,
                // Le haut et les commandes restent assez sombres pour rester lisibles.
                scrim = Brush.verticalGradient(
                    0f to base.copy(alpha = maxOf(0.55f, dim)),
                    0.18f to base.copy(alpha = dim),
                    0.55f to base.copy(alpha = dim),
                    0.75f to base.copy(alpha = maxOf(0.80f, dim)),
                    1f to base.copy(alpha = maxOf(0.92f, dim)),
                ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onLockScreen) {
                        TextButton(onClick = onClose) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.player_unlock))
                        }
                    } else {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_close))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (trackId != null && actions != null) {
                        FavoriteButton(trackId, actions)
                        IconButton(onClick = { queueOpen = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.player_queue))
                        }
                        // Le morceau en cours : « Lire ensuite » n'aurait pas de sens.
                        TrackMenuButton(trackId, actions, queueEntries = false)
                    }
                }
                // Pochette au centre de l'espace libre ; rien quand le morceau n'en a pas.
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerCover(metadata?.artworkUri)
                }
                Text(
                    metadata?.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    metadata?.artist?.toString().orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() }?.let { album ->
                    Text(
                        album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ProgressBar(viewModel)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceEvenly,
                    Alignment.CenterVertically,
                ) {
                    ToggleButton(
                        Icons.Rounded.Shuffle,
                        stringResource(R.string.player_shuffle),
                        player.shuffle,
                        viewModel::toggleShuffle,
                    )
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(36.dp))
                    }
                    FilledIconButton(onClick = viewModel::togglePlayPause, Modifier.size(64.dp)) {
                        Icon(
                            if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (player.isPlaying) R.string.player_pause else R.string.player_play),
                            Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = viewModel::next) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.size(36.dp))
                    }
                    ToggleButton(
                        if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        stringResource(R.string.player_repeat),
                        player.repeatMode != Player.REPEAT_MODE_OFF,
                        viewModel::cycleRepeat,
                    )
                }
            }
        }
    }

    if (queueOpen) {
        QueueSheet(player, viewModel, onAddQueueToPlaylist, onDismiss = { queueOpen = false })
    }
}

/**
 * File d'attente, dans l'ordre où elle sera jouée, ouverte sur le morceau en cours. La poignée à
 * droite de chaque ligne se fait glisser pour la déplacer.
 */
@Composable
internal fun QueueSheet(
    player: PlayerState,
    viewModel: PlayerViewModel,
    onAddToPlaylist: ((trackIds: List<Long>) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val order = player.playOrder.takeIf { it.size == player.queue.size } ?: player.queue.indices.toList()
    // Ordre affiché après un glissement, le temps que le lecteur confirme.
    var pendingOrder by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(player.playOrder, player.queue) { pendingOrder = null }
    var dragged by remember { mutableStateOf<Int?>(null) } // position de départ
    var target by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(1) }
    var confirmClear by remember { mutableStateOf(false) }

    val base = pendingOrder ?: order
    val shown = dragged?.let { QueueOrder.move(base, it, target) } ?: base
    val currentPosition = base.indexOf(player.currentIndex).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPosition - 1).coerceAtLeast(0))

    // Sans geste sur la feuille : sinon glisser une poignée vers le bas fermerait la file.
    // Elle se ferme en touchant à côté ou avec Retour.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetGesturesEnabled = false) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.queue_title, countLabel(player.queue.size)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (onAddToPlaylist != null) {
                TextButton(
                    onClick = { onAddToPlaylist(base.mapNotNull { player.queue.getOrNull(it)?.mediaId?.toLongOrNull() }) },
                ) { Text(stringResource(R.string.queue_save)) }
            }
            TextButton(onClick = { confirmClear = true }, enabled = player.queue.size > 1) { Text(stringResource(R.string.queue_clear)) }
        }
        LazyColumn(state = listState) {
            itemsIndexed(shown, key = { _, index -> index }) { position, index ->
                val item = player.queue.getOrNull(index) ?: return@itemsIndexed
                val isDragged = dragged?.let { from -> position == target && base.getOrNull(from) == index } == true
                val currentPos by rememberUpdatedState(position)
                Box(
                    Modifier
                        .onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            // Lu au dessin, parfois après le relâchement : le glissement peut déjà être fini.
                            val from = dragged
                            if (isDragged && from != null) {
                                translationY = offset - (target - from) * rowHeight
                                shadowElevation = 8f
                            }
                        }
                        .background(
                            if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        ),
                ) {
                    ListRow(
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        subtitle = item.mediaMetadata.artist?.toString().orEmpty(),
                        artworkUri = item.mediaMetadata.artworkUri,
                        highlighted = index == player.currentIndex,
                        onClick = { viewModel.playQueueItem(index) },
                        trailing = {
                            if (index != player.currentIndex) {
                                IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.queue_remove))
                                }
                            }
                            Icon(
                                Icons.Rounded.DragHandle,
                                stringResource(R.string.library_move),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .pointerInput(index) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragged = currentPos
                                                target = currentPos
                                                offset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                val from = dragged ?: return@detectDragGestures
                                                offset += amount.y
                                                target = (from + (offset / rowHeight).roundToInt())
                                                    .coerceIn(0, base.lastIndex)
                                            },
                                            onDragEnd = {
                                                val from = dragged
                                                if (from != null && from != target) {
                                                    pendingOrder = QueueOrder.move(base, from, target)
                                                    viewModel.moveInQueue(from, target)
                                                }
                                                dragged = null
                                                offset = 0f
                                            },
                                            onDragCancel = {
                                                dragged = null
                                                offset = 0f
                                            },
                                        )
                                    },
                            )
                        },
                    )
                }
            }
        }
    }


    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = { Text(stringResource(R.string.queue_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearQueue()
                        confirmClear = false
                    },
                ) { Text(stringResource(R.string.queue_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/**
 * Barre de progression et temps écoulé. Seule à lire la position : elle est seule à se redessiner
 * pendant la lecture.
 */
@Composable
internal fun ProgressBar(viewModel: PlayerViewModel) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = progress.durationMs.coerceAtLeast(1).toFloat()
    val sliderValue = if (dragging) dragValue else progress.positionMs.toFloat().coerceIn(0f, duration)
    Slider(
        value = sliderValue,
        valueRange = 0f..duration,
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            viewModel.seekTo(dragValue.toLong())
            dragging = false
        },
    )
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.bodySmall)
        Text(formatTime(progress.durationMs), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Pochette de l'écran de lecture, à la forme choisie dans l'apparence. Tant qu'elle n'est pas
 * chargée, ou si le morceau n'en a pas, rien n'est dessiné : pas de carré vide devant l'image de fond.
 */
@Composable
internal fun PlayerCover(uri: Uri?) {
    if (uri == null) return
    var loaded by remember(uri) { mutableStateOf(false) }
    val shape = when (LocalAppearance.current.coverShape) {
        CoverShape.ROUNDED -> RoundedCornerShape(16.dp)
        CoverShape.CIRCLE -> CircleShape
        CoverShape.SQUARE -> RectangleShape
    }
    AsyncImage(
        model = uri,
        contentDescription = stringResource(R.string.player_cover),
        contentScale = ContentScale.Crop,
        onSuccess = { loaded = true },
        onError = { loaded = false },
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .widthIn(max = 360.dp)
            .aspectRatio(1f)
            .graphicsLayer { alpha = if (loaded) 1f else 0f }
            .clip(shape),
    )
}

@Composable
internal fun ToggleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            description,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
