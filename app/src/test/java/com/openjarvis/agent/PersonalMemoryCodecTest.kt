package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalMemoryCodecTest {
    @Test fun storesBoundedDeduplicatedNotesNewestFirst() {
        val first = PersonalMemoryCodec.remember(emptyList(), "I prefer Marathi")
        val second = PersonalMemoryCodec.remember(first, "I prefer English")
        val duplicate = PersonalMemoryCodec.remember(second, "I prefer Marathi")
        assertEquals(listOf("I prefer Marathi"), first)
        assertEquals(listOf("I prefer English", "I prefer Marathi"), second)
        assertEquals(listOf("I prefer Marathi", "I prefer English"), duplicate)
    }

    @Test fun rejectsEmptyAndOversizedNotes() {
        assertFalse(PersonalMemoryCodec.normalize("  ") != null)
        assertEquals(null, PersonalMemoryCodec.normalize("x".repeat(181)))
        assertTrue(PersonalMemoryCodec.normalize("use dark theme") != null)
    }

    @Test fun forgetsMatchingNoteOrAllNotesExplicitly() {
        val notes = listOf("I prefer Marathi", "I prefer dark theme")
        assertEquals(listOf("I prefer dark theme"), PersonalMemoryCodec.forget(notes, "Marathi"))
        assertTrue(PersonalMemoryCodec.forget(notes, "all").isEmpty())
    }
}
