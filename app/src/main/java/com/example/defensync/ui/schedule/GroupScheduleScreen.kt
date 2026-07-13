package com.example.defensync.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.TimeConfig
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday
import com.example.defensync.domain.scheduling.SchedulingEngine
import com.example.defensync.ui.defense.DefenseViewModel

private val HourDp = 28.dp
private val HeaderH = 30.dp
private val AxisW = 46.dp
private val BarCorner = 4.dp
private val TotalMinutes = TimeConfig.DAY_END - TimeConfig.DAY_START
private val BodyH = HourDp * (TotalMinutes / 60)

/** One participant's schedule for the whole week, shown as a labelled lane. */
private data class ScheduleLane(
    val title: String,
    val subtitle: String,
    val ranges: Map<Weekday, List<TimeRange>>,
    val isYou: Boolean
)

/**
 * Read-only weekly schedule for a defense group. Faculty (adviser + panelists) are always shown; a
 * student viewing their own defense also sees their own lane (never other students). The confirmed
 * defense time is highlighted on its weekday so a student can see exactly when they'll be busy — i.e.
 * which class to be excused from. Times are exact-minute, and an exact-time list sits below the chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScheduleScreen(
    defenseId: String,
    currentUid: String,
    isStudentViewer: Boolean,
    defenseVm: DefenseViewModel,
    onBack: () -> Unit,
    scheduleVm: ScheduleViewModel = viewModel()
) {
    val defenses by defenseVm.defenses.collectAsStateWithLifecycle()
    val defense = defenses.firstOrNull { it.id == defenseId }
    // The signed-in user's default weekly schedule — the source for a student's own lane.
    val mySchedule by scheduleVm.selection.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group schedule", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (defense == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This defense is no longer available.")
            }
            return@Scaffold
        }

        val lanes = buildLanes(defense, currentUid, isStudentViewer, mySchedule)
        var day by remember(defense.id) { mutableStateOf(defense.slot?.weekday ?: Weekday.MON) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DefenseTimeCard(defense.slot, defense.title)

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Weekday.entries.forEach { d ->
                    FilterChip(selected = d == day, onClick = { day = d }, label = { Text(d.short) })
                }
            }

            Timeline(lanes, day, defense.slot)

            ExactTimeList(lanes, day, defense.slot)
        }
    }
}

private fun buildLanes(
    defense: DefenseRequest,
    currentUid: String,
    isStudentViewer: Boolean,
    mySchedule: Map<Weekday, List<TimeRange>>
): List<ScheduleLane> {
    val faculty = defense.memberIds.map { uid ->
        ScheduleLane(
            title = shortName(defense.nameOf(uid)),
            subtitle = if (uid == defense.adviserId) "Adviser" else "Panelist",
            ranges = defense.availabilityOf(uid),
            isYou = uid == currentUid
        )
    }
    // A student viewer gets their own lane from their default weekly schedule.
    val you = if (isStudentViewer && currentUid !in defense.memberIds) {
        listOf(
            ScheduleLane(
                title = shortName(defense.nameOf(currentUid).ifBlank { "You" }),
                subtitle = "You",
                ranges = mySchedule,
                isYou = true
            )
        )
    } else emptyList()
    return faculty + you
}

@Composable
private fun DefenseTimeCard(slot: AlignedSlot?, title: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Defense time", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                slot?.label ?: "No time confirmed yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (slot == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
            if (slot != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Be free (and excused from any class) during this window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Timeline(lanes: List<ScheduleLane>, day: Weekday, slot: AlignedSlot?) {
    val freeColor = MaterialTheme.colorScheme.secondary
    val youColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val highlight = MaterialTheme.colorScheme.primary
    val showHighlight = slot != null && slot.weekday == day

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp)) {
            // Headers, sharing the body's axis width + per-lane weights.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.width(AxisW))
                lanes.forEach { lane ->
                    HeaderCell(
                        lane.title,
                        lane.subtitle,
                        Modifier.weight(1f),
                        accent = if (lane.isYou) youColor else null
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth()) {
                TimeAxis()
                lanes.forEach { lane ->
                    Lane(
                        Modifier.weight(1f),
                        ranges = SchedulingEngine.mergeRanges(lane.ranges[day].orEmpty())
                            .mapNotNull { it.clampToDay() },
                        barColor = if (lane.isYou) youColor else freeColor,
                        trackColor = trackColor,
                        gridColor = gridColor,
                        highlight = if (showHighlight) slot else null,
                        highlightColor = highlight
                    )
                }
            }

            if (slot != null && !showHighlight) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "The defense is on ${slot.weekday.full}. Tap ${slot.weekday.short} above to see it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Lane(
    modifier: Modifier,
    ranges: List<TimeRange>,
    barColor: Color,
    trackColor: Color,
    gridColor: Color,
    highlight: AlignedSlot?,
    highlightColor: Color
) {
    Box(
        modifier
            .height(BodyH)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(trackColor)
    ) {
        HourGridlines(gridColor)
        // Confirmed-defense band sits behind the availability bars, tinting the whole window.
        if (highlight != null) {
            Box(
                Modifier
                    .offset(y = yFor(highlight.start))
                    .fillMaxWidth()
                    .height(HourDp * ((highlight.end - highlight.start) / 60f))
                    .background(highlightColor.copy(alpha = 0.22f))
            )
        }
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
                    .offset(y = yFor(minute) - 7.dp)
                    .padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun HeaderCell(title: String, subtitle: String, modifier: Modifier, accent: Color?) {
    Box(modifier.height(HeaderH), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Exact-minute breakdown for the selected day, so specific times (e.g. 3:33) are always legible. */
@Composable
private fun ExactTimeList(lanes: List<ScheduleLane>, day: Weekday, slot: AlignedSlot?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${day.full} — exact times", style = MaterialTheme.typography.titleMedium)
            if (slot != null && slot.weekday == day) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Defense · ${TimeConfig.label(slot.start)} – ${TimeConfig.label(slot.end)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            lanes.forEach { lane ->
                val ranges = SchedulingEngine.mergeRanges(lane.ranges[day].orEmpty())
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        lane.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(88.dp)
                    )
                    Text(
                        if (ranges.isEmpty()) "—"
                        else ranges.joinToString(", ") { it.label },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun yFor(minute: Int): Dp = HourDp * ((minute - TimeConfig.DAY_START) / 60f)

private fun heightFor(r: TimeRange): Dp = HourDp * ((r.end - r.start) / 60f)

private fun TimeRange.clampToDay(): TimeRange? {
    val s = start.coerceIn(TimeConfig.DAY_START, TimeConfig.DAY_END)
    val e = end.coerceIn(TimeConfig.DAY_START, TimeConfig.DAY_END)
    return if (e > s) TimeRange(s, e) else null
}

private fun shortName(full: String): String = full.trim().split(" ").firstOrNull().orEmpty()
