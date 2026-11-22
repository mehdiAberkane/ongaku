// Onglets de la bibliothèque, recherche, listes de morceaux, d'albums et d'artistes.
@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.AlbumSort
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.GroupRow
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.PlaylistEdits
import fr.devsecopsforce.ongaku.data.FolderTree
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.TrackGroup
import fr.devsecopsforce.ongaku.data.TrackSort
import fr.devsecopsforce.ongaku.data.albums
import fr.devsecopsforce.ongaku.data.artists
import fr.devsecopsforce.ongaku.data.letterPositions
import fr.devsecopsforce.ongaku.data.rows
import fr.devsecopsforce.ongaku.data.search
import fr.devsecopsforce.ongaku.data.sortText
import kotlin.math.roundToInt

@Composable
internal fun LibraryTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    tracks: List<Track>,
    trackSort: TrackSort,
    onTrackSortChange: (TrackSort) -> Unit,
    albums: List<TrackGroup>,
    albumSort: AlbumSort,
    onAlbumSortChange: (AlbumSort) -> Unit,
    artists: List<TrackGroup>,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onOpenGroup: (TrackGroup) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenTop: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    existingIds: Set<Long>,
    folders: FolderTree.View,
    onOpenFolder: (String) -> Unit,
    genres: List<TrackGroup>,
    decades: List<TrackGroup>,
    albumGrid: Boolean,
    onAlbumGridChange: (Boolean) -> Unit,
) {
    // Index par lettre seulement quand la liste est rangée par ordre alphabétique.
    val trackLetters = remember(tracks, trackSort) {
        if (trackSort.alphabetical) tracks.letterPositions { it.sortText(trackSort) } else null
    }
    val albumLetters = remember(albums, albumSort) {
        if (albumSort.alphabetical) albums.letterPositions { it.sortText(albumSort) } else null
    }
    val artistLetters = remember(artists) { artists.letterPositions { it.title } }

    Column(Modifier.fillMaxSize()) {
        PlayAllButtons(onPlayAll)
        // Sept onglets : la rangée défile au lieu de tasser les titres.
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 8.dp) {
            LIBRARY_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { onTabChange(index) },
                    text = { Text(stringResource(title), maxLines = 1) },
                )
            }
        }
        when (tab) {
            0 -> {
                SortSelector(TrackSort.entries, trackSort, { stringResource(it.label) }, onTrackSortChange)
                TrackList(tracks, currentId, actions, onPlay, letters = trackLetters)
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SortSelector(AlbumSort.entries, albumSort, { stringResource(it.label) }, onAlbumSortChange)
                    }
                    IconButton(onClick = { onAlbumGridChange(!albumGrid) }) {
                        Icon(
                            if (albumGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            stringResource(if (albumGrid) R.string.library_albums_as_list else R.string.library_albums_as_grid),
                        )
                    }
                }
                val albumSubtitle: @Composable (TrackGroup) -> String = { album ->
                    if (albumSort == AlbumSort.YEAR) {
                        listOfNotNull(album.subtitle, album.year?.toString()).joinToString(" · ")
                    } else {
                        album.subtitle
                    }
                }
                if (albumGrid) {
                    AlbumGrid(albums, onOpenGroup, albumSubtitle)
                } else {
                    GroupList(albums, onOpenGroup, letters = albumLetters, subtitle = albumSubtitle)
                }
            }
            2 -> GroupList(artists, onOpenGroup, letters = artistLetters, subtitle = { groupSubtitle(it) })
            5 -> GroupList(genres, onOpenGroup, subtitle = { groupSubtitle(it) })
            6 -> GroupList(decades, onOpenGroup, subtitle = { groupSubtitle(it) })
            4 -> FolderBrowser(
                view = folders,
                allTracks = emptyList(),
                showPlayAll = false,
                currentId = currentId,
                actions = actions,
                onPlay = onPlay,
                onPlayAll = { _, _ -> },
                onOpenFolder = onOpenFolder,
            )
            else -> PlaylistsTab(
                actions.data,
                existingIds,
                onOpenFavorites,
                onOpenRecent,
                onOpenTop,
                onOpenPlaylist,
                onCreatePlaylist,
            )
        }
    }
}

/** « Lire » et « Lecture aléatoire » : remplacent la lecture en cours par toute la liste affichée. */
@Composable
internal fun PlayAllButtons(onPlayAll: (shuffled: Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onPlayAll(false) },
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.library_play), maxLines = 1)
        }
        FilledTonalButton(
            onClick = { onPlayAll(true) },
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.library_shuffle), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** « Trier par : Titre ▾ », avec le choix dans un menu. */
@Composable
internal fun <T> SortSelector(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 8.dp)) {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.library_sort_by, label(selected)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Recherche dans toute la bibliothèque affichée, avec les résultats groupés par type. */
@Composable
internal fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    tracks: List<Track>,
    albums: List<TrackGroup>,
    artists: List<TrackGroup>,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenGroup: (TrackGroup) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Clavier ouvert d'emblée, sauf au retour d'un album ouvert depuis les résultats.
    LaunchedEffect(Unit) { if (query.isEmpty()) focusRequester.requestFocus() }
    val results = remember(query, tracks, albums, artists) { search(query, tracks, albums, artists) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        when {
            query.isBlank() -> CenteredMessage(stringResource(R.string.search_hint))
            results.isEmpty -> CenteredMessage(stringResource(R.string.search_no_result, query.trim()))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (results.tracks.isNotEmpty()) {
                    item(key = "titres") { SectionHeader(stringResource(R.string.search_section_tracks, results.tracks.size)) }
                    itemsIndexed(results.tracks, key = { _, track -> "t${track.id}" }) { index, track ->
                        TrackRow(track, track.id.toString() == currentId, actions) {
                            keyboard?.hide()
                            onPlay(results.tracks, index)
                        }
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item(key = "albums") { SectionHeader(stringResource(R.string.search_section_albums, results.albums.size)) }
                    items(results.albums, key = { "a${it.key}" }) { album ->
                        ListRow(album.title, album.subtitle, album.artworkUri, onClick = { onOpenGroup(album) })
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item(key = "artistes") { SectionHeader(stringResource(R.string.search_section_artists, results.artists.size)) }
                    items(results.artists, key = { "r${it.key}" }) { artist ->
                        ListRow(artist.title, groupSubtitle(artist), artist.artworkUri, onClick = { onOpenGroup(artist) })
                    }
                }
            }
        }
    }
}

/** Au-dessus d'une liste : les morceaux dont le fichier a disparu, et de quoi les retirer. */
@Composable
internal fun WithMissingBanner(missing: Int, onRemove: () -> Unit, content: @Composable () -> Unit) {
    if (missing == 0) {
        content()
        return
    }
    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.library_missing_banner, missing, missing),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TextButton(onClick = onRemove) { Text(stringResource(R.string.common_remove)) }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun PlaylistsTab(
    data: AppData,
    existingIds: Set<Long>,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenTop: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.playlists_section_smart)) }
        item {
            ListRow(
                title = stringResource(R.string.main_title_favorites),
                subtitle = countLabel(data.favorites, existingIds),
                artworkUri = null,
                icon = Icons.Rounded.Favorite,
                onClick = onOpenFavorites,
            )
        }
        item {
            ListRow(
                title = stringResource(R.string.main_title_recent),
                subtitle = stringResource(R.string.playlists_recent_subtitle, SMART_PLAYLIST_SIZE),
                artworkUri = null,
                icon = Icons.Rounded.Schedule,
                onClick = onOpenRecent,
            )
        }
        item {
            ListRow(
                title = stringResource(R.string.main_title_top50),
                subtitle = stringResource(R.string.playlists_top50_subtitle, SMART_PLAYLIST_SIZE),
                artworkUri = null,
                icon = Icons.Rounded.Star,
                onClick = onOpenTop,
            )
        }
        item { SectionHeader(stringResource(R.string.playlists_section_mine)) }
        item {
            ListRow(
                title = stringResource(R.string.playlist_new),
                subtitle = stringResource(R.string.playlists_new_subtitle),
                artworkUri = null,
                icon = Icons.Rounded.Add,
                onClick = onCreatePlaylist,
            )
        }
        items(data.playlists, key = { it.id }) { playlist ->
            ListRow(
                title = playlist.name,
                subtitle = countLabel(playlist.trackIds, existingIds),
                artworkUri = null,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = { onOpenPlaylist(playlist) },
            )
        }
    }
}

@Composable
internal fun TrackList(
    tracks: List<Track>,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    emptyMessage: String = "",
    extraLabel: @Composable (Track) -> String? = { null },
    letters: List<Pair<Char, Int>>? = null,
) {
    if (tracks.isEmpty()) {
        CenteredMessage(emptyMessage)
        return
    }
    val listState = rememberLazyListState()
    FastScrollList(listState, letters, tracks.size) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(track, track.id.toString() == currentId, actions, extraLabel(track)) {
                    onPlay(tracks, index)
                }
            }
        }
    }
}

/** Morceaux d'une liste : la poignée à droite de chaque ligne se fait glisser pour la déplacer. */
@Composable
internal fun ReorderableTrackList(
    tracks: List<Track>,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    onReorder: (List<Long>) -> Unit,
    emptyMessage: String,
) {
    if (tracks.isEmpty()) {
        CenteredMessage(emptyMessage)
        return
    }
    // Ordre affiché après un glissement, le temps que la liste enregistrée suive.
    var pendingOrder by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(tracks) { pendingOrder = null }
    var dragged by remember { mutableStateOf<Int?>(null) } // position de départ
    var target by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(1) }

    val base = pendingOrder ?: tracks
    val shown = dragged?.let { PlaylistEdits.move(base, it, target) } ?: base
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(shown, key = { _, track -> track.id }) { position, track ->
            val isDragged = dragged?.let { from -> position == target && base.getOrNull(from) == track } == true
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
                    .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
            ) {
                ListRow(
                    title = track.title,
                    subtitle = listOf(track.artist, formatTime(track.durationMs)).joinToString(" · "),
                    artworkUri = track.artworkUri,
                    highlighted = track.id.toString() == currentId,
                    onClick = { onPlay(base, base.indexOf(track).coerceAtLeast(0)) },
                    trailing = {
                        FavoriteButton(track.id, actions)
                        TrackMenuButton(track.id, actions)
                        Icon(
                            Icons.Rounded.DragHandle,
                            stringResource(R.string.library_move),
                            modifier = Modifier
                                .padding(start = 4.dp, end = 12.dp)
                                .pointerInput(track.id) {
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
                                                val moved = PlaylistEdits.move(base, from, target)
                                                pendingOrder = moved
                                                onReorder(moved.map { it.id })
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

/** Page d'un album ou d'un artiste : morceaux rangés par album, puis par disque. */
@Composable
internal fun GroupTrackList(
    group: TrackGroup,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val rows = remember(group) { group.rows() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is GroupRow.AlbumHeader -> AlbumHeaderRow(row)
                is GroupRow.DiscHeader -> Text(
                    stringResource(R.string.library_disc, row.disc),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                is GroupRow.Song -> TrackRow(row.track, row.track.id.toString() == currentId, actions) {
                    onPlay(group.tracks, row.index)
                }
            }
        }
    }
}

@Composable
internal fun AlbumHeaderRow(header: GroupRow.AlbumHeader) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(header.artworkUri, Modifier.size(64.dp), iconSize = 28.dp, icon = Icons.Rounded.Album)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                header.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    header.year?.toString(),
                    countLabel(header.trackCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GroupList(
    groups: List<TrackGroup>,
    onOpen: (TrackGroup) -> Unit,
    letters: List<Pair<Char, Int>>? = null,
    subtitle: @Composable (TrackGroup) -> String = { it.subtitle },
) {
    val listState = rememberLazyListState()
    FastScrollList(listState, letters, groups.size) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(groups, key = { it.key }) { group ->
                ListRow(
                    title = group.title,
                    subtitle = subtitle(group),
                    artworkUri = group.artworkUri,
                    onClick = { onOpen(group) },
                )
            }
        }
    }
}

/** Onglets de la bibliothèque, dans l'ordre de leur index (voir aussi l'onglet au démarrage). */
internal val LIBRARY_TABS = listOf(
    R.string.library_tab_tracks,
    R.string.library_tab_albums,
    R.string.library_tab_artists,
    R.string.library_tab_playlists,
    R.string.library_tab_folders,
    R.string.library_tab_genres,
    R.string.library_tab_decades,
)

/** Albums en grille de pochettes, autant de colonnes que la largeur en permet. */
@Composable
internal fun AlbumGrid(
    albums: List<TrackGroup>,
    onOpen: (TrackGroup) -> Unit,
    subtitle: @Composable (TrackGroup) -> String,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.key }) { album ->
            Column(Modifier.clickable { onOpen(album) }) {
                Artwork(
                    album.artworkUri,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    iconSize = 48.dp,
                    icon = Icons.Rounded.Album,
                )
                Spacer(Modifier.height(6.dp))
                Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle(album),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
