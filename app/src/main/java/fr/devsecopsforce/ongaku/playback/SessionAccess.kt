package fr.devsecopsforce.ongaku.playback

/**
 * Qui peut se connecter à la session de lecture : Ongaku lui-même (écran, widgets, lecture
 * automatique, notification), les contrôleurs de confiance pour Android, c'est-à-dire l'interface
 * du système (notification, écran de verrouillage, reprise), le Bluetooth et les applications
 * autorisées à lire les notifications (souvent celles des montres connectées), et la voiture :
 * Android Auto et l'Assistant Google, à condition d'être installés avec le système. Les autres
 * applications ne peuvent ni piloter la lecture ni lire le titre en cours et la file.
 */
object SessionAccess {
    /**
     * Android Auto (et son outil de bureau, qui passe par lui) et l'Assistant, pour les demandes
     * vocales. Le nom seul ne prouve rien : une application installée à part pourrait le prendre sur
     * un téléphone qui n'a pas la vraie ; seules les applications système sont acceptées.
     */
    val CAR_PACKAGES = setOf(
        "com.google.android.projection.gearhead",
        "com.google.android.googlequicksearchbox",
    )

    fun allowed(controllerPackage: String, ownPackage: String, trusted: Boolean, systemApp: Boolean = false): Boolean =
        trusted || controllerPackage == ownPackage || (controllerPackage in CAR_PACKAGES && systemApp)
}