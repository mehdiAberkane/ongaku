package fr.devsecopsforce.ongaku.widget

import fr.devsecopsforce.ongaku.data.AppData

/** Une image possible pour le widget : un fichier d'image de fond d'Ongaku, ou une pochette. */
sealed interface WidgetImage {
    /** Image choisie dans Ongaku, rangée dans `files/backgrounds/`. */
    data class Background(val name: String) : WidgetImage

    /** Pochette de l'album (adresse MediaStore). */
    data class Artwork(val uri: String) : WidgetImage

    companion object {
        /**
         * Images à essayer, dans l'ordre : celle choisie pour le widget, celle du morceau, la pochette,
         * puis l'image par défaut de l'écran de lecture et celle de l'application. La première qui se
         * charge est affichée ; si aucune ne se charge, le widget garde son image de base.
         */
        fun candidates(data: AppData, trackId: Long?, artworkUri: String?): List<WidgetImage> =
            listOfNotNull(
                data.widgetBackground?.let(::Background),
                trackId?.let(data.trackBackgrounds::get)?.let(::Background),
                artworkUri?.let(::Artwork),
                data.playerBackground?.let(::Background),
                data.appBackground?.let(::Background),
            ).distinct()
    }
}
