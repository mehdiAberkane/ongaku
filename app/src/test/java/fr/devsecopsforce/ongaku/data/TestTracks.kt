package fr.devsecopsforce.ongaku.data

import android.net.Uri

/** Morceau de test : seuls les champs utiles au test sont à préciser. */
internal fun track(
    id: Long,
    title: String = "Titre $id",
    artist: String = "Artiste",
    album: String = "Album",
    albumId: Long = 1,
    trackNumber: Int = 0,
    year: Int = 0,
    durationMs: Long = 180_000,
    dateAddedSeconds: Long = 0,
    folder: String = "Music",
    extension: String = "mp3",
    relativePath: String = "$folder/$title.$extension",
    sizeBytes: Long = 1_000L + id,
    albumArtist: String = "",
    artistMissing: Boolean = false,
) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    trackNumber = trackNumber,
    year = year,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    dateAddedSeconds = dateAddedSeconds,
    folder = folder,
    extension = extension,
    uri = Uri.EMPTY,
    artworkUri = Uri.EMPTY,
    relativePath = relativePath,
    albumArtist = albumArtist,
    artistMissing = artistMissing,
)

/** Le même fichier, auquel Android a donné un autre identifiant. */
internal fun Track.renumbered(newId: Long) = copy(id = newId)
