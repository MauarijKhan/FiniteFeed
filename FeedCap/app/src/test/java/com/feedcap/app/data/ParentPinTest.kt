package com.feedcap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentPinTest {

    @Test
    fun rightPinMatchesWrongDoesNot() {
        val salt = ParentPin.newSalt()
        val hash = ParentPin.hash("4821", salt)
        assertTrue(ParentPin.matches("4821", salt, hash))
        assertFalse(ParentPin.matches("4822", salt, hash))
        assertFalse(ParentPin.matches("482", salt, hash))
    }

    @Test
    fun samePinDifferentSaltDifferentHash() {
        assertNotEquals(ParentPin.hash("1234", ParentPin.newSalt()), ParentPin.hash("1234", ParentPin.newSalt()))
    }

    @Test
    fun onlyFourDigitsAreValid() {
        assertTrue(ParentPin.isValid("0000"))
        assertFalse(ParentPin.isValid("12a4"))
        assertFalse(ParentPin.isValid("12345"))
    }

    @Test
    fun lockoutGrowsAndCaps() {
        assertEquals(0L, ParentPin.lockoutMs(4))
        assertEquals(30_000L, ParentPin.lockoutMs(5))
        assertEquals(60_000L, ParentPin.lockoutMs(6))
        assertEquals(120_000L, ParentPin.lockoutMs(7))
        assertEquals(15 * 60_000L, ParentPin.lockoutMs(40))
    }
}
