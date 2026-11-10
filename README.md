# Ongaku

Lecteur de musique Android pour les fichiers audio stockés sur le téléphone. Aucun compte, aucune
publicité, et pas d'accès à Internet : l'application ne demande pas cette permission.

Écrit en Kotlin avec Jetpack Compose (Material 3) et Media3 / ExoPlayer.

## Fonctionnalités

- **Bibliothèque** : morceaux, albums, artistes, ajouts récents, favoris et playlists.
- **Lecture** : file d'attente, lecture aléatoire, répétition, fondu enchaîné réglable, pause quand
  les écouteurs sont débranchés, reprise de la dernière file au redémarrage.
- **Lecture automatique en Bluetooth** : la musique reprend quand un appareil choisi se connecte.
- **Écran de verrouillage** : écran de lecture affiché par-dessus le verrouillage, sans accès à la
  bibliothèque ni aux réglages.
- **Widgets** : trois formats pour l'écran d'accueil (ligne, rectangle, carré).
- **Apparence** : couleurs du système ou couleur personnalisée, noir pur pour écran OLED, images de
  fond (générale ou par morceau) avec flou et assombrissement, taille du texte, affichage compact.
- **Filtres** : extensions de fichier affichées, dossiers surveillés, durée minimale et maximale.
- **Sauvegarde** : export et import des playlists et favoris dans un fichier JSON. Les morceaux sont
  retrouvés par leurs tags, donc la sauvegarde fonctionne d'un téléphone à l'autre.
- **Statistiques** : nombre d'écoutes et durée totale.

Les playlists, favoris et réglages restent dans le stockage privé de l'application : Ongaku n'écrit
jamais dans les fichiers audio.

## Prérequis

- Android 8.0 (API 26) ou plus récent.
- Pour compiler : JDK 17 ou plus récent et le SDK Android (API 37). Android Studio fournit les deux.

## Compiler et installer

```bash
./gradlew assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/`. Pour l'installer sur un téléphone branché
en USB avec le débogage activé :

```bash
./gradlew installDebug
```

Sous Windows, utilisez `gradlew.bat` à la place de `./gradlew`.

## Permissions

| Permission | Usage |
| --- | --- |
| Accès à la musique (`READ_MEDIA_AUDIO`, ou `READ_EXTERNAL_STORAGE` avant Android 13) | Lire les fichiers audio du téléphone. |
| Service au premier plan, `WAKE_LOCK` | Continuer la lecture écran éteint. |
| Appareils à proximité (`BLUETOOTH_CONNECT`) | Lecture automatique quand un appareil Bluetooth choisi se connecte. Facultative. |
| Superposition (`SYSTEM_ALERT_WINDOW`) | Afficher l'écran de lecture sur le verrouillage. Facultative. |

## Structure

```
app/src/main/java/fr/devsecopsforce/ongaku/
├── data/       bibliothèque, playlists, réglages, sauvegarde
├── playback/   service de lecture, fondu enchaîné, reprise, Bluetooth
├── ui/         écrans Compose et ViewModels
└── widget/     widgets de l'écran d'accueil
```

## Licence

Domaine public, sous [The Unlicense](LICENSE).
