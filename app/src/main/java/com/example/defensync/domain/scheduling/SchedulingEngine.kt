package com.example.defensync.domain.scheduling

import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.TimeConfig
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday

/**
 * Turns availability ranges into 3-hour windows everyone shares.
 *
 * Each participant's ranges (per day) are expanded to the set of 30-min slot-starts they fully
 * cover. Intersecting those sets across participants gives the shared slots; a defense start is
 * "aligned" when the participants share every 30-min slot across the whole 3-hour block.
 */
object SchedulingEngine {

    /**
     * Merge overlapping or touching ranges into a minimal, sorted list (a union). Exact-minute:
     * unlike [coveredSlots] it does not snap to 30-min slots, so partial availability (e.g. a range
     * ending 4:35 PM) is preserved. Empty/invalid ranges are dropped.
     */
    fun mergeRanges(ranges: List<TimeRange>): List<TimeRange> {
        val sorted = ranges.filter { it.isValid }.sortedBy { it.start }
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf(sorted.first())
        for (r in sorted.drop(1)) {
            val last = merged.last()
            if (r.start <= last.end) {
                // Overlapping or touching — extend the current range to the farther end.
                if (r.end > last.end) merged[merged.lastIndex] = last.copy(end = r.end)
            } else {
                merged.add(r)
            }
        }
        return merged
    }

    /**
     * Exact-minute interval intersection across every member: the ranges of time where *all*
     * members are simultaneously free. [perMember] is one member's ranges per entry. Returns empty
     * if there are no members or any member has no availability. Handles non-30-aligned boundaries.
     */
    fun overlapRanges(perMember: List<List<TimeRange>>): List<TimeRange> {
        if (perMember.isEmpty() || perMember.any { it.none { r -> r.isValid } }) return emptyList()
        return perMember
            .map { mergeRanges(it) }
            .reduce { acc, next -> intersectRanges(acc, next) }
    }

    /** Intersection of two already-sorted, non-overlapping range lists. */
    private fun intersectRanges(a: List<TimeRange>, b: List<TimeRange>): List<TimeRange> {
        val out = mutableListOf<TimeRange>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            val start = maxOf(a[i].start, b[j].start)
            val end = minOf(a[i].end, b[j].end)
            if (end > start) out.add(TimeRange(start, end))
            // Advance whichever range ends first — it can't overlap anything further along.
            if (a[i].end < b[j].end) i++ else j++
        }
        return out
    }

    /** Slot-start minutes fully inside at least one range. */
    fun coveredSlots(ranges: List<TimeRange>): Set<Int> {
        if (ranges.isEmpty()) return emptySet()
        val result = mutableSetOf<Int>()
        for (slot in TimeConfig.slotStarts) {
            if (ranges.any { it.start <= slot && slot + TimeConfig.SLOT <= it.end }) {
                result.add(slot)
            }
        }
        return result
    }

    /** True when [ranges] cover the entire 3-hour block starting at [start]. */
    fun coversDefense(ranges: List<TimeRange>, start: Int): Boolean {
        val slots = coveredSlots(ranges)
        return (start until start + TimeConfig.DEFENSE step TimeConfig.SLOT).all { it in slots }
    }

    /**
     * Exact-minute check that a full 3-hour defense block starting at [start] fits entirely inside
     * one of the shared [windows] (typically the output of [overlapRanges]). Unlike [coversDefense]
     * this does not snap to the 30-min grid, so an arbitrary start like 10:33 is honored.
     */
    fun fitsBlock(windows: List<TimeRange>, start: Int): Boolean =
        windows.any { it.start <= start && start + TimeConfig.DEFENSE <= it.end }

    /** [participants] is one weekday -> ranges map per participant. */
    fun findAlignedSlots(participants: List<Map<Weekday, List<TimeRange>>>): List<AlignedSlot> {
        if (participants.isEmpty() || participants.any { p -> p.values.all { it.isEmpty() } }) {
            return emptyList()
        }

        val slots = mutableListOf<AlignedSlot>()
        for (day in Weekday.entries) {
            val common: Set<Int> = participants
                .map { coveredSlots(it[day].orEmpty()) }
                .reduce { acc, s -> acc intersect s }
            if (common.isEmpty()) continue

            for (start in TimeConfig.defenseStarts) {
                val fits = (start until start + TimeConfig.DEFENSE step TimeConfig.SLOT)
                    .all { it in common }
                if (fits) slots.add(AlignedSlot(day, start))
            }
        }
        return slots
    }
}
