@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import fr.devsecopsforce.ongaku.data.AlbumSort
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
import fr.devsecopsforce.ongaku.data.CoverShape
import fr.devsecopsforce.ongaku.data.GroupRow
import fr.devsecopsforce.ongaku.data.LibraryIdentity
import fr.devsecopsforce.ongaku.data.LibrarySortStore
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.PlaylistEdits
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.TrackGroup
import fr.devsecopsforce.ongaku.data.TrackSort
import fr.devsecopsforce.ongaku.data.albums
import fr.devsecopsforce.ongaku.data.artists
import fr.devsecopsforce.ongaku.data.letterPositions
import fr.devsecopsforce.ongaku.data.missingTrackBackgrounds
import fr.devsecopsforce.ongaku.data.rows
import fr.devsecopsforce.ongaku.data.search
import fr.devsecopsforce.ongaku.data.sortText
import fr.devsecopsforce.ongaku.data.sorted
import fr.devsecopsforce.ongaku.playback.QueueOrder
import java.io.File
import kotlin.math.roundToInt

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private sealed interface Detail {
    /** [from] : écran où revenir, par exemple la recherche qui a ouvert cet album. */
    data class Group(val group: TrackGroup, val from: Detail? = null) : Detail
    data object Search : Detail
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
    val playNext: (Long) -> Unit,
    val addToQueue: (Long) -> Unit,
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
    // Morceaux à ajouter à une liste : un seul, un album, un artiste, la file…
    var adding by remember { mutableStateOf<List<Long>?>(null) }
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
    // Tous les morceaux du téléphone : un morceau masqué par le filtre n'est pas introuvable.
    val existingIds = remember(library.allTracks) { library.allTracks.mapTo(HashSet()) { it.id } }
    val allTracksById = remember(library.allTracks) { library.allTracks.associateBy { it.id } }
    val enqueue: (List<Track>, Boolean) -> Unit = { tracks, next ->
        if (next) playerViewModel.playNext(tracks) else playerViewModel.addToQueue(tracks)
        val what = tracks.singleOrNull()?.title ?: "${tracks.size} titres"
        Toast.makeText(context, if (next) "Lu ensuite : $what" else "Ajouté à la file : $what", Toast.LENGTH_SHORT).show()
    }
    val identityReport by LibraryIdentity.pendingReport.collectAsStateWithLifecycle()
    val dataNotice by AppDataStore.pendingNotice.collectAsStateWithLifecycle()
    val playCounts = remember { PlayCounts(context) }
    var counts by remember { mutableStateOf(playCounts.all()) }
    DisposableEffect(playCounts) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            counts = playCounts.all()
        }
        playCounts.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { playCounts.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val sortStore = remember { LibrarySortStore(context) }
    var trackSort by remember { mutableStateOf(sortStore.trackSort) }
    var albumSort by remember { mutableStateOf(sortStore.albumSort) }
    // Les écoutes changent souvent : elles ne relancent le tri que s'il porte sur elles.
    val sortedTracks = remember(library.tracks, trackSort, counts.takeIf { trackSort == TrackSort.PLAYS }) {
        library.tracks.sorted(trackSort, counts)
    }
    val albums = remember(library.tracks) { library.tracks.albums() }
    val sortedAlbums = remember(albums, albumSort) { albums.sorted(albumSort) }
    var mergeFeaturing by remember { mutableStateOf(settings.mergeFeaturing) }
    val artists = remember(library.tracks, mergeFeaturing) { library.tracks.artists(mergeFeaturing) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val goBack: () -> Unit = { detail = (detail as? Detail.Group)?.from }

    // Mémorisé : un objet neuf à chaque recomposition empêcherait Compose de sauter les lignes visibles.
    val actions = remember(data, allTracksById) {
        TrackActions(
            data = data,
            toggleFavorite = appDataViewModel::toggleFavorite,
            addToPlaylist = { adding = listOf(it) },
            pickBackground = { pickImage("track:$it") },
            removeBackground = { appDataViewModel.removeBackground(BackgroundTarget.Track(it)) },
            playNext = { id -> allTracksById[id]?.let { enqueue(listOf(it), true) } },
            addToQueue = { id -> allTracksById[id]?.let { enqueue(listOf(it), false) } },
        )
    }

    val currentDetail = detail
    val currentPlaylist = (currentDetail as? Detail.PlaylistDetail)
        ?.let { open -> data.playlists.firstOrNull { it.id == open.id } }
    val favoriteTracks = remember(library.tracks, data.favorites) {
        library.tracks.filter { it.id in data.favorites }
    }
    val playlistTracks = remember(currentPlaylist, tracksById) {
        currentPlaylist?.trackIds.orEmpty().mapNotNull(tracksById::get)
    }
    val detailTitle = when (currentDetail) {
        is Detail.Group -> currentDetail.group.title
        Detail.Search -> "Rechercher"
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
                            IconButton(onClick = goBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour")
                            }
                        }
                    },
                    actions = {
                        // Album, artiste, favoris ou liste ouverts : tout ajouter à la file.
                        val detailTracks = when (currentDetail) {
                            is Detail.Group -> currentDetail.group.tracks
                            Detail.Favorites -> favoriteTracks
                            is Detail.PlaylistDetail -> playlistTracks
                            else -> emptyList()
                        }
                        if (detailTracks.isNotEmpty()) {
                            QueueMenuButton(
                                onPlayNext = { enqueue(detailTracks, true) },
                                onAddToQueue = { enqueue(detailTracks, false) },
                                onAddToPlaylist = { adding = detailTracks.map { it.id } },
                            )
                        }
                        if (currentPlaylist != null) {
                            IconButton(onClick = { renamingPlaylist = currentPlaylist }) {
                                Icon(Icons.Rounded.Edit, "Renommer la liste")
                            }
                            IconButton(onClick = { deletingPlaylist = currentPlaylist }) {
                                Icon(Icons.Rounded.Delete, "Supprimer la liste")
                            }
                        } else if (currentDetail == null) {
                            IconButton(onClick = { detail = Detail.Search }) {
                                Icon(Icons.Rounded.Search, "Rechercher")
                            }
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
                        Button(onClick = libraryViewModel::refresh) { Text("Rechercher à nouveau") }
                    }
                    currentDetail is Detail.Group -> GroupTrackList(
                        group = currentDetail.group,
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                    )
                    currentDetail == Detail.Search -> SearchScreen(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        tracks = sortedTracks,
                        albums = sortedAlbums,
                        artists = artists,
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                        onOpenGroup = { detail = Detail.Group(it, from = Detail.Search) },
                    )
                    currentDetail == Detail.Favorites -> WithMissingBanner(
                        missing = data.favorites.count { it !in existingIds },
                        onRemove = { appDataViewModel.removeMissingFavorites(existingIds) },
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            if (favoriteTracks.isNotEmpty()) {
                                PlayAllButtons { shuffled -> playerViewModel.playAll(favoriteTracks, shuffled) }
                            }
                            TrackList(
                                tracks = favoriteTracks,
                                currentId = currentId,
                                actions = actions,
                                onPlay = playerViewModel::play,
                                emptyMessage = "Touche le cœur d'un morceau pour l'ajouter ici.",
                            )
                        }
                    }
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
                    currentDetail is Detail.PlaylistDetail -> WithMissingBanner(
                        missing = currentPlaylist?.trackIds.orEmpty().count { it !in existingIds },
                        onRemove = {
                            appDataViewModel.removeMissingFromPlaylist(currentDetail.id, existingIds)
                        },
                    ) {
                        ReorderableTrackList(
                            tracks = playlistTracks,
                            onReorder = { order -> appDataViewModel.reorderPlaylist(currentDetail.id, order) },
                            currentId = currentId,
                            actions = remember(actions, currentDetail.id) {
                                actions.copy(
                                    removeFromPlaylist = { trackId ->
                                        appDataViewModel.removeFromPlaylist(currentDetail.id, trackId)
                                    },
                                )
                            },
                            onPlay = playerViewModel::play,
                            emptyMessage = "Cette liste est vide. Ajoute des morceaux depuis leur menu.",
                        )
                    }
                    currentDetail == Detail.Settings -> SettingsScreen(
                        settings = settings,
                        allTracks = library.allTracks,
                        visibleCount = library.tracks.size,
                        mergeFeaturing = mergeFeaturing,
                        onMergeFeaturingChange = {
                            mergeFeaturing = it
                            settings.mergeFeaturing = it
                        },
                        // Tous les morceaux : un filtre ne doit pas vider la sauvegarde des listes.
                        onExport = { appDataViewModel.exportBackup(it, library.allTracks) },
                        onImport = { appDataViewModel.importBackup(it, library.allTracks) },
                    )
                    currentDetail == Detail.Stats -> StatsScreen(library.tracks, settings, mergeFeaturing)
                    currentDetail == Detail.Appearance -> AppearanceScreen(
                        data = data,
                        backgroundFile = appDataViewModel::backgroundFile,
                        onPick = pickImage,
                        onRemove = appDataViewModel::removeBackground,
                        // Tous les morceaux : un morceau masqué par le filtre n'a pas disparu.
                        missingTrackImages = data.missingTrackBackgrounds(existingIds).size,
                        onRemoveMissing = { appDataViewModel.removeMissingBackgrounds(existingIds) },
                    )
                    else -> LibraryTabs(
                        tab = tab,
                        onTabChange = { tab = it },
                        tracks = sortedTracks,
                        trackSort = trackSort,
                        onTrackSortChange = {
                            trackSort = it
                            sortStore.trackSort = it
                        },
                        albums = sortedAlbums,
                        albumSort = albumSort,
                        onAlbumSortChange = {
                            albumSort = it
                            sortStore.albumSort = it
                        },
                        artists = artists,
                        currentId = currentId,
                        actions = actions,
                        onPlay = playerViewModel::play,
                        // « Lire » suit l'ordre affiché.
                        onPlayAll = { shuffled -> playerViewModel.playAll(sortedTracks, shuffled) },
                        onOpenGroup = { detail = Detail.Group(it) },
                        onOpenFavorites = { detail = Detail.Favorites },
                        onOpenRecent = { detail = Detail.RecentlyAdded },
                        onOpenTop = { detail = Detail.Top50 },
                        onOpenPlaylist = { detail = Detail.PlaylistDetail(it.id) },
                        onCreatePlaylist = { creatingPlaylist = true },
                        existingIds = existingIds,
                    )
                }
            }
        }

        if (detail != null && !playerOpen) {
            BackHandler(onBack = goBack)
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
                onAddQueueToPlaylist = { ids -> adding = ids },
            )
        }
    }

    adding?.let { trackIds ->
        AddToPlaylistDialog(
            playlists = data.playlists,
            trackCount = trackIds.size,
            onSelect = { playlist ->
                val added = trackIds.distinct().count { it !in playlist.trackIds }
                appDataViewModel.addToPlaylist(playlist.id, trackIds)
                adding = null
                val message = when {
                    added == 0 -> "Déjà dans « ${playlist.name} »"
                    trackIds.size > 1 -> "${countLabel(added)} ajouté${if (added > 1) "s" else ""} à « ${playlist.name} »"
                    else -> null
                }
                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            },
            onCreate = { name ->
                appDataViewModel.createPlaylist(name, trackIds)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }

    if (creatingPlaylist) {
        NameDialog(
            title = "Nouvelle liste",
            initial = "",
            taken = { PlaylistEdits.isNameTaken(data.playlists, it) },
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
            taken = { PlaylistEdits.isNameTaken(data.playlists, it, exceptId = playlist.id) },
            onConfirm = {
                appDataViewModel.renamePlaylist(playlist.id, it)
                renamingPlaylist = null
            },
            onDismiss = { renamingPlaylist = null },
        )
    }

    dataNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = AppDataStore::noticeShown,
            title = { Text("Fichier des listes abîmé") },
            text = {
                Text(
                    buildString {
                        append("Le fichier qui contient tes listes, tes favoris et tes images de fond était illisible. ")
                        append(
                            if (notice.restoredFromCopy) {
                                "Ongaku a repris sa dernière copie : tes dernières modifications peuvent manquer. "
                            } else {
                                "Aucune copie lisible n'existait : Ongaku repart de zéro. Tu peux réimporter une sauvegarde depuis les réglages. "
                            },
                        )
                        if (notice.setAsideName != null) {
                            append("Le fichier abîmé est gardé de côté (${notice.setAsideName}).")
                        }
                    },
                )
            },
            confirmButton = { TextButton(onClick = AppDataStore::noticeShown) { Text("OK") } },
        )
    }

    identityReport?.let { report ->
        AlertDialog(
            onDismissRequest = LibraryIdentity::reportShown,
            title = { Text("Bibliothèque renumérotée") },
            text = {
                Text(
                    buildString {
                        append("Android a changé les identifiants de ta musique. ")
                        if (report.recovered > 0) {
                            append("${report.recovered} morceau${if (report.recovered > 1) "x" else ""} de tes listes, favoris ou écoutes ")
                            append(if (report.recovered > 1) "ont été retrouvés. " else "a été retrouvé. ")
                        }
                        if (report.missing > 0) {
                            append(
                                if (report.missing > 1) {
                                    "${report.missing} sont introuvables : ils restent dans tes listes et reviendront si leur fichier réapparaît."
                                } else {
                                    "1 est introuvable : il reste dans tes listes et reviendra si son fichier réapparaît."
                                },
                            )
                        }
                    },
                )
            },
            confirmButton = { TextButton(onClick = LibraryIdentity::reportShown) { Text("OK") } },
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
    onUnlock: () -> Unit,
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
        onClose = onUnlock,
        onLockScreen = true,
    )
}

private fun decodeTarget(value: String?): BackgroundTarget? = when {
    value == "app" -> BackgroundTarget.App
    value == "player" -> BackgroundTarget.Player
    value == "widget" -> BackgroundTarget.Widget
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
) {
    val titles = listOf("Titres", "Albums", "Artistes", "Listes")
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
            0 -> {
                SortSelector(TrackSort.entries, trackSort, { it.label }, onTrackSortChange)
                TrackList(tracks, currentId, actions, onPlay, letters = trackLetters)
            }
            1 -> {
                SortSelector(AlbumSort.entries, albumSort, { it.label }, onAlbumSortChange)
                GroupList(
                    albums,
                    onOpenGroup,
                    letters = albumLetters,
                    subtitle = { album ->
                        if (albumSort == AlbumSort.YEAR) {
                            listOfNotNull(album.subtitle, album.year?.toString()).joinToString(" · ")
                        } else {
                            album.subtitle
                        }
                    },
                )
            }
            2 -> GroupList(artists, onOpenGroup, letters = artistLetters)
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
private fun PlayAllButtons(onPlayAll: (shuffled: Boolean) -> Unit) {
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
}

/** « Trier par : Titre ▾ », avec le choix dans un menu. */
@Composable
private fun <T> SortSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 8.dp)) {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Trier par : ${label(selected)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun SearchScreen(
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
            placeholder = { Text("Titre, album ou artiste") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, "Effacer la recherche")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        when {
            query.isBlank() -> CenteredMessage("Cherche dans tes titres, albums et artistes. Les accents et les majuscules ne comptent pas.")
            results.isEmpty -> CenteredMessage("Aucun résultat pour « ${query.trim()} ».")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (results.tracks.isNotEmpty()) {
                    item(key = "titres") { SectionHeader("Titres (${results.tracks.size})") }
                    itemsIndexed(results.tracks, key = { _, track -> "t${track.id}" }) { index, track ->
                        TrackRow(track, track.id.toString() == currentId, actions) {
                            keyboard?.hide()
                            onPlay(results.tracks, index)
                        }
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item(key = "albums") { SectionHeader("Albums (${results.albums.size})") }
                    items(results.albums, key = { "a${it.key}" }) { album ->
                        ListRow(album.title, album.subtitle, album.artworkUri, onClick = { onOpenGroup(album) })
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item(key = "artistes") { SectionHeader("Artistes (${results.artists.size})") }
                    items(results.artists, key = { "r${it.key}" }) { artist ->
                        ListRow(artist.title, artist.subtitle, artist.artworkUri, onClick = { onOpenGroup(artist) })
                    }
                }
            }
        }
    }
}

private const val SMART_PLAYLIST_SIZE = 50

private fun countLabel(count: Int) = "$count titre${if (count > 1) "s" else ""}"

/** « 12 titres », ou « 12 titres · 2 introuvables » quand des fichiers ont disparu. */
private fun countLabel(ids: Collection<Long>, existingIds: Set<Long>): String {
    val missing = ids.count { it !in existingIds }
    val label = countLabel(ids.size - missing)
    return if (missing == 0) label else "$label · $missing introuvable${if (missing > 1) "s" else ""}"
}

/** Au-dessus d'une liste : les morceaux dont le fichier a disparu, et de quoi les retirer. */
@Composable
private fun WithMissingBanner(missing: Int, onRemove: () -> Unit, content: @Composable () -> Unit) {
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
                    if (missing > 1) {
                        "$missing morceaux introuvables : leur fichier n'est plus sur le téléphone."
                    } else {
                        "1 morceau introuvable : son fichier n'est plus sur le téléphone."
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TextButton(onClick = onRemove) { Text("Retirer") }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

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
    existingIds: Set<Long>,
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
                subtitle = countLabel(data.favorites, existingIds),
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
                subtitle = countLabel(playlist.trackIds, existingIds),
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
private fun ReorderableTrackList(
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
                            "Déplacer",
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
private fun GroupTrackList(
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
                    "Disque ${row.disc}",
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
private fun AlbumHeaderRow(header: GroupRow.AlbumHeader) {
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
                    "${header.trackCount} titre${if (header.trackCount > 1) "s" else ""}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    highlighted: Boolean,
    actions: TrackActions,
    extraLabel: String? = null,
    onClick: () -> Unit,
) {
    ListRow(
        title = track.title,
        subtitle = listOfNotNull(track.artist, formatTime(track.durationMs), extraLabel)
            .joinToString(" · "),
        artworkUri = track.artworkUri,
        highlighted = highlighted,
        onClick = onClick,
        trailing = {
            FavoriteButton(track.id, actions)
            TrackMenuButton(track.id, actions)
        },
    )
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

/** Menu d'un album, d'un artiste ou d'une liste ouverts. */
@Composable
private fun QueueMenuButton(onPlayNext: () -> Unit, onAddToQueue: () -> Unit, onAddToPlaylist: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Options de la liste")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuEntry("Lire ensuite", Icons.AutoMirrored.Rounded.PlaylistPlay) {
                open = false
                onPlayNext()
            }
            MenuEntry("Ajouter à la file", Icons.AutoMirrored.Rounded.QueueMusic) {
                open = false
                onAddToQueue()
            }
            MenuEntry("Ajouter à une liste", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                open = false
                onAddToPlaylist()
            }
        }
    }
}

@Composable
private fun TrackMenuButton(trackId: Long, actions: TrackActions, queueEntries: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Options du morceau")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (queueEntries) {
                MenuEntry("Lire ensuite", Icons.AutoMirrored.Rounded.PlaylistPlay) {
                    open = false
                    actions.playNext(trackId)
                }
                MenuEntry("Ajouter à la file", Icons.AutoMirrored.Rounded.QueueMusic) {
                    open = false
                    actions.addToQueue(trackId)
                }
            }
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
private fun GroupList(
    groups: List<TrackGroup>,
    onOpen: (TrackGroup) -> Unit,
    letters: List<Pair<Char, Int>>? = null,
    subtitle: (TrackGroup) -> String = { it.subtitle },
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
                            Text("Déverrouiller")
                        }
                    } else {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Fermer")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (trackId != null && actions != null) {
                        FavoriteButton(trackId, actions)
                        IconButton(onClick = { queueOpen = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File d'attente")
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
        QueueSheet(player, viewModel, onAddQueueToPlaylist, onDismiss = { queueOpen = false })
    }
}

/**
 * File d'attente, dans l'ordre où elle sera jouée, ouverte sur le morceau en cours. La poignée à
 * droite de chaque ligne se fait glisser pour la déplacer.
 */
@Composable
private fun QueueSheet(
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
                "File d'attente · ${countLabel(player.queue.size)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (onAddToPlaylist != null) {
                TextButton(
                    onClick = { onAddToPlaylist(base.mapNotNull { player.queue.getOrNull(it)?.mediaId?.toLongOrNull() }) },
                ) { Text("Enregistrer") }
            }
            TextButton(onClick = { confirmClear = true }, enabled = player.queue.size > 1) { Text("Vider") }
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
                                    Icon(Icons.Rounded.Close, "Retirer de la file")
                                }
                            }
                            Icon(
                                Icons.Rounded.DragHandle,
                                "Déplacer",
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
            title = { Text("Vider la file ?") },
            text = { Text("Seul le morceau en cours reste dans la file.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearQueue()
                        confirmClear = false
                    },
                ) { Text("Vider") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

/**
 * Barre de progression et temps écoulé. Seule à lire la position : elle est seule à se redessiner
 * pendant la lecture.
 */
@Composable
private fun ProgressBar(viewModel: PlayerViewModel) {
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
private fun PlayerCover(uri: Uri?) {
    if (uri == null) return
    var loaded by remember(uri) { mutableStateOf(false) }
    val shape = when (LocalAppearance.current.coverShape) {
        CoverShape.ROUNDED -> RoundedCornerShape(16.dp)
        CoverShape.CIRCLE -> CircleShape
        CoverShape.SQUARE -> RectangleShape
    }
    AsyncImage(
        model = uri,
        contentDescription = "Pochette",
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
    trackCount: Int,
    onSelect: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    if (creating) {
        NameDialog(
            title = "Nouvelle liste",
            initial = "",
            taken = { PlaylistEdits.isNameTaken(playlists, it) },
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trackCount > 1) "Ajouter ${countLabel(trackCount)} à une liste" else "Ajouter à une liste") },
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
                label = { Text("Nom") },
                isError = duplicate,
                supportingText = if (duplicate) {
                    { Text("Une liste porte déjà ce nom.") }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !duplicate, onClick = { onConfirm(name) }) {
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
