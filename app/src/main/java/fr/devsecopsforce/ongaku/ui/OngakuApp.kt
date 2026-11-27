// Écran principal : état partagé, navigation entre les écrans, dialogues ouverts depuis plusieurs écrans.
@file:OptIn(ExperimentalMaterial3Api::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import android.Manifest
import android.content.SharedPreferences
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.AlbumSort
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
import fr.devsecopsforce.ongaku.data.FolderTree
import fr.devsecopsforce.ongaku.data.LibraryIdentity
import fr.devsecopsforce.ongaku.data.LibrarySortStore
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import fr.devsecopsforce.ongaku.data.PlaylistEdits
import fr.devsecopsforce.ongaku.data.RuleList
import fr.devsecopsforce.ongaku.data.SmartLists
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.TrackSort
import fr.devsecopsforce.ongaku.data.albums
import fr.devsecopsforce.ongaku.data.artists
import fr.devsecopsforce.ongaku.data.decades
import fr.devsecopsforce.ongaku.data.genres
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
) {
    companion object {
        /** Écran du verrouillage : seul le favori agit, les autres actions n'y sont pas proposées. */
        fun favoriteOnly(data: AppData, toggleFavorite: (Long) -> Unit) = TrackActions(
            data = data,
            toggleFavorite = toggleFavorite,
            addToPlaylist = {},
            pickBackground = {},
            removeBackground = {},
            playNext = {},
            addToQueue = {},
        )
    }
}

@Composable
fun OngakuApp(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    appData: AppDataRepository,
) {
    val context = LocalContext.current
    // Textes lus hors composition (messages des clics) : suivent la configuration en cours.
    val resources = LocalResources.current
    val access = rememberPermissionAccess(audioPermission)
    val granted = access.granted
    // Demandée d'elle-même une seule fois ; ensuite, par le bouton.
    var autoRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(granted) {
        if (granted) {
            libraryViewModel.load()
        } else if (!autoRequested) {
            autoRequested = true
            access.request()
        }
    }

    if (!granted) {
        if (access.blocked) {
            CenteredMessage(stringResource(R.string.main_permission_blocked)) {
                Button(onClick = access.request) { Text(stringResource(R.string.main_permission_settings)) }
            }
        } else {
            CenteredMessage(stringResource(R.string.main_permission_needed)) {
                Button(onClick = access.request) { Text(stringResource(R.string.main_permission_allow)) }
            }
        }
        return
    }

    val library by libraryViewModel.state.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val data by appData.state.collectAsStateWithLifecycle()
    val settings = remember { PlaybackSettings(context) }
    ArtworkAccentEffect(player.current?.mediaMetadata?.artworkUri)

    // Sauvegardés : une rotation ou un changement de thème recrée l'activité.
    var detail by rememberSaveable(stateSaver = DetailSaver) { mutableStateOf<Detail?>(null) }
    // Gardé ici : les onglets quittent la composition quand une liste ou un album est ouvert.
    val startTab = LocalAppearance.current.startTab
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    // Morceaux à ajouter à une liste : un seul, un album, un artiste, la file…
    var adding by rememberSaveable(stateSaver = TrackIdsSaver) { mutableStateOf<List<Long>?>(null) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Liste par règle en cours de modification : "" pour une nouvelle.
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingRuleId by rememberSaveable { mutableStateOf<String?>(null) }

    // Conservée en texte : le sélecteur d'images peut provoquer la recréation de l'activité.
    var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        val target = decodeTarget(pendingTarget)
        if (uri != null && target != null) appData.setBackground(target, uri)
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
        val what = tracks.singleOrNull()?.title ?: resources.getString(R.string.queue_n_tracks, tracks.size)
        val message = resources.getString(if (next) R.string.queue_toast_play_next else R.string.queue_toast_added, what)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val identityReport by LibraryIdentity.pendingReport.collectAsStateWithLifecycle()
    val dataNotice by AppDataStore.pendingNotice.collectAsStateWithLifecycle()
    val playCounts = remember { PlayCounts(context) }
    var counts by remember { mutableStateOf(playCounts.all()) }
    var lastPlayed by remember { mutableStateOf(playCounts.lastPlayed()) }
    DisposableEffect(playCounts) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            counts = playCounts.all()
            lastPlayed = playCounts.lastPlayed()
        }
        playCounts.prefs.registerOnSharedPreferenceChangeListener(listener)
        playCounts.lastPlayedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            playCounts.prefs.unregisterOnSharedPreferenceChangeListener(listener)
            playCounts.lastPlayedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    var smartSize by remember { mutableIntStateOf(settings.smartListSize) }
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
    val unknownGenre = stringResource(R.string.library_unknown_genre)
    val unknownYear = stringResource(R.string.library_unknown_year)
    val genres = remember(library.tracks, unknownGenre) { library.tracks.genres(unknownGenre) }
    val decades = remember(library.tracks, unknownYear) {
        library.tracks.decades({ resources.getString(R.string.library_decade, it) }, unknownYear)
    }
    var albumGrid by remember { mutableStateOf(sortStore.albumGrid) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val goBack: () -> Unit = {
        detail = when (val open = detail) {
            is Detail.Group -> open.from
            // Ouvert depuis les réglages : on y revient.
            Detail.Equalizer -> Detail.Settings
            is Detail.Folder -> FolderTree.parent(open.path)?.takeIf { it.isNotEmpty() }?.let(Detail::Folder)
            else -> null
        }
    }

    // Mémorisé : un objet neuf à chaque recomposition empêcherait Compose de sauter les lignes visibles.
    val actions = remember(data, allTracksById) {
        TrackActions(
            data = data,
            toggleFavorite = appData::toggleFavorite,
            addToPlaylist = { adding = listOf(it) },
            pickBackground = { pickImage("track:$it") },
            removeBackground = { appData.removeBackground(BackgroundTarget.Track(it)) },
            playNext = { id -> allTracksById[id]?.let { enqueue(listOf(it), true) } },
            addToQueue = { id -> allTracksById[id]?.let { enqueue(listOf(it), false) } },
        )
    }

    val currentDetail = detail
    val currentPlaylist = (currentDetail as? Detail.PlaylistDetail)
        ?.let { open -> data.playlists.firstOrNull { it.id == open.id } }
    // Dossiers : la racine pour l'onglet, le dossier ouvert et tous ses morceaux pour la lecture.
    val folderRoot = remember(library.tracks) { FolderTree.view(library.tracks, "") }
    val folderPath = (currentDetail as? Detail.Folder)?.path
    val folderView = remember(folderPath, library.tracks) { folderPath?.let { FolderTree.view(library.tracks, it) } }
    val folderTracks = remember(folderPath, library.tracks) {
        folderPath?.let { FolderTree.tracksUnder(library.tracks, it) }.orEmpty()
    }
    // Liste automatique ouverte : recalculée quand les écoutes, les favoris ou la taille changent.
    val smartKind = (currentDetail as? Detail.Smart)?.kind
    val smartTracks = remember(smartKind, library.tracks, counts, lastPlayed, data.favorites, smartSize) {
        smartKind?.let {
            SmartLists.build(it, library.tracks, counts, lastPlayed, data.favorites, System.currentTimeMillis(), smartSize)
        }.orEmpty()
    }
    val renamingPlaylist = renamingId?.let { id -> data.playlists.firstOrNull { it.id == id } }
    val deletingPlaylist = deletingId?.let { id -> data.playlists.firstOrNull { it.id == id } }
    val currentRule = (currentDetail as? Detail.RuleListDetail)?.let { open -> data.ruleLists.firstOrNull { it.id == open.id } }
    val ruleTracks = remember(currentRule, library.tracks) { currentRule?.tracks(library.tracks).orEmpty() }
    val ruleCounts = remember(data.ruleLists, library.tracks) {
        data.ruleLists.associate { rule -> rule.id to library.tracks.count(rule::matches) }
    }
    // Retrouvé dans la bibliothèque actuelle : la page suit un morceau ajouté ou retiré.
    val groupKey = (currentDetail as? Detail.Group)?.key
    val openGroup = remember(groupKey, albums, artists, genres, decades) {
        groupKey?.let { findGroup(it, albums, artists, genres, decades) }
    }
    LaunchedEffect(groupKey, openGroup, library.loaded) {
        // Album ou artiste disparu (morceaux supprimés, filtre) : retour à l'écran précédent.
        if (library.loaded && groupKey != null && openGroup == null) {
            detail = (detail as? Detail.Group)?.from
        }
    }
    val favoriteTracks = remember(library.tracks, data.favorites) {
        library.tracks.filter { it.id in data.favorites }
    }
    val playlistTracks = remember(currentPlaylist, tracksById) {
        currentPlaylist?.trackIds.orEmpty().mapNotNull(tracksById::get)
    }
    val detailTitle = when (currentDetail) {
        is Detail.Group -> openGroup?.title.orEmpty()
        Detail.Search -> stringResource(R.string.main_title_search)
        Detail.Favorites -> stringResource(R.string.main_title_favorites)
        is Detail.Smart -> smartTitle(currentDetail.kind, smartSize)
        is Detail.PlaylistDetail -> currentPlaylist?.name.orEmpty()
        is Detail.RuleListDetail -> currentRule?.name.orEmpty()
        Detail.Settings -> stringResource(R.string.main_title_settings)
        Detail.Stats -> stringResource(R.string.main_title_stats)
        Detail.Appearance -> stringResource(R.string.main_title_appearance)
        Detail.Equalizer -> stringResource(R.string.eq_title)
        is Detail.Folder -> FolderTree.name(currentDetail.path)
        null -> stringResource(R.string.app_name)
    }

    // Paysage et grand écran : la bibliothèque à gauche, le lecteur toujours visible à droite.
    val configuration = LocalConfiguration.current
    val twoPane = PaneLayout.twoPane(configuration.screenWidthDp, configuration.screenHeightDp)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackgroundImage(appData.backgroundFile(data.appBackground))

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
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
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                                    }
                                }
                            },
                            actions = {
                                // Album, artiste, favoris ou liste ouverts : tout ajouter à la file.
                                val detailTracks = when (currentDetail) {
                                    is Detail.Group -> openGroup?.tracks.orEmpty()
                                    Detail.Favorites -> favoriteTracks
                                    is Detail.Smart -> smartTracks
                                    is Detail.Folder -> folderTracks
                                    is Detail.PlaylistDetail -> playlistTracks
                                    is Detail.RuleListDetail -> ruleTracks
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
                                    IconButton(onClick = { renamingId = currentPlaylist.id }) {
                                        Icon(Icons.Rounded.Edit, stringResource(R.string.main_rename_playlist))
                                    }
                                    IconButton(onClick = { deletingId = currentPlaylist.id }) {
                                        Icon(Icons.Rounded.Delete, stringResource(R.string.main_delete_playlist))
                                    }
                                } else if (currentRule != null) {
                                    IconButton(onClick = { editingRuleId = currentRule.id }) {
                                        Icon(Icons.Rounded.Edit, stringResource(R.string.rule_edit))
                                    }
                                    IconButton(onClick = { deletingRuleId = currentRule.id }) {
                                        Icon(Icons.Rounded.Delete, stringResource(R.string.rule_delete))
                                    }
                                } else if (currentDetail == null) {
                                    IconButton(onClick = { detail = Detail.Search }) {
                                        Icon(Icons.Rounded.Search, stringResource(R.string.main_title_search))
                                    }
                                    IconButton(onClick = { detail = Detail.Appearance }) {
                                        Icon(Icons.Rounded.Palette, stringResource(R.string.main_title_appearance))
                                    }
                                    IconButton(onClick = { detail = Detail.Stats }) {
                                        Icon(Icons.Rounded.Info, stringResource(R.string.main_title_stats))
                                    }
                                    IconButton(onClick = { detail = Detail.Settings }) {
                                        Icon(Icons.Rounded.Settings, stringResource(R.string.main_title_settings))
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        // En deux colonnes, le lecteur est déjà affiché à droite.
                        if (player.current != null && !twoPane) {
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
                                Button(onClick = libraryViewModel::refresh) { Text(stringResource(R.string.main_retry)) }
                            }
                            !library.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            // Les réglages restent accessibles, sinon un filtre trop strict serait sans retour.
                            library.tracks.isEmpty() && currentDetail != Detail.Settings &&
                                currentDetail != Detail.Appearance && currentDetail != Detail.Stats &&
                                currentDetail != Detail.Equalizer -> CenteredMessage(
                                stringResource(
                                    if (library.allTracks.isEmpty()) R.string.main_no_music else R.string.main_all_filtered,
                                ),
                            ) {
                                Button(onClick = libraryViewModel::refresh) { Text(stringResource(R.string.main_search_again)) }
                            }
                            // Le temps de retrouver le groupe ou de revenir en arrière : rien à afficher.
                            currentDetail is Detail.Group -> if (openGroup != null) GroupTrackList(
                                group = openGroup,
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
                                onOpenGroup = { detail = Detail.Group(it.key, from = Detail.Search) },
                            )
                            currentDetail == Detail.Favorites -> WithMissingBanner(
                                missing = data.favorites.count { it !in existingIds },
                                onRemove = { appData.removeMissingFavorites(existingIds) },
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
                                        emptyMessage = stringResource(R.string.library_favorites_empty),
                                    )
                                }
                            }
                            currentDetail is Detail.Smart -> SmartListScreen(
                                kind = currentDetail.kind,
                                tracks = smartTracks,
                                counts = counts,
                                currentId = currentId,
                                actions = actions,
                                onPlay = playerViewModel::play,
                                onPlayAll = playerViewModel::playAll,
                            )
                            currentDetail is Detail.RuleListDetail -> Column(Modifier.fillMaxSize()) {
                                if (ruleTracks.isNotEmpty()) {
                                    PlayAllButtons { shuffled -> playerViewModel.playAll(ruleTracks, shuffled) }
                                }
                                TrackList(
                                    tracks = ruleTracks,
                                    currentId = currentId,
                                    actions = actions,
                                    onPlay = playerViewModel::play,
                                    emptyMessage = stringResource(R.string.rule_empty),
                                )
                            }
                            currentDetail is Detail.PlaylistDetail -> WithMissingBanner(
                                missing = currentPlaylist?.trackIds.orEmpty().count { it !in existingIds },
                                onRemove = {
                                    appData.removeMissingFromPlaylist(currentDetail.id, existingIds)
                                },
                            ) {
                                ReorderableTrackList(
                                    tracks = playlistTracks,
                                    onReorder = { order -> appData.reorderPlaylist(currentDetail.id, order) },
                                    currentId = currentId,
                                    actions = remember(actions, currentDetail.id) {
                                        actions.copy(
                                            removeFromPlaylist = { trackId ->
                                                appData.removeFromPlaylist(currentDetail.id, trackId)
                                            },
                                        )
                                    },
                                    onPlay = playerViewModel::play,
                                    emptyMessage = stringResource(R.string.library_playlist_empty),
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
                                onExport = { appData.exportBackup(it, library.allTracks) },
                                onImport = { appData.importBackup(it, library.allTracks) },
                                onOpenEqualizer = { detail = Detail.Equalizer },
                            onAutoBackupNow = appData::autoBackupNow,
                                smartSize = smartSize,
                                onSmartSizeChange = {
                                    smartSize = it
                                    settings.smartListSize = it
                                },
                            )
                            currentDetail == Detail.Stats -> StatsScreen(library.tracks, settings, mergeFeaturing)
                            currentDetail == Detail.Equalizer -> EqualizerScreen()
                            currentDetail is Detail.Folder -> FolderBrowser(
                                view = folderView ?: FolderTree.View(currentDetail.path, emptyList(), emptyList()),
                                allTracks = folderTracks,
                                showPlayAll = true,
                                currentId = currentId,
                                actions = actions,
                                onPlay = playerViewModel::play,
                                onPlayAll = playerViewModel::playAll,
                                onOpenFolder = { detail = Detail.Folder(it) },
                            )
                            currentDetail == Detail.Appearance -> AppearanceScreen(
                                data = data,
                                backgroundFile = appData::backgroundFile,
                                onPick = pickImage,
                                onRemove = appData::removeBackground,
                                // Tous les morceaux : un morceau masqué par le filtre n'a pas disparu.
                                missingTrackImages = data.missingTrackBackgrounds(existingIds).size,
                                onRemoveMissing = { appData.removeMissingBackgrounds(existingIds) },
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
                                onOpenGroup = { detail = Detail.Group(it.key) },
                                onOpenFavorites = { detail = Detail.Favorites },
                                smartSize = smartSize,
                                onOpenSmart = { detail = Detail.Smart(it) },
                                onOpenPlaylist = { detail = Detail.PlaylistDetail(it.id) },
                                onCreatePlaylist = { creatingPlaylist = true },
                                ruleCounts = ruleCounts,
                                onOpenRuleList = { detail = Detail.RuleListDetail(it.id) },
                                onCreateRuleList = { editingRuleId = "" },
                                existingIds = existingIds,
                                folders = folderRoot,
                                onOpenFolder = { detail = Detail.Folder(it) },
                                genres = genres,
                                decades = decades,
                                albumGrid = albumGrid,
                                onAlbumGridChange = {
                                    albumGrid = it
                                    sortStore.albumGrid = it
                                },
                            )
                        }
                    }
                }
            }

            if (twoPane) {
                VerticalDivider()
                Box(Modifier.width(PaneLayout.playerWidthDp(configuration.screenWidthDp).dp).fillMaxHeight()) {
                    if (player.current != null) {
                        val trackId = player.current?.mediaId?.toLongOrNull()
                        FullPlayer(
                            player = player,
                            viewModel = playerViewModel,
                            trackId = trackId,
                            actions = actions,
                            background = appData.backgroundFile(playerBackgroundName(data, trackId)),
                            onClose = null,
                            onAddQueueToPlaylist = { ids -> adding = ids },
                        )
                    } else {
                        CenteredMessage(stringResource(R.string.player_nothing_playing))
                    }
                }
            }
        }

        val playerOverlay = playerOpen && player.current != null && !twoPane
        if (detail != null && !playerOverlay) {
            BackHandler(onBack = goBack)
        }

        if (playerOverlay) {
            BackHandler { playerOpen = false }
            val trackId = player.current?.mediaId?.toLongOrNull()
            FullPlayer(
                player = player,
                viewModel = playerViewModel,
                trackId = trackId,
                actions = actions,
                background = appData.backgroundFile(playerBackgroundName(data, trackId)),
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
                appData.addToPlaylist(playlist.id, trackIds)
                adding = null
                val message = when {
                    added == 0 -> resources.getString(R.string.playlist_already_in, playlist.name)
                    trackIds.size > 1 -> resources.getQuantityString(
                        R.plurals.playlist_tracks_added,
                        added,
                        added,
                        playlist.name,
                    )
                    else -> null
                }
                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            },
            onCreate = { name ->
                appData.createPlaylist(name, trackIds)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }

    if (creatingPlaylist) {
        NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            taken = { PlaylistEdits.isNameTaken(data.playlists, it) },
            onConfirm = {
                appData.createPlaylist(it)
                creatingPlaylist = false
            },
            onDismiss = { creatingPlaylist = false },
        )
    }

    renamingPlaylist?.let { playlist ->
        NameDialog(
            title = stringResource(R.string.main_rename_playlist),
            initial = playlist.name,
            taken = { PlaylistEdits.isNameTaken(data.playlists, it, exceptId = playlist.id) },
            onConfirm = {
                appData.renamePlaylist(playlist.id, it)
                renamingId = null
            },
            onDismiss = { renamingId = null },
        )
    }

    dataNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = AppDataStore::noticeShown,
            title = { Text(stringResource(R.string.data_notice_title)) },
            text = {
                val unreadable = stringResource(R.string.data_notice_unreadable)
                val outcome = stringResource(
                    if (notice.restoredFromCopy) R.string.data_notice_restored else R.string.data_notice_reset,
                )
                val setAside = notice.setAsideName?.let { stringResource(R.string.data_notice_set_aside, it) }
                Text(unreadable + outcome + setAside.orEmpty())
            },
            confirmButton = {
                TextButton(onClick = AppDataStore::noticeShown) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }

    identityReport?.let { report ->
        AlertDialog(
            onDismissRequest = LibraryIdentity::reportShown,
            title = { Text(stringResource(R.string.identity_title)) },
            text = {
                val intro = stringResource(R.string.identity_intro)
                val recovered = if (report.recovered > 0) {
                    pluralStringResource(R.plurals.identity_recovered, report.recovered, report.recovered)
                } else {
                    ""
                }
                val missing = if (report.missing > 0) {
                    pluralStringResource(R.plurals.identity_missing, report.missing, report.missing)
                } else {
                    ""
                }
                Text(intro + recovered + missing)
            },
            confirmButton = {
                TextButton(onClick = LibraryIdentity::reportShown) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }

    editingRuleId?.let { id ->
        val genreNames = remember(genres) { genres.filter { it.key != "genre:" }.map { it.title } }
        val folderPaths = remember(library.tracks) { FolderTree.allPaths(library.tracks) }
        RuleListDialog(
            initial = data.ruleLists.firstOrNull { it.id == id } ?: RuleList(id = "", name = ""),
            library = library.tracks,
            genres = genreNames,
            folders = folderPaths,
            onConfirm = {
                appData.saveRuleList(it)
                editingRuleId = null
            },
            onDismiss = { editingRuleId = null },
        )
    }

    data.ruleLists.firstOrNull { it.id == deletingRuleId }?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRuleId = null },
            title = { Text(stringResource(R.string.playlist_delete_title, rule.name)) },
            text = { Text(stringResource(R.string.rule_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        appData.deleteRuleList(rule.id)
                        deletingRuleId = null
                        detail = null
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRuleId = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    deletingPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.playlist_delete_title, playlist.name)) },
            text = { Text(stringResource(R.string.playlist_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        appData.deletePlaylist(playlist.id)
                        deletingId = null
                        detail = null
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.common_cancel)) }
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

