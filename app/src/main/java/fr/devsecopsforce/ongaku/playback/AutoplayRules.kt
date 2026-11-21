package fr.devsecopsforce.ongaku.playback

/**
 * Décisions de la lecture automatique en Bluetooth, sans Android : [BluetoothAutoplayReceiver] et
 * [AutoplayGuard] relèvent l'état et appliquent ce qui est décidé ici.
 *
 * Un autoradio démarre plus lentement que le téléphone : la lecture lancée à la connexion peut être
 * perdue (sortie audio pas encore prête) ou arrêtée par l'autoradio qui envoie « pause » en
 * s'allumant. La lecture attend donc la vraie sortie audio, puis reste surveillée quelques dizaines
 * de secondes, et reprend si elle s'est arrêtée sans que l'utilisateur l'ait demandé.
 */
object AutoplayRules {
    /**
     * Attente maximale de la sortie audio Bluetooth après la connexion (dans la voiture de test, elle
     * arrive une demi-seconde après). Android laisse 10 s à un récepteur : on reste en dessous.
     */
    const val ROUTE_TIMEOUT_MS = 6_000L
    const val ROUTE_POLL_MS = 250L

    /** Relevés après le lancement : couvre le démarrage d'un autoradio lent. */
    val CHECKS_MS = listOf(3_000L, 8_000L, 15_000L, 25_000L, 40_000L)

    /** Au-delà, l'arrêt est sans doute voulu : on n'insiste plus. */
    const val MAX_RESUMES = 3

    /**
     * Commande de lecture ou de pause venue d'Ongaku ou de l'interface du système (notification,
     * écran de verrouillage) : l'utilisateur a la main, la surveillance s'arrête. Une commande de la
     * voiture, elle, peut venir de l'autoradio qui démarre : la surveillance continue.
     */
    fun userTookOver(controllerPackage: String, ownPackage: String): Boolean =
        controllerPackage == ownPackage || controllerPackage == SYSTEM_UI

    /** À un relevé : relancer la lecture ? */
    fun shouldResume(isPlaying: Boolean, stoppedByUser: Boolean, resumesDone: Int, routedToDevice: Boolean): Boolean =
        !isPlaying && !stoppedByUser && resumesDone < MAX_RESUMES && routedToDevice

    private const val SYSTEM_UI = "com.android.systemui"
}
