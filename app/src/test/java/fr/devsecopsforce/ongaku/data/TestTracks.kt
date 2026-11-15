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
) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    trackNumber = trackNumber,
    year = year,
    durationMs = durationMs,
    sizeBytes = 0,
    dateAddedSeconds = dateAddedSeconds,
    folder = folder,
    extension = extension,
    uri = Uri.EMPTY,
    artworkUri = Uri.EMPTY,
)
