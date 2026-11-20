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
    fun recoveryCodeLooksRightAndMatchesLoosely() {
        val code = ParentPin.newRecoveryCode()
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
        assertFalse(code.any { it in "01OIL" })
        val salt = ParentPin.newSalt()
        val hash = ParentPin.hash(ParentPin.normalizeCode(code), salt)
        assertTrue(ParentPin.codeMatches(code, salt, hash))
        // Lower case, no dashes, extra spaces: still the same code.
        assertTrue(ParentPin.codeMatches(" " + code.lowercase().replace("-", " ") + " ", salt, hash))
        assertFalse(ParentPin.codeMatches(code.dropLast(1), salt, hash))
        assertFalse(ParentPin.codeMatches(ParentPin.newRecoveryCode(), salt, hash))
    }

    @Test
    fun resetTakesSeventyTwoHours() {
        val start = 1_000_000L
        val h = 60 * 60 * 1000L
        assertFalse(ParentPin.resetDue(0, start + 100 * h))
        assertFalse(ParentPin.resetDue(start, start + 72 * h - 1))
        assertTrue(ParentPin.resetDue(start, start + 72 * h))
        assertEquals(h, ParentPin.resetRemainingMs(start, start + 71 * h))
        assertEquals(0L, ParentPin.resetRemainingMs(start, start + 80 * h))
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
