package com.ari.streamer

import com.ari.streamer.util.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupRestoreTest {

    @Test
    fun testParserCorrectnessAndRobustness() {
        val m3uContent = """
            #EXTM3U
            
            #EXTINF:-1 group-title="Favourites" tvg-logo="https://static.mytuner.mobi/media/tvos_radios/HGDyN8ySPT.jpg",Sonora 92.0 FM Jakarta
            https://sonora-radio.arenastreaming.com/8130/stream
            
            #EXTINF:-1 group-title="Favourites" tvg-logo="https://www.mahakax.com/wp-content/uploads/2022/06/logo-kisfm.png",Kis 95.1 FM Jakarta
            http://103.246.184.62:1935/noice_kisfm/kisfm/chunklist.m3u8
            
            #EXTINF:-1 group-title="Uncategorized" tvg-logo="https://static.mytuner.mobi/media/tvos_radios/854/delta-fm.57e81899.png",Delta 99.1 FM Jakarta
            http://s1.cloudmu.id:8030/radio.mp3
        """.trimIndent()

        val inputStream = ByteArrayInputStream(m3uContent.toByteArray())
        val entries = M3uParser.parse(inputStream)

        assertEquals(3, entries.size)

        // Entry 1
        assertEquals("Sonora 92.0 FM Jakarta", entries[0].title)
        assertEquals("https://sonora-radio.arenastreaming.com/8130/stream", entries[0].url)
        assertEquals("https://static.mytuner.mobi/media/tvos_radios/HGDyN8ySPT.jpg", entries[0].logoUrl)
        assertEquals("Favourites", entries[0].categoryName)

        // Entry 2
        assertEquals("Kis 95.1 FM Jakarta", entries[1].title)
        assertEquals("http://103.246.184.62:1935/noice_kisfm/kisfm/chunklist.m3u8", entries[1].url)
        assertEquals("https://www.mahakax.com/wp-content/uploads/2022/06/logo-kisfm.png", entries[1].logoUrl)
        assertEquals("Favourites", entries[1].categoryName)
    }

    @Test
    fun testCommasInsideQuotesAndAttributes() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 group-title="Rock, Classic" tvg-logo="https://images.com/logo,1.jpg",Sonora "92.0, FM" Jakarta
            https://sonora.com/stream
        """.trimIndent()

        val inputStream = ByteArrayInputStream(m3uContent.toByteArray())
        val entries = M3uParser.parse(inputStream)

        assertEquals(1, entries.size)
        assertEquals("Sonora \"92.0, FM\" Jakarta", entries[0].title)
        assertEquals("https://sonora.com/stream", entries[0].url)
        assertEquals("https://images.com/logo,1.jpg", entries[0].logoUrl)
        assertEquals("Rock, Classic", entries[0].categoryName)
    }
}
