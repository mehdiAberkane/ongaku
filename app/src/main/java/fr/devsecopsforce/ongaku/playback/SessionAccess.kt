package fr.devsecopsforce.ongaku.playback

/**
 * Qui peut se connecter à la session de lecture : Ongaku lui-même (écran, widgets, lecture
 * automatique, notification) et les contrôleurs de confiance pour Android, c'est-à-dire l'interface
 * du système (notification, écran de verrouillage, reprise), le Bluetooth, et les applications
 * autorisées à lire les notifications (souvent celles des montres connectées). Les autres
 * applications ne peuvent ni piloter la lecture ni lire le titre en cours et la file.
 */
object SessionAccess {
    fun allowed(controllerPackage: String, ownPackage: String, trusted: Boolean): Boolean =
        trusted || controllerPackage == ownPackage
}
