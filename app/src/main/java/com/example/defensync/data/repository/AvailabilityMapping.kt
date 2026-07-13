package com.example.defensync.data.repository

import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday

/** Domain availability (weekday -> ranges) to the wire shape ({ "MON": [[420,540]] }). */
fun Map<Weekday, List<TimeRange>>.toWire(): Map<String, List<List<Int>>> =
    entries.filter { it.value.isNotEmpty() }
        .associate { (day, ranges) ->
            day.name to ranges.filter { it.isValid }.map { listOf(it.start, it.end) }
        }
