@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.CoverShape
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.TrackGroup
import fr.devsecopsforce.ongaku.data.albums
import fr.devsecopsforce.ongaku.data.artists
import java.io.File

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private sealed interface Detail {
    data class Group(val group: TrackGroup) : Detail
    data object Favorites : Detail
    data object RecentlyAdded : Detail
    data object Top50 : Detail
    data class PlaylistDetail(val id: String) : Detail
    data object Settings : Detail
    data object Stats : Detail
    data object Appearance : Detail
}

/** Actions du menu d'un morceau, partagées par les listes et l'écran de lecture. */
private data class TrackActions(
    val data: AppData,
    val toggleFavorite: (Long) -> Unit,
    val addToPlaylist: (Long) -> Unit,
    val pickBackground: (Long) -> Unit,
    val removeBackground: (Long) -> Unit,
    val removeFromPlaylist: ((Long) -> Unit)? = null,
)

@Composable
fun OngakuApp(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    appDataViewModel: AppDataViewModel,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    LaunchedEffect(granted) {
        if (granted) libraryViewModel.load() else launcher.launch(audioPermission)
    }

    if (!granted) {
        CenteredMessage("Ongaku a besoin d'accéder à ta musique pour la lire.") {
            Button(onClick = { launcher.launch(audioPermission) }) {
                Text("Autoriser l'accès")
            }
        }
        return
    }

    val library by libraryViewModel.state.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val data by appDataViewModel.state.collectAsStateWithLifecycle()
    val settings = remember { PlaybackSettings(context) }
    ArtworkAccentEffect(player.current?.mediaMetadata?.artworkUri)

    var detail by remember { mutableStateOf<Detail?>(null) }
    // Gardé ici : les onglets quittent la composition quand une liste ou un album est ouvert.
    val startTab = LocalAppearance.current.startTab
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var addingTrackId by remember { mutableStateOf<Long?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // Conservée en texte : le sélecteur d'images peut provoquer la recréation de l'activité.
    var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        val target = decodeTarget(pendingTarget)
        if (uri != null && target != null) appDataViewModel.setBackground(target, uri)
        pendingTarget = null
    }
    val pickImage: (String) -> Unit = { target ->
        pendingTarget = target
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val tracksById = remember(library.tracks) { library.tracks.associateBy { it.id } }
    val playCounts = remember { PlayCounts(context) }
    var counts by remember { mutableStateOf(playCounts.all()) }
    DisposableEffect(playCounts) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            counts = playCounts.all()
        }
        playCounts.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { playCounts.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val actions = TrackActions(
        data = data,
        toggleFavorite = appDataViewModel::toggleFavorite,
        addToPlaylist = { addingTrackId = it },
        pickBackground = { pickImage("track:$it") },
        removeBackground = { appDataViewModel.removeBackground(BackgroundTarget.Track(it)) },
    )

    val currentDetail = detail
    val currentPlaylist = (currentDetail as? Detail.PlaylistDetail)
        ?.let { open -> data.playlists.firstOrNull { it.id == open.id } }
    val detailTitle = when (currentDetail) {
        is Detail.Group -> currentDetail.group.title
        Detail.Favorites -> "Favoris"
        Detail.RecentlyAdded -> "Ajouts récents"
        Detail.Top50 -> "Top 50"
        is Detail.PlaylistDetail -> currentPlaylist?.name.orEmpty()
        Detail.Settings -> "Réglages"
        Detail.Stats -> "Informations"
        Detail.Appearance -> "Apparence"
        null -> "Ongaku"
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackgroundImage(appDataViewModel.backgroundFile(data.appBackground))

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = { Text(detailTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (currentDetail != null) {
                            IconButton(onClick = { detail = null }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour")
                            }
                        }
                    },
                    actions = {
                        if (currentPlaylist != null) {
                            IconButton(onClick = { renamingPlaylist = currentPlaylist }) {
                                Icon(Icons.Rounded.Edit, "Renommer la liste")
                            }
                            IconButton(onClick = { deletingPlaylist = currentPlaylist }) {
                                Icon(Icons.Rounded.Delete, "Supprimer la liste")
                            }
                        } else if (currentDetail == null) {
                            IconButton(onClick = { detail = Detail.Appearance }) {
                                Icon(Icons.Rounded.Palette, "Apparence")
                            }
                            IconButton(onClick = { detail = Detail.Stats }) {
                                Icon(Icons.Rounded.Info, "Informations")
                            }
                            IconButton(onClick = { detail = Detail.Settings }) {
                                Icon(Icons.Rounded.Settings, "Réglages")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (player.current != null) {
                    MiniPlayer(
                        player = player,
                        onOpen = { playerOpen = true },
                        onPlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val currentId = player.current?.mediaId
                when {
                    library.error != null -> CenteredMessage(library.error.orEmpty())
                    !library.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    // Les réglages restent accessibles, sinon un filtre trop strict serait sans retour.
                    library.tracks.isEmpty() && currentDetail != Detail.Settings &&
                        currentDetail != Detail.Appearance && currentDetail != Detail.Stats -> CenteredMessage(
                        if (library.allTracks.isEmpty()) {
                            "Aucune musique trouvée sur ce téléphone."
                        } else {
                            "Aucun morceau ne correspond à tes filtres. Modifie-les dans les réglages."
                        },
                    ) {
                        Button(onClick = libraryViewModel::load) { Text("Rechercher à nouveau") }
                    }
                    currentDetail is Detail.Group -> TrackList(
                        tracks = currentDetail.group.tracks,
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                    )
                    currentDetail == Detail.Favorites -> TrackList(
                        tracks = library.tracks.filter { it.id in data.favorites },
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                        emptyMessage = "Touche le cœur d'un morceau pour l'ajouter ici.",
                    )
                    currentDetail == Detail.RecentlyAdded -> TrackList(
                        tracks = remember(library.tracks) {
                            library.tracks.sortedByDescending { it.dateAddedSeconds }
                                .take(SMART_PLAYLIST_SIZE)
                        },
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                    )
                    currentDetail == Detail.Top50 -> TrackList(
                        tracks = remember(counts, tracksById) {
                            counts.entries.sortedByDescending { it.value }
                                .mapNotNull { tracksById[it.key] }
                                .take(SMART_PLAYLIST_SIZE)
                        },
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                        emptyMessage = "Écoute un morceau au moins 30 secondes pour qu'il apparaisse ici.",
                        extraLabel = { track ->
                            val plays = counts[track.id] ?: 0
                            "$plays écoute${if (plays > 1) "s" else ""}"
                        },
                    )
                    currentDetail is Detail.PlaylistDetail -> TrackList(
                        tracks = currentPlaylist?.trackIds.orEmpty().mapNotNull(tracksById::get),
                        currentId = currentId,
                        actions = actions.copy(
                            removeFromPlaylist = { trackId ->
                                appDataViewModel.removeFromPlaylist(currentDetail.id, trackId)
                            },
                        ),
                        onPlay = playerViewModel::play,
                        emptyMessage = "Cette liste est vide. Ajoute des morceaux depuis leur menu.",
                    )
                    currentDetail == Detail.Settings -> SettingsScreen(
                        settings = settings,
                        allTracks = library.allTracks,
                        visibleCount = library.tracks.size,
                        // Tous les morceaux : un filtre ne doit pas vider la sauvegarde des listes.
                        onExport = { appDataViewModel.exportBackup(it, library.allTracks) },
                        onImport = { appDataViewModel.importBackup(it, library.allTracks) },
                    )
                    currentDetail == Detail.Stats -> StatsScreen(library.tracks, settings)
                    currentDetail == Detail.Appearance -> AppearanceScreen(
                        data = data,
                        backgroundFile = appDataViewModel::backgroundFile,
                        onPick = pickImage,
                        onRemove = appDataViewModel::removeBackground,
                    )
                    else -> LibraryTabs(
                        tab = tab,
                        onTabChange = { tab = it },
                        tracks = library.tracks,
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                        onPlayAll = { shuffled -> playerViewModel.playAll(library.tracks, shuffled) },
                        onOpenGroup = { detail = Detail.Group(it) },
                        onOpenFavorites = { detail = Detail.Favorites },
                        onOpenRecent = { detail = Detail.RecentlyAdded },
                        onOpenTop = { detail = Detail.Top50 },
                        onOpenPlaylist = { detail = Detail.PlaylistDetail(it.id) },
                        onCreatePlaylist = { creatingPlaylist = true },
                    )
                }
            }
        }

        if (detail != null && !playerOpen) {
            BackHandler { detail = null }
        }

        if (playerOpen && player.current != null) {
            BackHandler { playerOpen = false }
            val trackId = player.current?.mediaId?.toLongOrNull()
            FullPlayer(
                player = player,
                viewModel = playerViewModel,
                trackId = trackId,
                actions = actions,
                background = appDataViewModel.backgroundFile(playerBackgroundName(data, trackId)),
                onClose = { playerOpen = false },
            )
        }
    }

    addingTrackId?.let { trackId ->
        AddToPlaylistDialog(
            playlists = data.playlists,
            onSelect = {
                appDataViewModel.addToPlaylist(it.id, trackId)
                addingTrackId = null
            },
            onCreate = { name ->
                appDataViewModel.createPlaylist(name, trackId)
                addingTrackId = null
            },
            onDismiss = { addingTrackId = null },
        )
    }

    if (creatingPlaylist) {
        NameDialog(
            title = "Nouvelle liste",
            initial = "",
            onConfirm = {
                appDataViewModel.createPlaylist(it)
                creatingPlaylist = false
            },
            onDismiss = { creatingPlaylist = false },
        )
    }

    renamingPlaylist?.let { playlist ->
        NameDialog(
            title = "Renommer la liste",
            initial = playlist.name,
            onConfirm = {
                appDataViewModel.renamePlaylist(playlist.id, it)
                renamingPlaylist = null
            },
            onDismiss = { renamingPlaylist = null },
        )
    }

    deletingPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = { Text("Supprimer « ${playlist.name} » ?") },
            text = { Text("Seule la liste est supprimée : tes morceaux restent sur le téléphone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appDataViewModel.deletePlaylist(playlist.id)
                        deletingPlaylist = null
                        detail = null
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlaylist = null }) { Text("Annuler") }
            },
        )
    }
}

private fun playerBackgroundName(data: AppData, trackId: Long?): String? =
    trackId?.let(data.trackBackgrounds::get) ?: data.playerBackground ?: data.appBackground

/** Version réduite de l'écran de lecture : uniquement les commandes, sans accès à la bibliothèque. */
@Composable
fun LockScreenPlayer(
    playerViewModel: PlayerViewModel,
    appDataViewModel: AppDataViewModel,
    onClose: () -> Unit,
) {
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val data by appDataViewModel.state.collectAsStateWithLifecycle()
    val trackId = player.current?.mediaId?.toLongOrNull()
    ArtworkAccentEffect(player.current?.mediaMetadata?.artworkUri)
    FullPlayer(
        player = player,
        viewModel = playerViewModel,
        trackId = trackId,
        actions = null,
        background = appDataViewModel.backgroundFile(playerBackgroundName(data, trackId)),
        onClose = onClose,
    )
}

private fun decodeTarget(value: String?): BackgroundTarget? = when {
    value == "app" -> BackgroundTarget.App
    value == "player" -> BackgroundTarget.Player
    value?.startsWith("track:") == true ->
        value.removePrefix("track:").toLongOrNull()?.let(BackgroundTarget::Track)
    else -> null
}

@Composable
private fun BackgroundImage(file: File?, scrim: Brush? = null) {
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
private fun CenteredMessage(message: String, action: @Composable () -> Unit = {}) {
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

@Composable
private fun LibraryTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    tracks: List<Track>,
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
) {
    val albums = remember(tracks) { tracks.albums() }
    val artists = remember(tracks) { tracks.artists() }
    val titles = listOf("Titres", "Albums", "Artistes", "Listes")

    Column(Modifier.fillMaxSize()) {
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
                Text("Lire", maxLines = 1)
            }
            FilledTonalButton(
                onClick = { onPlayAll(true) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Lecture aléatoire", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { onTabChange(index) },
                    text = { Text(title, maxLines = 1) },
                )
            }
        }
        when (tab) {
            0 -> TrackList(tracks, currentId, actions, onPlay)
            1 -> GroupList(albums, onOpenGroup)
            2 -> GroupList(artists, onOpenGroup)
            else -> PlaylistsTab(
                actions.data,
                onOpenFavorites,
                onOpenRecent,
                onOpenTop,
                onOpenPlaylist,
                onCreatePlaylist,
            )
        }
    }
}

private const val SMART_PLAYLIST_SIZE = 50

private fun countLabel(count: Int) = "$count titre${if (count > 1) "s" else ""}"

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PlaylistsTab(
    data: AppData,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenTop: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader("Playlists") }
        item {
            ListRow(
                title = "Favoris",
                subtitle = countLabel(data.favorites.size),
                artworkUri = null,
                icon = Icons.Rounded.Favorite,
                onClick = onOpenFavorites,
            )
        }
        item {
            ListRow(
                title = "Ajouts récents",
                subtitle = "Les $SMART_PLAYLIST_SIZE derniers morceaux ajoutés",
                artworkUri = null,
                icon = Icons.Rounded.Schedule,
                onClick = onOpenRecent,
            )
        }
        item {
            ListRow(
                title = "Top 50",
                subtitle = "Tes $SMART_PLAYLIST_SIZE morceaux les plus écoutés",
                artworkUri = null,
                icon = Icons.Rounded.Star,
                onClick = onOpenTop,
            )
        }
        item { SectionHeader("Mes playlists") }
        item {
            ListRow(
                title = "Nouvelle liste",
                subtitle = "Créer une liste de lecture",
                artworkUri = null,
                icon = Icons.Rounded.Add,
                onClick = onCreatePlaylist,
            )
        }
        items(data.playlists, key = { it.id }) { playlist ->
            ListRow(
                title = playlist.name,
                subtitle = countLabel(playlist.trackIds.size),
                artworkUri = null,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = { onOpenPlaylist(playlist) },
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentId: String?,
    actions: TrackActions,
    onPlay: (List<Track>, Int) -> Unit,
    emptyMessage: String = "",
    extraLabel: (Track) -> String? = { null },
) {
    if (tracks.isEmpty()) {
        CenteredMessage(emptyMessage)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            ListRow(
                title = track.title,
                subtitle = listOfNotNull(track.artist, formatTime(track.durationMs), extraLabel(track))
                    .joinToString(" · "),
                artworkUri = track.artworkUri,
                highlighted = track.id.toString() == currentId,
                onClick = { onPlay(tracks, index) },
                trailing = {
                    FavoriteButton(track.id, actions)
                    TrackMenuButton(track.id, actions)
                },
            )
        }
    }
}

@Composable
private fun FavoriteButton(trackId: Long, actions: TrackActions) {
    val favorite = trackId in actions.data.favorites
    IconButton(onClick = { actions.toggleFavorite(trackId) }) {
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (favorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TrackMenuButton(trackId: Long, actions: TrackActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Options du morceau")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuEntry("Ajouter à une liste", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                open = false
                actions.addToPlaylist(trackId)
            }
            actions.removeFromPlaylist?.let { remove ->
                MenuEntry("Retirer de cette liste", Icons.Rounded.Delete) {
                    open = false
                    remove(trackId)
                }
            }
            MenuEntry("Choisir une image de fond", Icons.Rounded.Image) {
                open = false
                actions.pickBackground(trackId)
            }
            if (trackId in actions.data.trackBackgrounds) {
                MenuEntry("Retirer l'image de fond", Icons.Rounded.Delete) {
                    open = false
                    actions.removeBackground(trackId)
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun GroupList(groups: List<TrackGroup>, onOpen: (TrackGroup) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(groups, key = { _, group -> group.key }) { _, group ->
            ListRow(
                title = group.title,
                subtitle = group.subtitle,
                artworkUri = group.artworkUri,
                onClick = { onOpen(group) },
            )
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    highlighted: Boolean = false,
    icon: ImageVector = Icons.Rounded.MusicNote,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val compact = LocalAppearance.current.compactList
    val verticalPadding = if (compact) 0.dp else 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
private fun Artwork(
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

@Composable
private fun MiniPlayer(
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
                    if (player.isPlaying) "Pause" else "Lecture",
                    Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipNext, "Suivant", Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun FullPlayer(
    player: PlayerState,
    viewModel: PlayerViewModel,
    trackId: Long?,
    actions: TrackActions?,
    background: File?,
    onClose: () -> Unit,
) {
    val metadata = player.current?.mediaMetadata
    var queueOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = player.durationMs.coerceAtLeast(1).toFloat()
    val sliderValue = if (dragging) dragValue else player.positionMs.toFloat().coerceAtMost(duration)

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
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Fermer")
                    }
                    Spacer(Modifier.weight(1f))
                    if (trackId != null && actions != null) {
                        FavoriteButton(trackId, actions)
                        IconButton(onClick = { queueOpen = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File d'attente")
                        }
                        TrackMenuButton(trackId, actions)
                    }
                }
                Spacer(Modifier.weight(1f))
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
                Spacer(Modifier.height(16.dp))
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
                    Text(formatTime(player.durationMs), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceEvenly,
                    Alignment.CenterVertically,
                ) {
                    ToggleButton(
                        Icons.Rounded.Shuffle,
                        "Lecture aléatoire",
                        player.shuffle,
                        viewModel::toggleShuffle,
                    )
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.Rounded.SkipPrevious, "Précédent", Modifier.size(36.dp))
                    }
                    FilledIconButton(onClick = viewModel::togglePlayPause, Modifier.size(64.dp)) {
                        Icon(
                            if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (player.isPlaying) "Pause" else "Lecture",
                            Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = viewModel::next) {
                        Icon(Icons.Rounded.SkipNext, "Suivant", Modifier.size(36.dp))
                    }
                    ToggleButton(
                        if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        "Répétition",
                        player.repeatMode != Player.REPEAT_MODE_OFF,
                        viewModel::cycleRepeat,
                    )
                }
            }
        }
    }

    if (queueOpen) {
        ModalBottomSheet(onDismissRequest = { queueOpen = false }) {
            LazyColumn {
                itemsIndexed(player.queue) { index, item ->
                    ListRow(
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        subtitle = item.mediaMetadata.artist?.toString().orEmpty(),
                        artworkUri = item.mediaMetadata.artworkUri,
                        highlighted = index == player.currentIndex,
                        onClick = { viewModel.playQueueItem(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleButton(
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

@Composable
private fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    if (creating) {
        NameDialog("Nouvelle liste", "", onCreate, onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter à une liste") },
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
            TextButton(onClick = { creating = true }) { Text("Nouvelle liste") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nom") },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text("Valider")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
