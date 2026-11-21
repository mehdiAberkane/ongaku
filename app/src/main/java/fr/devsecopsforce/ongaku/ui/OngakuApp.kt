// Écran principal : état partagé, navigation entre les écrans, dialogues ouverts depuis plusieurs écrans.
@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import fr.devsecopsforce.ongaku.data.AlbumSort
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
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
import fr.devsecopsforce.ongaku.data.missingTrackBackgrounds
import fr.devsecopsforce.ongaku.data.search
import fr.devsecopsforce.ongaku.data.sorted
import java.io.File

internal val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

internal sealed interface Detail {
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
internal data class TrackActions(
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
                    library.error != null -> CenteredMessage(library.error.orEmpty()) {
                        Button(onClick = libraryViewModel::refresh) { Text("Réessayer") }
                    }
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

internal fun playerBackgroundName(data: AppData, trackId: Long?): String? =
    trackId?.let(data.trackBackgrounds::get) ?: data.playerBackground ?: data.appBackground

internal fun decodeTarget(value: String?): BackgroundTarget? = when {
    value == "app" -> BackgroundTarget.App
    value == "player" -> BackgroundTarget.Player
    value == "widget" -> BackgroundTarget.Widget
    value?.startsWith("track:") == true ->
        value.removePrefix("track:").toLongOrNull()?.let(BackgroundTarget::Track)
    else -> null
}

internal const val SMART_PLAYLIST_SIZE = 50
