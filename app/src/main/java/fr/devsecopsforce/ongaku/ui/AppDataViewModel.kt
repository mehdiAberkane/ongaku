package fr.devsecopsforce.ongaku.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.PlaylistBackup
import fr.devsecopsforce.ongaku.data.Track
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
}

class AppDataViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppDataStore(application)
    private val backgroundsDir = File(application.filesDir, "backgrounds")
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    private val loading = viewModelScope.launch(Dispatchers.IO) { _state.value = store.load() }

    fun backgroundFile(name: String?): File? = name?.let { File(backgroundsDir, it) }

    private fun mutate(transform: (AppData) -> AppData) {
        viewModelScope.launch(Dispatchers.IO) { applyChange(transform) }
    }

    private suspend fun applyChange(transform: (AppData) -> AppData) {
        loading.join()
        mutex.withLock {
            val updated = transform(_state.value)
            _state.value = updated
            try {
                store.save(updated)
            } catch (e: IOException) {
                toast("Enregistrement impossible.")
            }
        }
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

    fun createPlaylist(name: String, firstTrackId: Long? = null) = mutate { data ->
        data.copy(
            playlists = data.playlists + Playlist(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                trackIds = listOfNotNull(firstTrackId),
            ),
        )
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylist(id) { it.copy(name = name.trim()) }

    fun deletePlaylist(id: String) = mutate { data ->
        data.copy(playlists = data.playlists.filterNot { it.id == id })
    }

    fun addToPlaylist(id: String, trackId: Long) = updatePlaylist(id) { playlist ->
        if (trackId in playlist.trackIds) {
            playlist
        } else {
            playlist.copy(trackIds = playlist.trackIds + trackId)
        }
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
                val text = input.use { String(it.readBytes(), Charsets.UTF_8) }
                PlaylistBackup.parse(text, tracks)
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
            applyChange { data -> merge(data, imported) }
            val missing = if (imported.missingTracks > 0) {
                ", ${imported.missingTracks} morceau(x) introuvable(s)"
            } else {
                ""
            }
            toast("Import terminé : ${imported.playlists.size} liste(s)$missing.")
        }
    }

    private fun merge(data: AppData, imported: PlaylistBackup.Imported): AppData {
        val playlists = data.playlists.toMutableList()
        imported.playlists.forEach { (name, ids) ->
            val index = playlists.indexOfFirst { it.name.trim().equals(name.trim(), ignoreCase = true) }
            if (index >= 0) {
                playlists[index] = playlists[index].let {
                    it.copy(trackIds = (it.trackIds + ids).distinct())
                }
            } else {
                playlists += Playlist(UUID.randomUUID().toString(), name.trim(), ids)
            }
        }
        return data.copy(favorites = data.favorites + imported.favorites, playlists = playlists)
    }

    /** Le sélecteur ne donne qu'un accès temporaire à l'image : on en garde une copie privée. */
    fun setBackground(target: BackgroundTarget, source: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "${UUID.randomUUID()}.img"
            val copied = try {
                backgroundsDir.mkdirs()
                val input = getApplication<Application>().contentResolver.openInputStream(source)
                    ?: throw IOException("image illisible")
                input.use { from ->
                    File(backgroundsDir, name).outputStream().use { to -> from.copyTo(to) }
                }
                true
            } catch (e: IOException) {
                toast("Impossible de copier cette image.")
                false
            } catch (e: SecurityException) {
                toast("Impossible de lire cette image.")
                false
            }
            if (copied) replaceBackground(target, name)
        }
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
            }
        }
        backgroundFile(previous)?.delete()
    }
}
