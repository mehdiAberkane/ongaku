package fr.devsecopsforce.ongaku.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Écriture tout-ou-rien d'un petit fichier : le contenu part dans un fichier temporaire, forcé sur le
 * disque, qui remplace ensuite l'ancien en une seule opération. Une coupure en plein milieu laisse
 * l'ancien fichier intact.
 *
 * Remplace `AtomicFile`, dont le renommage final échoue sous Windows quand le fichier existe déjà :
 * les tests qui tournent sur l'ordinateur relisaient alors l'ancien contenu.
 */
internal object SafeFile {

    @Throws(IOException::class)
    fun write(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }
}
