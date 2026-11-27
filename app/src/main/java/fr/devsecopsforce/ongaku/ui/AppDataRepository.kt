package fr.devsecopsforce.ongaku.ui

import android.app.Application
import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import fr.devsecopsforce.ongaku.data.AutoBackupRules
import fr.devsecopsforce.ongaku.data.AutoBackupSettings
import fr.devsecopsforce.ongaku.data.LibraryCache
import java.io.OutputStream
import java.time.LocalDateTime
import android.graphics.BitmapFactory
import androidx.core.content.edit
import fr.devsecopsforce.ongaku.data.AppearanceStore
import fr.devsecopsforce.ongaku.data.BackupArchive
import fr.devsecopsforce.ongaku.data.LibraryFilterStore
import fr.devsecopsforce.ongaku.data.PlayCounts
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
import fr.devsecopsforce.ongaku.data.BackgroundImage
import fr.devsecopsforce.ongaku.data.LibraryIdentity
import fr.devsecopsforce.ongaku.data.ListeningHistory
import fr.devsecopsforce.ongaku.data.Playlist
import fr.devsecopsforce.ongaku.data.PlaylistBackup
import fr.devsecopsforce.ongaku.data.PlaylistEdits
import fr.devsecopsforce.ongaku.data.RuleList
import fr.devsecopsforce.ongaku.data.SafeFile
import fr.devsecopsforce.ongaku.data.SelectionRules
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
    private val autoBackupMutex = Mutex()

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

    /** Cœur de la barre de sélection : voir [SelectionRules.favoritesAfter]. */
    fun toggleFavorites(selected: List<Long>) = mutate { data ->
        data.copy(favorites = SelectionRules.favoritesAfter(data.favorites, selected))
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

    /** Nouvelle liste par règle si [rule] n'a pas encore d'identifiant, sinon modification. */
    fun saveRuleList(rule: RuleList) = mutate { data ->
        if (rule.id.isEmpty()) {
            data.copy(ruleLists = data.ruleLists + rule.copy(id = UUID.randomUUID().toString()))
        } else {
            data.copy(ruleLists = data.ruleLists.map { if (it.id == rule.id) rule else it })
        }
    }

    fun deleteRuleList(id: String) = mutate { data ->
        data.copy(ruleLists = data.ruleLists.filterNot { it.id == id })
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
                val output = app.contentResolver.openOutputStream(target)
                    ?: throw IOException("destination inaccessible")
                output.use { writeBackup(it, tracks) }
                toast(R.string.backup_exported)
            } catch (e: IOException) {
                toast(R.string.backup_export_failed)
            } catch (e: SecurityException) {
                toast(R.string.backup_export_failed)
            }
        }
    }

    /** Sauvegarde automatique, si elle est due ; voir [AutoBackupRules]. */
    fun autoBackupIfDue() {
        scope.launch { runAutoBackup(force = false) }
    }

    /** « Sauvegarder maintenant » dans les réglages. */
    fun autoBackupNow() {
        scope.launch {
            val done = runAutoBackup(force = true)
            toast(if (done) R.string.auto_backup_done else R.string.auto_backup_failed)
        }
    }

    /**
     * Écrit une archive datée dans le dossier choisi, puis supprime les plus anciennes sauvegardes
     * d'Ongaku au-delà du nombre gardé. Rend vrai si l'archive est écrite.
     */
    private suspend fun runAutoBackup(force: Boolean): Boolean = autoBackupMutex.withLock {
        loading.join()
        val settings = AutoBackupSettings(app)
        val folder = settings.folderUri?.toUri() ?: return@withLock false
        val now = System.currentTimeMillis()
        if (!force && !AutoBackupRules.isDue(settings.enabled, true, settings.lastRunMs, now, settings.intervalDays)) {
            return@withLock false
        }
        val resolver = app.contentResolver
        try {
            val tracks = LibraryCache.tracks(app)
            val dir = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val name = AutoBackupRules.fileName(LocalDateTime.now())
            val document = DocumentsContract.createDocument(resolver, dir, "application/zip", name)
                ?: throw IOException("création impossible")
            val output = resolver.openOutputStream(document) ?: throw IOException("écriture impossible")
            output.use { writeBackup(it, tracks) }
            // Rotation : seulement les fichiers d'Ongaku, jamais ceux du dossier qui ne sont pas à lui.
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getDocumentId(dir))
            val existing = HashMap<String, String>()
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) existing[cursor.getString(1)] = cursor.getString(0)
            }
            AutoBackupRules.toDelete(existing.keys.toList(), settings.keep).forEach { old ->
                existing[old]?.let { id ->
                    runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
                }
            }
            settings.lastRunMs = now
            settings.lastFailed = false
            true
        } catch (e: Exception) {
            // Dossier supprimé, accès retiré, stockage plein : signalé dans les réglages.
            Log.w("OngakuBackup", "sauvegarde automatique impossible", e)
            settings.lastFailed = true
            false
        }
    }

    /** Archive complète : listes, écoutes, réglages et images de fond. */
    private fun writeBackup(output: OutputStream, tracks: List<Track>) {
        val data = _state.value
        val history = ListeningHistory.get(app)
        val counts = PlayCounts(app)
        // Images de fond : copiées telles quelles dans l'archive, une seule fois chacune.
        val images = LinkedHashMap<String, File>()
        fun image(name: String?): String? {
            val file = backgroundFile(name)?.takeIf { it.isFile } ?: return null
            return (BackupArchive.IMAGES_DIR + file.name).also { images[it] = file }
        }
        val extras = PlaylistBackup.Extras(
            playCounts = counts.all(),
            lastPlayed = counts.lastPlayed(),
            // Seulement les réglages qui existent : une section vide ne créerait rien d'utile.
            settings = PlaylistBackup.SETTINGS_FILES
                .associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all }
                .filterValues { it.isNotEmpty() },
            appImage = image(data.appBackground),
            playerImage = image(data.playerBackground),
            widgetImage = image(data.widgetBackground),
            trackImages = data.trackBackgrounds.mapNotNull { (id, name) -> image(name)?.let { id to it } }.toMap(),
        )
        val json = PlaylistBackup.serialize(data, tracks, history.plays.value, history.days.value, extras)
        BackupArchive.write(output, json, images)
    }

    /** Fusionne la sauvegarde avec l'existant : un import n'efface jamais rien. */
    fun importBackup(source: Uri, tracks: List<Track>) {
        scope.launch {
            val contents: BackupArchive.Contents
            val imported = try {
                val input = app.contentResolver.openInputStream(source)
                    ?: throw IOException("fichier illisible")
                contents = input.use { BackupArchive.read(it) }
                PlaylistBackup.parse(contents.json, tracks)
            } catch (e: BackupArchive.TooLargeException) {
                toast(R.string.backup_too_large)
                return@launch
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
            // Seules les images qui trouveront une place libre sont copiées, et seulement si ce sont
            // bien des images.
            val saved = HashMap<String, String>()
            PlaylistBackup.neededImages(_state.value, imported).forEach { path ->
                val bytes = contents.images[path] ?: return@forEach
                if (!isImage(bytes)) return@forEach
                val name = "${UUID.randomUUID()}.img"
                try {
                    backgroundsDir.mkdirs()
                    SafeFile.write(File(backgroundsDir, name), bytes)
                    saved[path] = name
                } catch (e: IOException) {
                    // Image perdue, le reste de l'import continue.
                }
            }
            applyChange { data ->
                val merged = PlaylistBackup.merge(data, imported) { UUID.randomUUID().toString() }
                PlaylistBackup.mergeImages(merged, imported, saved)
            }
            ListeningHistory.get(app).merge(imported.history, imported.listeningDays)
            PlayCounts(app).mergeMax(imported.playCounts)
            restoreSettings(imported.settings)
            WidgetDisplay.render(app)
            val missing = if (imported.missingTracks > 0) {
                app.getString(R.string.backup_import_missing, imported.missingTracks)
            } else {
                ""
            }
            toast(R.string.backup_import_done, imported.playlists.size, missing)
        }
    }

    /**
     * Réglages d'une sauvegarde : repris tels quels, sauf le temps total d'écoute, dont on garde le
     * plus grand (réimporter sur le même téléphone ne le double pas). Les réglages déjà chargés en
     * mémoire (apparence, filtres) sont relus aussitôt.
     */
    private fun restoreSettings(settings: Map<String, Map<String, Any>>) {
        if (settings.isEmpty()) return
        settings.forEach { (name, values) ->
            if (name !in PlaylistBackup.SETTINGS_FILES || values.isEmpty()) return@forEach
            val prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefs.edit(commit = true) {
                values.forEach { (key, value) ->
                    if (name == SETTINGS_PREFS && key == LISTENED_KEY) {
                        if (value is Long && value > prefs.getLong(key, 0L)) putLong(key, value)
                        return@forEach
                    }
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }
        }
        AppearanceStore.get(app).reload()
        LibraryFilterStore.get(app).reload()
    }

    /** Une image que le téléphone sait décoder : un autre fichier glissé dans l'archive est ignoré. */
    private fun isImage(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth > 0 && options.outHeight > 0
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
        private const val SETTINGS_PREFS = "ongaku_settings"
        private const val LISTENED_KEY = "listened_ms"

        @Volatile
        private var instance: AppDataRepository? = null

        fun get(context: Context): AppDataRepository = instance ?: synchronized(this) {
            instance ?: AppDataRepository(context.applicationContext as Application).also { instance = it }
        }
    }
}
