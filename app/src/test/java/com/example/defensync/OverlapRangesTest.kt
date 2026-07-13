package com.example.defensync

import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.scheduling.SchedulingEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlapRangesTest {

    @Test fun mergeRanges_unionsOverlappingAndTouching() {
        val ranges = listOf(
            TimeRange(420, 480),   // 7:00–8:00
            TimeRange(480, 540),   // 8:00–9:00 (touching -> merge)
            TimeRange(600, 660),   // 10:00–11:00 (disjoint)
            TimeRange(630, 720)    // 10:30–12:00 (overlaps previous)
        )
        assertEquals(
            listOf(TimeRange(420, 540), TimeRange(600, 720)),
            SchedulingEngine.mergeRanges(ranges)
        )
    }

    @Test fun mergeRanges_dropsInvalid() {
        assertEquals(
            listOf(TimeRange(420, 480)),
            SchedulingEngine.mergeRanges(listOf(TimeRange(500, 500), TimeRange(420, 480)))
        )
    }

    @Test fun overlapRanges_exactMinuteIntersection() {
        // Non-30-aligned boundary (…–4:35 PM = 995) must survive, not snap.
        val a = listOf(TimeRange(420, 995))    // 7:00–4:35 PM
        val b = listOf(TimeRange(600, 1200))   // 10:00–8:00 PM
        assertEquals(
            listOf(TimeRange(600, 995)),        // 10:00–4:35 PM
            SchedulingEngine.overlapRanges(listOf(a, b))
        )
    }

    @Test fun overlapRanges_multipleBandsAndGaps() {
        val a = listOf(TimeRange(420, 540), TimeRange(600, 720))
        val b = listOf(TimeRange(450, 690))
        assertEquals(
            listOf(TimeRange(450, 540), TimeRange(600, 690)),
            SchedulingEngine.overlapRanges(listOf(a, b))
        )
    }

    @Test fun overlapRanges_emptyWhenAMemberHasNone() {
        assertEquals(emptyList<TimeRange>(), SchedulingEngine.overlapRanges(listOf(
            listOf(TimeRange(420, 540)),
            emptyList()
        )))
    }
}
