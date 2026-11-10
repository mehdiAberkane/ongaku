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
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val folder: String,
    val extension: String,
    val uri: Uri,
    val artworkUri: Uri,
)

/** Un album ou un artiste : un titre affiché et les morceaux qu'il regroupe. */
data class TrackGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUri: Uri?,
    val tracks: List<Track>,
)

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .build(),
    )
    .build()

private fun List<Track>.mainArtist(): String =
    groupingBy { it.artist }.eachCount().maxBy { it.value }.key

/**
 * Android crée un album par dossier : on regroupe d'abord par identifiant, pour qu'une compilation
 * reste entière, puis on fusionne à l'affichage ceux qui ont le même nom et le même artiste.
 */
fun List<Track>.albums(): List<TrackGroup> = groupBy { it.albumId }
    .values
    .groupBy { tracks -> tracks.first().album.trim().lowercase() to tracks.mainArtist().lowercase() }
    .map { (key, parts) ->
        val sorted = parts.flatten()
            .sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
        TrackGroup(
            key = "album:${key.first}|${key.second}",
            title = sorted.first().album,
            subtitle = parts.first().mainArtist(),
            artworkUri = sorted.first().artworkUri,
            tracks = sorted,
        )
    }
    .sortedBy { it.title.lowercase() }

fun List<Track>.artists(): List<TrackGroup> = groupBy { it.artist }
    .map { (artist, tracks) ->
        val sorted = tracks.sortedWith(
            compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }),
        )
        TrackGroup(
            key = "artist:$artist",
            title = artist,
            subtitle = "${tracks.size} titre${if (tracks.size > 1) "s" else ""}",
            artworkUri = sorted.first().artworkUri,
            tracks = sorted,
        )
    }
    .sortedBy { it.title.lowercase() }

class MusicRepository(private val context: Context) {

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
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
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val artist = cursor.getString(artistColumn)
                val path = cursor.getString(pathColumn).orEmpty()
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Sans titre",
                    artist = artist?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                        ?: "Artiste inconnu",
                    album = cursor.getString(albumColumn) ?: "Album inconnu",
                    albumId = albumId,
                    // MediaStore encode le numéro de disque dans les milliers.
                    trackNumber = cursor.getInt(trackColumn),
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
                )
            }
        }
        tracks
    }

    private companion object {
        const val INTERNAL_STORAGE_PREFIX = "/storage/emulated/0/"
        val ALBUM_ART_URI: Uri = "content://media/external/audio/albumart".toUri()
    }
}
