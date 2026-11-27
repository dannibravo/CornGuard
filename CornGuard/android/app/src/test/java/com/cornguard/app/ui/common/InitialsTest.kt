package com.cornguard.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test
    fun firstAndLastWord() {
        assertEquals("JC", Initials.of("Juan dela Cruz"))
        assertEquals("AB", Initials.of("  ana   bacus "))
    }

    @Test
    fun oneWordOrNothing() {
        assertEquals("P", Initials.of("Pedro"))
        assertEquals("", Initials.of(""))
        assertEquals("", Initials.of(null))
    }
}
