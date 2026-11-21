package fr.devsecopsforce.ongaku.ui

import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppDataRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `l'écran principal et celui du verrouillage modifient les mêmes données`() {
        // Une seule méthode : l'instance unique vit autant que le processus de test.
        val main = AppDataRepository.get(context)
        val lockScreen = AppDataRepository.get(context)
        assertSame(main, lockScreen)

        main.toggleFavorite(1L)
        lockScreen.createPlaylist("Route", listOf(2L))

        val shown = awaitState(main) { it.favorites == setOf(1L) && it.playlists.size == 1 }
        assertEquals("Route", shown.playlists.single().name)
        // Aucune des deux modifications n'a écrasé l'autre dans le fichier.
        val saved = synchronized(AppDataStore.lock) { AppDataStore(context).load() }
        assertEquals(setOf(1L), saved.favorites)
        assertEquals(listOf(2L), saved.playlists.single().trackIds)
    }

    private fun awaitState(repository: AppDataRepository, done: (AppData) -> Boolean): AppData {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done(repository.state.value)) {
            assertTrue("état jamais atteint : ${repository.state.value}", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
        return repository.state.value
    }
}
