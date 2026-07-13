package com.example.defensync

import com.example.defensync.domain.model.TimeConfig
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.scheduling.SchedulingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitsBlockTest {

    // 10:33 AM = 633; a 3-hour block runs 10:33 AM – 1:33 PM (633..813).
    private val start1033 = 633

    @Test fun fitsBlock_arbitraryStartInsideWindow() {
        // Everyone free 10:00 AM – 2:00 PM (600..840) fully contains 10:33–1:33.
        val windows = listOf(TimeRange(600, 840))
        assertTrue(SchedulingEngine.fitsBlock(windows, start1033))
    }

    @Test fun fitsBlock_blockOverrunsWindow() {
        // Free only 10:00 AM – 1:00 PM (600..780); 10:33 block ends 1:33 PM -> doesn't fit.
        val windows = listOf(TimeRange(600, 780))
        assertFalse(SchedulingEngine.fitsBlock(windows, start1033))
    }

    @Test fun fitsBlock_exactBoundaryIsInclusive() {
        // Window exactly equals the block: 633..813 fits itself.
        val windows = listOf(TimeRange(633, 813))
        assertTrue(SchedulingEngine.fitsBlock(windows, start1033))
    }

    @Test fun fitsBlock_picksTheContainingBandAmongSeveral() {
        val windows = listOf(TimeRange(420, 500), TimeRange(600, 840), TimeRange(900, 960))
        assertTrue(SchedulingEngine.fitsBlock(windows, start1033))
        assertFalse(SchedulingEngine.fitsBlock(windows, 460)) // no band is 3h long there
    }

    @Test fun fitsBlock_emptyWindowsNeverFits() {
        assertFalse(SchedulingEngine.fitsBlock(emptyList(), start1033))
    }

    @Test fun parse_keepsExactMinuteNoSnap() {
        // 4:35 PM = 995 must survive rather than snapping to 4:30 (990) or 5:00 (1020).
        assertEquals(995, TimeConfig.parse("435pm"))
        assertEquals(995, TimeConfig.parse("4:35pm"))
        // A minute that used to round up must now stay put.
        assertEquals(633, TimeConfig.parse("10:33")) // 10:33 AM
    }

    @Test fun parse_stillClampsToDayWindow() {
        assertNull(TimeConfig.parse("6:00am"))  // before 7:00 AM
        assertNull(TimeConfig.parse("9:30pm"))  // after 9:00 PM
    }
}
