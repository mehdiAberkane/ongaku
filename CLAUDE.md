# Ongaku, lecteur de musique Android

Lecteur de musique pour les fichiers audio stockés sur le téléphone (Kotlin, Jetpack Compose, Media3 / ExoPlayer). Dépôt public sur GitHub, branche `main`. Le `README.md` décrit les fonctionnalités, les permissions et la structure du code.

Les chemins et identifiants propres à la machine (dossier Todoom, ids des colonnes, SDK, JDK, téléphone de test) sont dans `CLAUDE.local.md`, ignoré par Git : ne jamais les recopier dans un fichier commité.

## Tickets (règle impérative)

Les tickets sont dans le tableau Todoom « Ongaku » (un fichier JSON par carte).

1. **On ne fait que les tickets de la colonne « À faire ».** Ne jamais toucher aux autres colonnes (« Backlog », « En cours » d'un autre travail, « A verifier », « Terminé ») ni aux cartes qu'elles contiennent, même si on le demande en passant : le signaler à l'utilisateur.
2. En commençant un ticket, le déplacer de « À faire » vers « En cours », avant de coder.
3. Développer sur une branche dédiée, commiter, ouvrir la pull request vers `main` sur GitHub (c'est la « MR » dont parle l'utilisateur), puis la fusionner.
4. Une fois fusionnée : revenir sur `main` et la mettre à jour (`git checkout main` puis `git pull`), puis déplacer le ticket de « En cours » vers « A verifier ».
5. « A verifier » → « Terminé » : **uniquement l'utilisateur**, qui teste lui-même.

Les seuls déplacements permis à l'agent : « À faire » → « En cours » → « A verifier ». Si la pull request ne couvre qu'une partie d'un ticket, le dire et ne pas le déplacer vers « A verifier ».

Déplacer une carte : modifier `columnId`, `order` et `updatedAt` dans son fichier JSON, en gardant le format `JSON.stringify(carte, null, 2)` sans saut de ligne final. Le premier ticket d'une colonne est celui dont `order` est le plus bas (il peut être négatif : l'utilisateur réordonne les cartes).

## Principes de l'application

- **Lecture seule sur la musique** : Ongaku ne modifie, ne déplace et ne supprime jamais un fichier audio. Playlists, favoris et réglages restent dans le stockage privé de l'application.
- **Pas d'accès à Internet** : ne pas ajouter la permission `INTERNET`. La sauvegarde vers Google Drive passe par le sélecteur de fichiers d'Android.
- **Options d'apparence désactivables** : toute nouvelle option d'apparence peut être coupée dans les réglages.
- **Permissions facultatives** : une fonction qui demande une permission en plus (Bluetooth, superposition) doit rester utilisable sans elle et l'expliquer à l'utilisateur.
- Textes de l'interface en français.

## Compiler

```bash
./gradlew assembleDebug     # Windows : .\gradlew.bat assembleDebug (le .\ est obligatoire)
./gradlew installDebug
```

`JAVA_HOME` doit pointer vers un JDK 17 ou plus récent.

## Tester sur le téléphone

- Tester sur le vrai téléphone de l'utilisateur, branché en USB (`adb`), et vérifier le résultat (état de la session média, capture d'écran) plutôt que de le supposer.
- Le téléphone est souvent verrouillé : vérifier `isKeyguardShowing` avant de toucher l'écran, et sinon demander à l'utilisateur de le déverrouiller.
- Les données de l'utilisateur sont réelles : sauvegarder les préférences touchées par un test (`run-as fr.devsecopsforce.ongaku`) et les remettre après ; ne jamais modifier ni supprimer `files/ongaku.json` (playlists, favoris).
- Remettre la lecture dans l'état où l'utilisateur l'a laissée (en pause, même file).
- Un widget `RemoteViews` n'accepte que certaines vues : pas de `<View>` simple (le widget plante), utiliser `FrameLayout`.

## Git

- Messages de commit en français, courts, à l'impératif ou descriptifs.
- Ne jamais commiter `local.properties`, `CLAUDE.local.md`, des clés de signature ou des APK.
