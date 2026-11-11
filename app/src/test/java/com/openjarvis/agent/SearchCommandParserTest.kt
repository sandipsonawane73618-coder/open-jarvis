package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCommandParserTest {
    private fun yt(text: String) = SearchCommandParser.parseYouTubeQuery(text)
    private fun web(text: String) = SearchCommandParser.parseWebQuery(text)

    @Test fun youtubeOpenThenSearch() { assertEquals("lofi", yt("YouTube kholo aur lofi search karo")) }

    @Test fun youtubeVariants() {
        assertEquals("lofi", yt("YouTube par lofi search karo"))
        assertEquals("lofi", yt("YouTube pe lofi chalao"))
        assertEquals("lofi", yt("YouTube mein lofi dhundo"))
        assertEquals("lofi", yt("search lofi on YouTube"))
        assertEquals("lofi", yt("lofi YouTube par search karo"))
        assertEquals("lofi", yt("you tube par lofi search karo"))
    }

    @Test fun youtubeDevanagari() {
        assertEquals("लोफी", yt("यूट्यूब पर लोफी सर्च करो"))
        assertEquals("लोफी", yt("यूट्यूब खोलो और लोफी सर्च करो"))
    }

    @Test fun multiWordQueryKeepsInnerWords() {
        assertEquals("arijit singh ke gaane", yt("YouTube par arijit singh ke gaane search karo"))
        assertEquals("lofi hip hop", yt("YouTube kholo aur lofi hip hop search karo"))
    }

    @Test fun notASearch() {
        assertNull(yt("YouTube kholo")); assertNull(yt("YouTube kholo aur lofi"))
        assertNull(yt("YouTube par search karo")); assertNull(yt("Chrome kholo"))
        assertNull(yt("hello")); assertNull(yt("Rahul ko call karo")); assertNull(yt(""))
    }

    @Test fun webSearch() {
        assertEquals("lofi", web("Chrome kholo aur google par lofi search karo"))
        assertEquals("weather", web("Google pe weather search karo"))
        assertEquals("मौसम", web("गूगल पर मौसम सर्च करो"))
        assertNull(web("Chrome kholo")); assertNull(web("hello"))
        assertNull(web("YouTube par lofi search karo"))
    }
}
