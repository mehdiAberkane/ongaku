package fr.devsecopsforce.ongaku.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
import fr.devsecopsforce.ongaku.data.BackgroundImage
import fr.devsecopsforce.ongaku.data.ImportLimits
import fr.devsecopsforce.ongaku.data.LibraryIdentity
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.PlaylistBackup
import fr.devsecopsforce.ongaku.data.PlaylistEdits
import fr.devsecopsforce.ongaku.data.SafeFile
import fr.devsecopsforce.ongaku.data.backgroundNames
import fr.devsecopsforce.ongaku.data.missingTrackBackgrounds
import fr.devsecopsforce.ongaku.data.orphanBackgroundFiles
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.widget.WidgetDisplay
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface BackgroundTarget {
    data object App : BackgroundTarget
    data object Player : BackgroundTarget
    data class Track(val id: Long) : BackgroundTarget
    data object Widget : BackgroundTarget
}

/**
 * Listes, favoris et images de fond : une seule instance pour tout le processus, comme
 * [fr.devsecopsforce.ongaku.data.AppearanceStore]. L'écran principal et celui du verrouillage
 * partagent ainsi le même état : une modification faite dans l'un ne peut pas être écrasée par
 * l'état en retard de l'autre.
 *
 * Les modifications tournent dans [scope], qui vit autant que le processus : fermer un écran
 * n'interrompt pas un enregistrement en cours.
 */
class AppDataRepository private constructor(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = AppDataStore(app)
    private val backgroundsDir = File(app.filesDir, "backgrounds")
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    /** Correction des identifiants ([LibraryIdentity]) déjà prise en compte dans [_state]. */
    private var loadedGeneration = -1

    /** À appeler sous [AppDataStore.lock]. */
    private fun reloadIfCorrected() {
        val generation = LibraryIdentity.generation.value
        if (generation != loadedGeneration) {
            _state.value = store.load()
            loadedGeneration = generation
        }
    }

    private val loading = scope.launch {
        synchronized(AppDataStore.lock) { reloadIfCorrected() }
        deleteOrphanBackgrounds()
    }

    init {
        // Le fichier a été corrigé par la mise à jour des identifiants : on le relit.
        scope.launch {
            LibraryIdentity.generation.collect {
                loading.join()
                mutex.withLock { synchronized(AppDataStore.lock) { reloadIfCorrected() } }
            }
        }
    }

    fun backgroundFile(name: String?): File? = name?.let { File(backgroundsDir, it) }

    private fun mutate(transform: (AppData) -> AppData) {
        scope.launch { applyChange(transform) }
    }

    private suspend fun applyChange(transform: (AppData) -> AppData) {
        loading.join()
        mutex.withLock {
            val saved = synchronized(AppDataStore.lock) {
                // Jamais d'écriture à partir d'identifiants périmés.
                reloadIfCorrected()
                val updated = transform(_state.value)
                _state.value = updated
                try {
                    store.save(updated)
                    true
                } catch (e: IOException) {
                    false
                }
            }
            if (!saved) toast(R.string.data_save_failed)
        }
    }

    /** Retire des favoris les morceaux qui ne sont plus sur le téléphone. */
    fun removeMissingFavorites(existing: Set<Long>) = mutate { data ->
        data.copy(favorites = data.favorites.filterTo(HashSet()) { it in existing })
    }

    /** Retire d'une liste les morceaux qui ne sont plus sur le téléphone. */
    fun removeMissingFromPlaylist(id: String, existing: Set<Long>) = updatePlaylist(id) { playlist ->
        playlist.copy(trackIds = playlist.trackIds.filter { it in existing })
    }

    private suspend fun toast(@StringRes message: Int, vararg args: Any) = withContext(Dispatchers.Main) {
        Toast.makeText(app, app.getString(message, *args), Toast.LENGTH_SHORT).show()
    }

    fun toggleFavorite(trackId: Long) = mutate { data ->
        data.copy(
            favorites = if (trackId in data.favorites) {
                data.favorites - trackId
            } else {
                data.favorites + trackId
            },
        )
    }

    /**
     * Création ou renommage : refusé si une autre liste porte déjà ce nom (l'import fusionne les
     * listes par nom). Vérifié ici aussi, l'écran pouvant afficher un état en retard.
     */
    private fun mutateNamed(name: String, exceptId: String?, transform: (AppData) -> AppData) {
        scope.launch {
            var taken = false
            applyChange { data ->
                if (PlaylistEdits.isNameTaken(data.playlists, name, exceptId)) {
                    taken = true
                    data
                } else {
                    transform(data)
                }
            }
            if (taken) toast(R.string.data_playlist_name_exists, name.trim())
        }
    }

    /** Nouvelle liste, vide ou déjà remplie (un morceau, un album, la file d'attente…). */
    fun createPlaylist(name: String, trackIds: List<Long> = emptyList()) = mutateNamed(name, null) { data ->
        data.copy(
            playlists = data.playlists + Playlist(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                trackIds = trackIds.distinct(),
            ),
        )
    }

    fun renamePlaylist(id: String, name: String) = mutateNamed(name, id) { data ->
        data.copy(playlists = data.playlists.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePlaylist(id: String) = mutate { data ->
        data.copy(playlists = data.playlists.filterNot { it.id == id })
    }

    /** Ajoute à la fin de la liste les morceaux qui n'y sont pas encore. */
    fun addToPlaylist(id: String, trackIds: List<Long>) = updatePlaylist(id) { playlist ->
        playlist.copy(trackIds = PlaylistEdits.append(playlist.trackIds, trackIds))
    }

    /** [visibleOrder] : nouvel ordre des morceaux affichés ; les morceaux masqués ne bougent pas. */
    fun reorderPlaylist(id: String, visibleOrder: List<Long>) = updatePlaylist(id) { playlist ->
        playlist.copy(trackIds = PlaylistEdits.reorderVisible(playlist.trackIds, visibleOrder))
    }

    fun removeFromPlaylist(id: String, trackId: Long) = updatePlaylist(id) { playlist ->
        playlist.copy(trackIds = playlist.trackIds - trackId)
    }

    private fun updatePlaylist(id: String, transform: (Playlist) -> Playlist) = mutate { data ->
        data.copy(playlists = data.playlists.map { if (it.id == id) transform(it) else it })
    }

    fun exportBackup(target: Uri, tracks: List<Track>) {
        scope.launch {
            loading.join()
            try {
                val text = PlaylistBackup.serialize(_state.value, tracks)
                val output = app.contentResolver.openOutputStream(target)
                    ?: throw IOException("destination inaccessible")
                output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast(R.string.backup_exported)
            } catch (e: IOException) {
                toast(R.string.backup_export_failed)
            } catch (e: SecurityException) {
                toast(R.string.backup_export_failed)
            }
        }
    }

    /** Fusionne la sauvegarde avec l'existant : un import n'efface jamais rien. */
    fun importBackup(source: Uri, tracks: List<Track>) {
        scope.launch {
            val imported = try {
                val input = app.contentResolver.openInputStream(source)
                    ?: throw IOException("fichier illisible")
                val bytes = input.use { ImportLimits.readLimited(it, ImportLimits.MAX_BACKUP_BYTES) }
                if (bytes == null) {
                    toast(R.string.backup_too_large)
                    return@launch
                }
                PlaylistBackup.parse(String(bytes, Charsets.UTF_8), tracks)
            } catch (e: JSONException) {
                toast(R.string.backup_not_ongaku)
                return@launch
            } catch (e: IOException) {
                toast(R.string.backup_read_failed)
                return@launch
            } catch (e: SecurityException) {
                toast(R.string.backup_read_failed)
                return@launch
            }
            applyChange { data ->
                PlaylistBackup.merge(data, imported) { UUID.randomUUID().toString() }
            }
            val missing = if (imported.missingTracks > 0) {
                app.getString(R.string.backup_import_missing, imported.missingTracks)
            } else {
                ""
            }
            toast(R.string.backup_import_done, imported.playlists.size, missing)
        }
    }

    /**
     * Le sélecteur ne donne qu'un accès temporaire à l'image : on en garde une copie privée. L'image
     * est décodée (un fichier qui n'en est pas une est refusé), redressée et réduite à la taille de
     * l'écran : une photo de 50 Mo ne s'installe pas telle quelle dans le stockage de l'application.
     */
    fun setBackground(target: BackgroundTarget, source: Uri) {
        scope.launch {
            val name = "${UUID.randomUUID()}.img"
            val saved = try {
                val bytes = BackgroundImage.prepare(app, source)
                if (bytes == null) {
                    toast(R.string.background_not_image)
                    false
                } else {
                    backgroundsDir.mkdirs()
                    SafeFile.write(File(backgroundsDir, name), bytes)
                    true
                }
            } catch (e: IOException) {
                toast(R.string.background_copy_failed)
                false
            } catch (e: SecurityException) {
                toast(R.string.background_read_failed)
                false
            } catch (e: OutOfMemoryError) {
                toast(R.string.background_too_large)
                false
            }
            if (saved) replaceBackground(target, name)
        }
    }

    /** Retire les images des morceaux qui ne sont plus sur le téléphone, et leurs fichiers. */
    fun removeMissingBackgrounds(existing: Set<Long>) {
        scope.launch {
            var removed = emptyMap<Long, String>()
            var stillUsed = emptySet<String>()
            applyChange { data ->
                removed = data.missingTrackBackgrounds(existing)
                data.copy(trackBackgrounds = data.trackBackgrounds - removed.keys).also {
                    stillUsed = it.backgroundNames()
                }
            }
            removed.values.filterNot { it in stillUsed }.forEach { backgroundFile(it)?.delete() }
            WidgetDisplay.render(app)
        }
    }

    /** Fichiers d'images que plus rien ne désigne : voir [orphanBackgroundFiles]. */
    private fun deleteOrphanBackgrounds() {
        val files = backgroundsDir.listFiles()?.map { it.name to it.lastModified() } ?: return
        val orphans = synchronized(AppDataStore.lock) {
            orphanBackgroundFiles(_state.value, files, System.currentTimeMillis(), ORPHAN_MIN_AGE_MS)
        }
        orphans.forEach { File(backgroundsDir, it).delete() }
    }

    fun removeBackground(target: BackgroundTarget) {
        scope.launch { replaceBackground(target, null) }
    }

    private suspend fun replaceBackground(target: BackgroundTarget, name: String?) {
        var previous: String? = null
        applyChange { data ->
            when (target) {
                BackgroundTarget.App -> {
                    previous = data.appBackground
                    data.copy(appBackground = name)
                }
                BackgroundTarget.Player -> {
                    previous = data.playerBackground
                    data.copy(playerBackground = name)
                }
                is BackgroundTarget.Track -> {
                    previous = data.trackBackgrounds[target.id]
                    data.copy(
                        trackBackgrounds = if (name == null) {
                            data.trackBackgrounds - target.id
                        } else {
                            data.trackBackgrounds + (target.id to name)
                        },
                    )
                }
                BackgroundTarget.Widget -> {
                    previous = data.widgetBackground
                    data.copy(widgetBackground = name)
                }
            }
        }
        backgroundFile(previous)?.delete()
        // Toutes ces images peuvent apparaître dans le widget : il se redessine aussitôt.
        WidgetDisplay.render(app)
    }

    companion object {
        /** Une image en cours d'enregistrement n'est inscrite dans les données qu'à la fin. */
        private const val ORPHAN_MIN_AGE_MS = 60L * 60 * 1000

        @Volatile
        private var instance: AppDataRepository? = null

        fun get(context: Context): AppDataRepository = instance ?: synchronized(this) {
            instance ?: AppDataRepository(context.applicationContext as Application).also { instance = it }
        }
    }
}
