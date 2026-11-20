package fr.devsecopsforce.ongaku.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val trackNumber: Int,
    /** 0 si l'année n'est pas renseignée. */
    val year: Int = 0,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val folder: String,
    val extension: String,
    val uri: Uri,
    val artworkUri: Uri,
    /** Chemin du fichier depuis la racine du stockage : reconnaît le morceau si Android change son identifiant. */
    val relativePath: String = "",
    /** Artiste de l'album (tag « album artist ») ; vide s'il n'est pas renseigné. */
    val albumArtist: String = "",
) {
    /** Numéro de disque : MediaStore l'encode dans les milliers du numéro de piste ; 0 s'il manque. */
    val discNumber: Int get() = trackNumber / 1000
}

fun Track.toMediaItem(): MediaItem = mediaItem(id, title, artist, album, artworkUri)

/** Élément de lecture d'un morceau de MediaStore, à partir de son identifiant et de ses tags. */
fun mediaItem(id: Long, title: String, artist: String, album: String, artworkUri: Uri?): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build(),
        )
        .build()

class MusicRepository(private val context: Context) {

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            ALBUM_ARTIST,
        )
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            // Android classe certains fichiers de sauvegarde en musique : ils ont une durée nulle.
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumArtistColumn = cursor.getColumnIndex(ALBUM_ARTIST)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val artist = cursor.getString(artistColumn)
                val path = cursor.getString(pathColumn).orEmpty()
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Sans titre",
                    artist = artist?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                        ?: UNKNOWN_ARTIST,
                    album = cursor.getString(albumColumn) ?: "Album inconnu",
                    albumId = albumId,
                    // MediaStore encode le numéro de disque dans les milliers.
                    trackNumber = cursor.getInt(trackColumn),
                    year = cursor.getInt(yearColumn),
                    durationMs = cursor.getLong(durationColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    dateAddedSeconds = cursor.getLong(dateAddedColumn),
                    folder = path.substringBeforeLast('/', "").removePrefix(INTERNAL_STORAGE_PREFIX),
                    extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(),
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ),
                    artworkUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId),
                    relativePath = path.removePrefix(INTERNAL_STORAGE_PREFIX),
                    albumArtist = albumArtistColumn.takeIf { it >= 0 }
                        ?.let(cursor::getString)
                        ?.trim()
                        ?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                        .orEmpty(),
                )
            }
        }
        // Android a pu renuméroter ses morceaux : listes, favoris et compteurs sont recalés ici.
        LibraryIdentity.check(context, tracks)
        tracks
    }

    private companion object {
        const val INTERNAL_STORAGE_PREFIX = "/storage/emulated/0/"

        /** Colonne présente depuis longtemps, mais sa constante publique date d'Android 11. */
        const val ALBUM_ARTIST = "album_artist"
        val ALBUM_ART_URI: Uri = "content://media/external/audio/albumart".toUri()
    }
}
