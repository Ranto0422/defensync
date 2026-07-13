package com.example.defensync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.TimeConfig
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday
import com.example.defensync.domain.scheduling.SchedulingEngine

private val HourDp = 26.dp                                  // vertical scale: one hour of the day
private val HeaderH = 28.dp
private val AxisW = 44.dp
private val GutterW = 76.dp
private val BarCorner = 4.dp

private val TotalMinutes = TimeConfig.DAY_END - TimeConfig.DAY_START   // 07:00–21:00 = 840 min
private val BodyH = HourDp * (TotalMinutes / 60)
private val PreviewGreen = Color(0xFF1B5E20)                // "fits everyone" preview band

/**
 * Availability timeline for a defense's panel (adviser + panelists; the student is excluded because
 * only the panel's overlap drives scheduling). Time runs top→bottom for the selected day. Each
 * member is a lane that stretches to fill the width; their free ranges are drawn as continuous blue
 * bars from exact minutes — so partial availability (e.g. free only until 4:35 PM) renders truthfully
 * rather than snapping to 30-min cells. The accent "Everyone" lane shows the exact windows the whole
 * panel shares, labelled with their times in the right-hand gutter.
 */
@Composable
fun AlignmentGrid(
    defense: DefenseRequest,
    modifier: Modifier = Modifier,
    previewSlot: AlignedSlot? = null
) {
    val members = defense.memberIds
    var day by remember { mutableStateOf(defaultDay(defense)) }
    // Follow the student's pick: when they choose a time below, jump the chart to that day.
    LaunchedEffect(previewSlot) { previewSlot?.let { day = it.weekday } }

    val freeColor = MaterialTheme.colorScheme.secondary       // brand blue
    val everyoneColor = MaterialTheme.colorScheme.primary     // cardinal red accent
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val errorColor = MaterialTheme.colorScheme.error

    // Exact-minute availability for the selected day, clamped to the visible window.
    val memberRanges = members.map { uid ->
        SchedulingEngine.mergeRanges(defense.rangesOf(uid, day)).mapNotNull { it.clampToDay() }
    }
    val overlap = SchedulingEngine.overlapRanges(members.map { uid -> defense.rangesOf(uid, day) })
        .mapNotNull { it.clampToDay() }

    val preview = previewSlot?.takeIf { it.weekday == day }
    val previewFits = preview != null && SchedulingEngine.fitsBlock(overlap, preview.start)

    Column(modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Weekday.entries.forEach { d ->
                FilterChip(selected = d == day, onClick = { day = d }, label = { Text(d.short) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Legend(freeColor, everyoneColor, trackColor)
        Spacer(Modifier.height(10.dp))

        // Column headers, sharing the exact same widths/weights as the body row below.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(AxisW))
            members.forEach { uid ->
                HeaderCell(shortName(defense.nameOf(uid)), Modifier.weight(1f))
            }
            HeaderCell("Everyone", Modifier.weight(1f), accent = everyoneColor)
            Spacer(Modifier.width(GutterW))
        }

        Spacer(Modifier.height(4.dp))

        Box {
            Row(Modifier.fillMaxWidth()) {
                TimeAxis()
                memberRanges.forEach { ranges ->
                    Lane(Modifier.weight(1f), ranges, freeColor, trackColor, gridColor)
                }
                Lane(Modifier.weight(1f), overlap, everyoneColor, trackColor, gridColor)
                WindowGutter(overlap, everyoneColor)
            }
            // The student's candidate 3-hour block, drawn on top across the lanes: green when it fits
            // a window everyone shares, red when it overruns one.
            if (preview != null) {
                val bandColor = if (previewFits) PreviewGreen else errorColor
                Box(
                    Modifier
                        .padding(start = AxisW, end = GutterW)
                        .offset(y = yFor(preview.start))
                        .fillMaxWidth()
                        .height(HourDp * (TimeConfig.DEFENSE / 60f))
                        .clip(RoundedCornerShape(BarCorner))
                        .background(bandColor.copy(alpha = 0.12f))
                        .border(2.dp, bandColor, RoundedCornerShape(BarCorner))
                ) {
                    Text(
                        "Your pick",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = bandColor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

/** A single vertical lane: a faint busy track with hour gridlines and rounded free-time bars. */
@Composable
private fun Lane(
    modifier: Modifier,
    ranges: List<TimeRange>,
    barColor: Color,
    trackColor: Color,
    gridColor: Color
) {
    Box(
        modifier
            .height(BodyH)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(trackColor)
    ) {
        HourGridlines(gridColor)
        ranges.forEach { r ->
            Box(
                Modifier
                    .offset(y = yFor(r.start))
                    .padding(horizontal = 3.dp)
                    .fillMaxWidth()
                    .height(heightFor(r))
                    .clip(RoundedCornerShape(BarCorner))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun HourGridlines(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val step = HourDp.toPx()
        var y = step
        while (y < size.height - 0.5f) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

/** Left time ruler: bold labels on the hour, lighter ones on the half-hour, every 30 min visible. */
@Composable
private fun TimeAxis() {
    Box(Modifier.width(AxisW).height(BodyH)) {
        TimeConfig.timeOptions.forEach { minute ->
            val onHour = minute % 60 == 0
            Text(
                TimeConfig.label(minute),
                fontSize = if (onHour) 10.sp else 8.sp,
                fontWeight = if (onHour) FontWeight.Medium else FontWeight.Normal,
                color = if (onHour) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier
                    .offset(y = yFor(minute) - 7.dp)   // centre the label on its tick line
                    .padding(end = 4.dp)
            )
        }
    }
}

/**
 * Right gutter: the exact panel-wide windows. Each window is a thin accent bracket spanning its band,
 * with the start time pinned to the band's top and the end time to the band's bottom — so the labels
 * line up with where the bar actually begins and ends.
 */
@Composable
private fun WindowGutter(overlap: List<TimeRange>, accent: Color) {
    Box(Modifier.width(GutterW).height(BodyH)) {
        overlap.forEach { r ->
            // Bracket line spanning the band top→bottom.
            Box(
                Modifier
                    .offset(y = yFor(r.start))
                    .width(2.dp)
                    .height(heightFor(r))
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent)
            )
            // Start time — centred on the band's top edge.
            Text(
                TimeConfig.label(r.start),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                modifier = Modifier
                    .offset(y = yFor(r.start) - 6.dp)
                    .padding(start = 8.dp)
            )
            // End time — centred on the band's bottom edge.
            Text(
                "– ${TimeConfig.label(r.end)}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .offset(y = yFor(r.end) - 6.dp)
                    .padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, accent: Color? = null) {
    Box(modifier.height(HeaderH), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = if (accent != null) FontWeight.SemiBold else FontWeight.Medium,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun Legend(free: Color, everyone: Color, busy: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(free, "Free")
        LegendItem(everyone, "Everyone free")
        LegendItem(busy, "Busy")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Vertical offset of a minute-of-day within the timeline body. */
private fun yFor(minute: Int): Dp = HourDp * ((minute - TimeConfig.DAY_START) / 60f)

/** Pixel height of a range within the timeline body. */
private fun heightFor(r: TimeRange): Dp = HourDp * ((r.end - r.start) / 60f)

/** Clamp a range to the visible day window; null if nothing remains. */
private fun TimeRange.clampToDay(): TimeRange? {
    val s = start.coerceIn(TimeConfig.DAY_START, TimeConfig.DAY_END)
    val e = end.coerceIn(TimeConfig.DAY_START, TimeConfig.DAY_END)
    return if (e > s) TimeRange(s, e) else null
}

private fun defaultDay(defense: DefenseRequest): Weekday {
    val aligned = SchedulingEngine.findAlignedSlots(defense.memberIds.map { defense.availabilityOf(it) })
    return aligned.firstOrNull()?.weekday ?: Weekday.MON
}

private fun shortName(full: String): String = full.trim().split(" ").first()
