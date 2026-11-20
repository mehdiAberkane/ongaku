package fr.devsecopsforce.ongaku.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
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

class AppDataViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppDataStore(application)
    private val backgroundsDir = File(application.filesDir, "backgrounds")
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

    private val loading = viewModelScope.launch(Dispatchers.IO) {
        synchronized(AppDataStore.lock) { reloadIfCorrected() }
        deleteOrphanBackgrounds()
    }

    init {
        // Le fichier a été corrigé pendant que l'écran était ouvert : on le relit.
        viewModelScope.launch(Dispatchers.IO) {
            LibraryIdentity.generation.collect {
                loading.join()
                mutex.withLock { synchronized(AppDataStore.lock) { reloadIfCorrected() } }
            }
        }
    }

    fun backgroundFile(name: String?): File? = name?.let { File(backgroundsDir, it) }

    private fun mutate(transform: (AppData) -> AppData) {
        viewModelScope.launch(Dispatchers.IO) { applyChange(transform) }
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
            if (!saved) toast("Enregistrement impossible.")
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

    private suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
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
        viewModelScope.launch(Dispatchers.IO) {
            var taken = false
            applyChange { data ->
                if (PlaylistEdits.isNameTaken(data.playlists, name, exceptId)) {
                    taken = true
                    data
                } else {
                    transform(data)
                }
            }
            if (taken) toast("Une liste « ${name.trim()} » existe déjà.")
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
        viewModelScope.launch(Dispatchers.IO) {
            loading.join()
            try {
                val text = PlaylistBackup.serialize(_state.value, tracks)
                val output = getApplication<Application>().contentResolver.openOutputStream(target)
                    ?: throw IOException("destination inaccessible")
                output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast("Sauvegarde exportée.")
            } catch (e: IOException) {
                toast("Export impossible.")
            } catch (e: SecurityException) {
                toast("Export impossible.")
            }
        }
    }

    /** Fusionne la sauvegarde avec l'existant : un import n'efface jamais rien. */
    fun importBackup(source: Uri, tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = try {
                val input = getApplication<Application>().contentResolver.openInputStream(source)
                    ?: throw IOException("fichier illisible")
                val bytes = input.use { ImportLimits.readLimited(it, ImportLimits.MAX_BACKUP_BYTES) }
                if (bytes == null) {
                    toast("Fichier trop lourd pour une sauvegarde Ongaku (plus de 5 Mo).")
                    return@launch
                }
                PlaylistBackup.parse(String(bytes, Charsets.UTF_8), tracks)
            } catch (e: JSONException) {
                toast("Ce fichier n'est pas une sauvegarde Ongaku.")
                return@launch
            } catch (e: IOException) {
                toast("Lecture du fichier impossible.")
                return@launch
            } catch (e: SecurityException) {
                toast("Lecture du fichier impossible.")
                return@launch
            }
            applyChange { data ->
                PlaylistBackup.merge(data, imported) { UUID.randomUUID().toString() }
            }
            val missing = if (imported.missingTracks > 0) {
                ", ${imported.missingTracks} morceau(x) introuvable(s)"
            } else {
                ""
            }
            toast("Import terminé : ${imported.playlists.size} liste(s)$missing.")
        }
    }

    /**
     * Le sélecteur ne donne qu'un accès temporaire à l'image : on en garde une copie privée. L'image
     * est décodée (un fichier qui n'en est pas une est refusé), redressée et réduite à la taille de
     * l'écran : une photo de 50 Mo ne s'installe pas telle quelle dans le stockage de l'application.
     */
    fun setBackground(target: BackgroundTarget, source: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "${UUID.randomUUID()}.img"
            val saved = try {
                val bytes = BackgroundImage.prepare(getApplication(), source)
                if (bytes == null) {
                    toast("Ce fichier n'est pas une image lisible.")
                    false
                } else {
                    backgroundsDir.mkdirs()
                    SafeFile.write(File(backgroundsDir, name), bytes)
                    true
                }
            } catch (e: IOException) {
                toast("Impossible de copier cette image.")
                false
            } catch (e: SecurityException) {
                toast("Impossible de lire cette image.")
                false
            } catch (e: OutOfMemoryError) {
                toast("Image trop grande.")
                false
            }
            if (saved) replaceBackground(target, name)
        }
    }

    /** Retire les images des morceaux qui ne sont plus sur le téléphone, et leurs fichiers. */
    fun removeMissingBackgrounds(existing: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            var removed = emptyMap<Long, String>()
            var stillUsed = emptySet<String>()
            applyChange { data ->
                removed = data.missingTrackBackgrounds(existing)
                data.copy(trackBackgrounds = data.trackBackgrounds - removed.keys).also {
                    stillUsed = it.backgroundNames()
                }
            }
            removed.values.filterNot { it in stillUsed }.forEach { backgroundFile(it)?.delete() }
            WidgetDisplay.render(getApplication())
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
        viewModelScope.launch(Dispatchers.IO) { replaceBackground(target, null) }
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
        WidgetDisplay.render(getApplication())
    }

    private companion object {
        /** Une image en cours d'enregistrement n'est inscrite dans les données qu'à la fin. */
        const val ORPHAN_MIN_AGE_MS = 60L * 60 * 1000
    }
}
