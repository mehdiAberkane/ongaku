# Ongaku

Lecteur de musique Android pour les fichiers audio stockés sur le téléphone. Aucun compte, aucune
publicité, et pas d'accès à Internet : l'application ne demande pas cette permission.

Écrit en Kotlin avec Jetpack Compose (Material 3) et Media3 / ExoPlayer.

## Fonctionnalités

- **Bibliothèque** : morceaux, albums, artistes, ajouts récents, favoris et playlists.
- **Recherche et tri** : recherche dans les titres, albums et artistes, sans tenir compte des
  accents ni des majuscules ; tri des titres (titre, artiste, album, date d'ajout, durée, écoutes)
  et des albums (nom, artiste, année), retenu d'un lancement à l'autre ; index par lettre pour
  parcourir vite les longues listes.
- **Lecture** : file d'attente, lecture aléatoire, répétition, fondu enchaîné réglable, pause quand
  les écouteurs sont débranchés, reprise de la dernière file au redémarrage.
- **Minuteur de sommeil** : arrêt après 15, 30, 45 ou 60 minutes, ou à la fin du morceau, réglé
  depuis l'écran de lecture ; le volume baisse sur la dernière minute (désactivable dans les réglages).
- **Dossiers** : un onglet qui suit l'arborescence du téléphone ; lecture d'un dossier avec ses
  sous-dossiers, ou de ses seuls fichiers.
- **Égaliseur** : bandes et préréglages du téléphone, amplification des basses, coupé par défaut ;
  accès à l'égaliseur du téléphone quand il en propose un. Les fichiers ne sont jamais modifiés.
- **Lecture automatique en Bluetooth** : la musique reprend quand un appareil choisi se connecte.
- **Écran de verrouillage** : écran de lecture affiché par-dessus le verrouillage, sans accès à la
  bibliothèque ni aux réglages. Son bouton « Déverrouiller » ouvre le déverrouillage d'Android
  (sur certains téléphones, l'empreinte ne marche pas tant que cet écran est affiché).
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

Pour l'usage quotidien, installez la **version de publication** : le code est réduit et optimisé
par R8 et un profil de démarrage est fourni, ce qui la rend nettement plus fluide que la version
de débogage (défilement, ouverture de l'écran de lecture, démarrage).

```bash
./gradlew installRelease
```

L'APK est produit dans `app/build/outputs/apk/release/`. Pour le développement, la version de
débogage reste disponible avec `./gradlew installDebug`.

Sous Windows, utilisez `gradlew.bat` à la place de `./gradlew`.

### Clé de signature

La clé ne fait pas partie du dépôt. Créez-la une fois, hors du dépôt :

```bash
keytool -genkeypair -keystore ~/.android-signing/ongaku-release.jks -storetype PKCS12 \
  -alias ongaku -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Ongaku"
```

puis un fichier `keystore.properties` à la racine du projet (ignoré par Git) :

```properties
storeFile=/chemin/absolu/vers/ongaku-release.jks
storePassword=…
keyAlias=ongaku
keyPassword=…
```

Avec ce fichier, la version de publication **et** la version de débogage sont signées avec cette
clé : on peut passer de l'une à l'autre sur le téléphone sans désinstaller, donc sans perdre ses
listes ni ses réglages. Sans ce fichier, la version de publication est produite non signée.

Gardez une copie de la clé et de son mot de passe en lieu sûr : sans elle, une mise à jour
oblige à désinstaller l'application, ce qui efface les listes, les favoris et les réglages.

## Tests

```bash
./gradlew lintDebug testDebugUnitTest
```

Les tests unitaires (`app/src/test`) couvrent la sauvegarde des listes, le stockage des données, le
filtre de la bibliothèque, le regroupement en albums et en artistes, et les décisions du fondu
enchaîné et du compteur d'écoutes. Ils tournent sur l'ordinateur, sans téléphone. La même
vérification, avec la compilation des deux versions, tourne sur GitHub Actions à chaque pull request.

## Permissions

| Permission | Usage |
| --- | --- |
| Accès à la musique (`READ_MEDIA_AUDIO`, ou `READ_EXTERNAL_STORAGE` avant Android 13) | Lire les fichiers audio du téléphone. |
| Service au premier plan, `WAKE_LOCK` | Continuer la lecture écran éteint. |
| Appareils à proximité (`BLUETOOTH_CONNECT`) | Lecture automatique quand un appareil Bluetooth choisi se connecte. Facultative. |
| Superposition (`SYSTEM_ALERT_WINDOW`) | Ouvrir l'écran de lecture sur le verrouillage depuis l'arrière-plan ; Ongaku ne dessine aucune superposition. Facultative, demandée seulement si l'option est activée. |

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
