package fr.devsecopsforce.ongaku.playback

import android.os.Bundle
import fr.devsecopsforce.ongaku.data.toMediaItem
import fr.devsecopsforce.ongaku.data.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueCommandsTest {

    @Test
    fun `les morceaux envoyés au service sont reconstruits à l'identique`() {
        val tracks = listOf(track(7, title = "Été", artist = "Zazie", album = "Cyclo"), track(9))

        val items = QueueCommands.items(QueueCommands.addArgs(tracks, next = true))

        assertEquals(tracks.map { it.toMediaItem() }, items)
    }

    @Test
    fun `des arguments incomplets ou incohérents sont refusés`() {
        assertNull(QueueCommands.items(Bundle()))
        val args = QueueCommands.addArgs(listOf(track(1), track(2)), next = false)
        args.putStringArray("titles", arrayOf("un seul"))
        assertNull(QueueCommands.items(args))
    }
}
