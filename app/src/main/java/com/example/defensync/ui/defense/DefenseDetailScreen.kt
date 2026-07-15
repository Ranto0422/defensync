package com.example.defensync.ui.defense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.defensync.domain.model.AlignedSlot
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.DefenseStatus
import com.example.defensync.domain.model.ResponseStatus
import com.example.defensync.domain.model.TimeConfig
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday
import com.example.defensync.domain.scheduling.SchedulingEngine
import com.example.defensync.ui.components.AlignmentGrid
import com.example.defensync.ui.components.InitialsAvatar
import com.example.defensync.ui.components.TimeField
import com.example.defensync.ui.components.WeeklyAvailabilityEditor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ConfirmGreen = Color(0xFF1B5E20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefenseDetailScreen(
    defenseId: String,
    currentUid: String,
    vm: DefenseViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenChat: (String, String) -> Unit,
    onOpenDm: (String, String) -> Unit,
    onOpenSchedules: (String) -> Unit
) {
    val defenses by vm.defenses.collectAsStateWithLifecycle()
    val defense = defenses.firstOrNull { it.id == defenseId }

    // Hoisted so the overflow menu (in the top bar) can trigger the same confirmation prompt.
    var showDeleteConfirm by remember(defenseId) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        defense?.title?.takeIf { it.isNotBlank() } ?: "Defense",
                        maxLines = 1
                    )
                },
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

        // Availability alignment feeds both the summary stats and the "Aligned times" card, so
        // compute it once here rather than twice.
        val missing = defense.membersMissingAvailability
        val slots = if (missing.isEmpty()) {
            SchedulingEngine.findAlignedSlots(defense.memberIds.map { defense.availabilityOf(it) })
        } else emptyList()

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroCard(defense, currentUid, onOpenChat, onEdit)

            if (currentUid in defense.participantIds) {
                OutlinedButton(
                    onClick = { onOpenSchedules(defense.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("View group schedule")
                }
            }

            StatRow(defense, alignedWindows = slots.size)

            // Availability is a panel-member concern; students no longer edit it here — their
            // schedule now lives in the group-schedule screen.
            val editable = currentUid in defense.memberIds &&
                defense.status != DefenseStatus.CONFIRMED &&
                defense.status != DefenseStatus.CANCELLED
            if (editable) {
                MyAvailabilityCard(defense, currentUid, vm)
            }

            // The student's in-progress time pick, previewed on the overlap chart above the picker.
            var previewSlot by remember(defense.id) { mutableStateOf<AlignedSlot?>(null) }

            // Highlight the student's active pick if any; otherwise the already-scheduled time, so
            // panel members (and anyone just viewing) see the requested time marked on the chart.
            OverlapCard(
                defense,
                previewSlot = previewSlot ?: defense.slot,
                previewLabel = if (previewSlot != null) "Your pick" else "Scheduled"
            )

            AlignedTimesCard(defense, currentUid, vm, missing, slots, onPreview = { previewSlot = it })

            ResponsesCard(defense, currentUid, vm, onOpenDm)

            if (currentUid == defense.studentId) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete defense")
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        if (showDeleteConfirm) {
            val notConfirmed = defense.status != DefenseStatus.CONFIRMED
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = {
                    Text(
                        if (notConfirmed) "Delete before it's confirmed?"
                        else "Delete this confirmed defense?"
                    )
                },
                text = {
                    Text(
                        if (notConfirmed)
                            "This defense hasn't been confirmed yet. Deleting permanently " +
                                "removes the group, its schedule, chat, and responses. " +
                                "This can't be undone."
                        else
                            "This defense is already confirmed. Deleting permanently removes " +
                                "it for everyone — the group, schedule, chat, and responses. " +
                                "This can't be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        vm.deleteDefense(defense.id) { onBack() }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun HeroCard(
    defense: DefenseRequest,
    currentUid: String,
    onOpenChat: (String, String) -> Unit,
    onEdit: (String) -> Unit
) {
    val total = defense.memberIds.size
    val accepted = defense.acceptedCount
    val confirmed = defense.status == DefenseStatus.CONFIRMED

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(defense.status)
                if (total > 0) {
                    Text(
                        "$accepted of $total accepted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // People — plain labelled lines (matches the mockup).
            val studentNames = defense.studentNames.ifEmpty { listOf(defense.studentName) }
                .filter { it.isNotBlank() }
            Text(
                "Students: ${studentNames.joinToString(", ").ifBlank { "—" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Adviser: ${defense.adviserName.ifBlank { "Unassigned" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // Scheduled time — the single most important fact — in a boxed field.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(
                    "SCHEDULED TIME",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val slot = defense.slot
                Text(
                    if (slot == null) "Not chosen yet" else buildString {
                        slotDateLabel(slot.date)?.let { append(it); append(" · ") }
                        append("${TimeConfig.label(slot.start)} – ${TimeConfig.label(slot.end)}")
                        append(" · ${TimeConfig.durationLabel(slot.durationMin)}")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (slot == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            if (total > 0 && defense.slot != null) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { accepted.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (confirmed) ConfirmGreen else MaterialTheme.colorScheme.primary
                )
            }

            if (confirmed) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ConfirmGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Confirmed by everyone — this is on the schedule.",
                        color = ConfirmGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (currentUid in defense.participantIds) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onOpenChat(defense.id, defense.title) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open group chat")
                }
            }

            val canEdit = defense.canEdit(currentUid) && defense.status != DefenseStatus.CANCELLED
            if (canEdit) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onEdit(defense.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit group (panel, adviser, students)")
                }
            }
        }
    }
}

@Composable
private fun StatRow(defense: DefenseRequest, alignedWindows: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(
            icon = Icons.Default.Groups,
            value = defense.memberIds.size.toString(),
            label = "Panel",
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Default.HowToReg,
            value = "${defense.acceptedCount}/${defense.memberIds.size}",
            label = "Accepted",
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Default.Event,
            value = alignedWindows.toString(),
            label = "Windows",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MyAvailabilityCard(
    defense: DefenseRequest,
    currentUid: String,
    vm: DefenseViewModel
) {
    val stored = defense.availabilityOf(currentUid)
    var local by remember(defense.id) { mutableStateOf<Map<Weekday, List<TimeRange>>>(emptyMap()) }
    var seeded by remember(defense.id) { mutableStateOf(false) }
    LaunchedEffect(defense.id) {
        if (!seeded) {
            local = stored
            seeded = true
        }
    }
    val normalized = local
        .mapValues { entry -> entry.value.filter { it.isValid } }
        .filterValues { it.isNotEmpty() }
    val dirty = normalized != stored

    var expanded by remember(defense.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Your availability", style = MaterialTheme.typography.titleMedium)
                    if (!expanded) {
                        Text(
                            if (normalized.isEmpty()) "Tap to set the times you're free"
                            else "${normalized.size} day(s) set · tap to edit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Text(
                    "Seeded from your default schedule — adjust it for this panel only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                WeeklyAvailabilityEditor(
                    selection = local,
                    onAddRange = { day ->
                        val existing = local[day].orEmpty()
                        val start = (existing.maxOfOrNull { it.end } ?: TimeConfig.DAY_START)
                            .coerceAtMost(TimeConfig.DAY_END - TimeConfig.DEFENSE)
                        local = local.toMutableMap().apply {
                            this[day] = existing + TimeRange(start, start + TimeConfig.DEFENSE)
                        }
                    },
                    onUpdateRange = { day, index, range ->
                        local = local.toMutableMap().apply {
                            this[day] = local[day].orEmpty().mapIndexed { i, r -> if (i == index) range else r }
                        }
                    },
                    onRemoveRange = { day, index ->
                        local = local.toMutableMap().apply {
                            this[day] = local[day].orEmpty().filterIndexed { i, _ -> i != index }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveGroupAvailability(defense.id, normalized) },
                    enabled = dirty,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (dirty) "Save my availability" else "Saved ✓") }
            }
        }
    }
}

@Composable
private fun OverlapCard(
    defense: DefenseRequest,
    previewSlot: AlignedSlot? = null,
    previewLabel: String = "Your pick"
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Availability overlap", style = MaterialTheme.typography.titleMedium)
            Text(
                "Blue bars show when each panel member is free; the “Everyone” lane marks the exact " +
                    "windows the whole panel shares.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            AlignmentGrid(defense, previewSlot = previewSlot, previewLabel = previewLabel)
        }
    }
}

@Composable
private fun AlignedTimesCard(
    defense: DefenseRequest,
    currentUid: String,
    vm: DefenseViewModel,
    missing: List<String>,
    slots: List<AlignedSlot>,
    onPreview: (AlignedSlot?) -> Unit
) {
    val studentCanPick = currentUid == defense.studentId &&
        defense.status in listOf(DefenseStatus.SCHEDULING, DefenseStatus.PENDING, DefenseStatus.DECLINED)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Aligned times", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            when {
                missing.isNotEmpty() -> Hint(
                    "Waiting on availability from: " +
                        missing.joinToString { defense.nameOf(it) }
                )

                !studentCanPick -> {
                    when {
                        defense.slot != null ->
                            Text("•  ${defense.slot.label}", style = MaterialTheme.typography.bodyMedium)
                        slots.isEmpty() -> Hint(
                            "No 3-hour window works for the whole panel yet. Ask members to add " +
                                "more availability."
                        )
                        else -> {
                            Hint("The student will pick one of these ${slots.size} aligned windows:")
                            slots.forEach { slot ->
                                Text("•  ${slot.label}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                else -> DefenseTimePicker(defense, vm, onPreview)
            }
        }
    }
}

/**
 * Lets the student set the defense on a specific calendar date, for a chosen length. The date's
 * weekday drives the panel's shared-availability check; suggested windows and the exact-time field
 * respect the selected duration. Confirming sends the date + start + duration to the server, which
 * rejects it if any panelist/adviser is already booked in an overlapping block that day.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DefenseTimePicker(
    defense: DefenseRequest,
    vm: DefenseViewModel,
    onPreview: (AlignedSlot?) -> Unit
) {
    var duration by remember(defense.id) {
        mutableStateOf(defense.slot?.durationMin ?: TimeConfig.DEFENSE)
    }
    var date by remember(defense.id) { mutableStateOf(defense.slot?.date) }
    var start by remember(defense.id) {
        mutableStateOf(defense.slot?.start ?: TimeConfig.DAY_START)
    }
    var showOverride by remember(defense.id) { mutableStateOf(false) }
    var showDatePicker by remember(defense.id) { mutableStateOf(false) }
    var showConfirm by remember(defense.id) { mutableStateOf(false) }
    var showExact by remember(defense.id) { mutableStateOf(false) }

    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val calendarEntries by vm.calendarEntries.collectAsStateWithLifecycle()

    val weekday: Weekday? = date?.let { weekdayOf(it) }
    val end = start + duration

    // Immediate double-booking check: any OTHER scheduled defense on this date whose block overlaps
    // this one and shares a panelist/adviser with us. Mirrors the server rule so it shows as-you-pick.
    val conflictNames: List<String> = if (date == null) emptyList() else calendarEntries
        .filter {
            it.id != defense.id && it.date == date &&
                it.status in listOf(DefenseStatus.PENDING, DefenseStatus.CONFIRMED) &&
                it.start < end && start < it.end
        }
        .flatMap { it.facultyIds }
        .toSet()
        .intersect(defense.memberIds.toSet())
        .map { defense.nameOf(it) }
        .distinct()
    val hasConflict = conflictNames.isNotEmpty()

    // Suggested aligned windows + shared overlap, recomputed for the chosen duration and weekday.
    val memberAvail = defense.memberIds.map { defense.availabilityOf(it) }
    val daySlots = weekday?.let { wd ->
        SchedulingEngine.findAlignedSlots(memberAvail, duration).filter { it.weekday == wd }
    }.orEmpty()
    val windows = weekday?.let { wd ->
        SchedulingEngine.overlapRanges(defense.memberIds.map { defense.rangesOf(it, wd) })
    }.orEmpty()
    val fits = weekday != null && SchedulingEngine.fitsBlock(windows, start, duration)

    // Mirror the current pick onto the overlap chart above; clear it when the picker goes away.
    LaunchedEffect(weekday, start, duration) {
        weekday?.let { onPreview(AlignedSlot(it, start, date, duration)) }
    }
    DisposableEffect(Unit) { onDispose { onPreview(null) } }
    // A stale conflict/cap error shouldn't linger once the student changes the pick.
    LaunchedEffect(date, start, duration) { vm.clearActionError() }

    // 1) Date
    SectionLabel("PICK A DATE")
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { showDatePicker = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            date?.let { slotDateLabel(it) ?: it } ?: "Choose a date",
            style = MaterialTheme.typography.bodyLarge,
            color = if (date == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(Modifier.height(16.dp))

    // 2) Duration — hours + minutes steppers, 1h up to whatever still fits the day.
    val applyDuration: (Int) -> Unit = { d ->
        val clamped = d.coerceIn(TimeConfig.MIN_DEFENSE, TimeConfig.MAX_DEFENSE)
        duration = clamped
        // A longer block may push the start past the day's end — pull it back in.
        if (start > TimeConfig.DAY_END - clamped) start = TimeConfig.DAY_END - clamped
    }
    SectionLabel("DEFENSE LENGTH")
    Spacer(Modifier.height(8.dp))
    DurationStepperRow(
        label = "Hours",
        value = "${duration / 60}",
        onMinus = { applyDuration(duration - 60) },
        onPlus = { applyDuration(duration + 60) },
        minusEnabled = duration - 60 >= TimeConfig.MIN_DEFENSE,
        plusEnabled = duration + 60 <= TimeConfig.MAX_DEFENSE
    )
    Spacer(Modifier.height(4.dp))
    DurationStepperRow(
        label = "Minutes",
        value = (duration % 60).toString().padStart(2, '0'),
        onMinus = { applyDuration(duration - 30) },
        onPlus = { applyDuration(duration + 30) },
        minusEnabled = duration - 30 >= TimeConfig.MIN_DEFENSE,
        plusEnabled = duration + 30 <= TimeConfig.MAX_DEFENSE
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Length: ${TimeConfig.durationLabel(duration)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))

    if (showDatePicker) {
        DefenseDatePickerDialog(
            initialIso = date,
            onDismiss = { showDatePicker = false },
            onPick = { iso -> date = iso; showDatePicker = false }
        )
    }

    if (weekday == null) {
        Hint("Pick a date to see the times the whole panel is free.")
        return
    }

    // 3) Suggested windows for the date's weekday + chosen duration.
    Text("Suggested windows · ${weekday.full}", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    if (daySlots.isEmpty()) {
        Hint("No ${TimeConfig.durationLabel(duration)} window everyone shares this day — set an exact time below.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            daySlots.forEach { slot ->
                val selected = slot.start == start
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.18f else 0.06f)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 1f else 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                        .selectable(selected = selected, onClick = { start = slot.start })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${TimeConfig.label(slot.start)} – ${TimeConfig.label(slot.start + duration)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (selected) "Selected" else "Propose",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    val startMatchesSlot = daySlots.any { it.start == start }
    val exactVisible = showExact || daySlots.isEmpty() || !startMatchesSlot
    if (!exactVisible) {
        Text(
            "or set an exact time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { showExact = true }
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Exact time", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            TimeField(
                value = start,
                onValueChange = { start = it },
                label = "Defense start",
                minMinutes = TimeConfig.DAY_START,
                maxMinutes = TimeConfig.DAY_END - duration
            )
        }
    }
    Spacer(Modifier.height(10.dp))

    // A server-side rejection (panel conflict or the daily creation cap) — distinct from the
    // availability warning below, and shown whether or not the block fits everyone's free time.
    actionError?.let { msg ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (hasConflict) {
        // A panelist/adviser is already booked in an overlapping block — surfaced the instant the
        // time is picked; there's no override for a double-booking, so no confirm button is offered.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${conflictNames.joinToString(", ")} already " +
                    (if (conflictNames.size == 1) "has" else "have") +
                    " a defense that overlaps ${TimeConfig.label(start)}–${TimeConfig.label(end)} on " +
                    "${slotDateLabel(date) ?: date}. Pick another time or date.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    } else if (fits) {
        Text(
            "${TimeConfig.label(start)} – ${TimeConfig.label(end)} · the whole panel is free",
            style = MaterialTheme.typography.bodyMedium,
            color = ConfirmGreen
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (defense.slot == null) "Confirm time & notify panel"
                else "Change time & re-notify panel"
            )
        }
    } else {
        // No shared window covers this block. The student can still override — e.g. after agreeing
        // a time with the panel over chat — but it takes an explicit, warned-about confirmation.
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "The panel isn't all free from ${TimeConfig.label(start)} to ${TimeConfig.label(end)}. " +
                    "Pick a time inside a shared window above — or, if the panel has already agreed to " +
                    "this time, override below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showOverride = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (defense.slot == null) "Override & set this time anyway"
                else "Override & change to this time"
            )
        }
    }

    if (showOverride) {
        AlertDialog(
            onDismissRequest = { showOverride = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Set a time outside everyone's availability?") },
            text = {
                Text(
                    "${weekday.full}, ${TimeConfig.label(start)} – ${TimeConfig.label(end)} isn't inside a " +
                        "${TimeConfig.durationLabel(duration)} window the whole panel is free.\n\n" +
                        "Only do this if you've already contacted your adviser and panelists — for " +
                        "example in the group chat — and they've agreed to this time. When they open " +
                        "this defense they'll see a warning that it's outside the availability they " +
                        "set, then accept or decline as usual."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverride = false
                    vm.selectSlot(defense.id, AlignedSlot(weekday, start, date, duration))
                }) { Text("Set time & notify", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showOverride = false }) { Text("Cancel") }
            }
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = {
                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(if (defense.slot == null) "Notify the panel of this time?" else "Re-notify the panel?")
            },
            text = {
                Text(
                    "${slotDateLabel(date) ?: date}\n" +
                        "${TimeConfig.label(start)} – ${TimeConfig.label(end)} · ${TimeConfig.durationLabel(duration)}\n\n" +
                        "Panel: ${defense.memberIds.joinToString(", ") { defense.nameOf(it) }}\n\n" +
                        "They'll be asked to accept or decline this time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.selectSlot(defense.id, AlignedSlot(weekday, start, date, duration))
                }) { Text(if (defense.slot == null) "Notify panel" else "Re-notify panel") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/** One labeled +/− stepper row (e.g. Hours or Minutes) for the defense-length control. */
@Composable
private fun DurationStepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
        OutlinedIconButton(onClick = onMinus, enabled = minusEnabled) {
            Text("–", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp)
        )
        OutlinedIconButton(onClick = onPlus, enabled = plusEnabled) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** A date picker restricted to today onward, with Sundays disabled (no defenses on Sundays). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefenseDatePickerDialog(
    initialIso: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val todayUtc = todayUtcMidnightMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialIso?.let { isoToUtcMillis(it) } ?: todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (utcTimeMillis < todayUtc) return false
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = utcTimeMillis
                return cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(utcMillisToIso(it)) } },
                enabled = state.selectedDateMillis != null
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

/* ---- Date helpers. Material's DatePicker works in UTC-midnight millis; we store ISO yyyy-MM-dd. ---- */

private fun ymdToUtcMillis(year: Int, month0: Int, day: Int): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(year, month0, day, 0, 0, 0)
    return cal.timeInMillis
}

private fun todayUtcMidnightMillis(): Long {
    val local = Calendar.getInstance()
    return ymdToUtcMillis(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
}

private fun utcMillisToIso(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
}

private fun isoToUtcMillis(iso: String): Long? {
    val p = iso.split("-").mapNotNull { it.toIntOrNull() }
    if (p.size != 3) return null
    return ymdToUtcMillis(p[0], p[1] - 1, p[2])
}

private fun weekdayOf(iso: String): Weekday? {
    val millis = isoToUtcMillis(iso) ?: return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> Weekday.MON
        Calendar.TUESDAY -> Weekday.TUE
        Calendar.WEDNESDAY -> Weekday.WED
        Calendar.THURSDAY -> Weekday.THU
        Calendar.FRIDAY -> Weekday.FRI
        Calendar.SATURDAY -> Weekday.SAT
        else -> null   // Sunday — not a scheduling day
    }
}

/** "Mon, Jul 20, 2026" from an ISO date, or null if it can't be parsed. */
private fun slotDateLabel(iso: String?): String? {
    val millis = iso?.let { isoToUtcMillis(it) } ?: return null
    val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(millis))
}

@Composable
private fun ResponsesCard(
    defense: DefenseRequest,
    currentUid: String,
    vm: DefenseViewModel,
    onOpenDm: (String, String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Panel responses", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            // If the student overrode onto a time outside this member's own availability, warn them
            // — the picked block doesn't fit any window they marked free for this panel.
            val slot = defense.slot
            val outsideMyAvailability = slot != null &&
                currentUid in defense.memberIds &&
                defense.status in listOf(DefenseStatus.PENDING, DefenseStatus.CONFIRMED) &&
                !SchedulingEngine.fitsBlock(
                    SchedulingEngine.mergeRanges(defense.rangesOf(currentUid, slot.weekday)),
                    slot.start,
                    slot.durationMin
                )
            if (outsideMyAvailability) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "This time is outside the availability you set for this panel. The student set " +
                            "it as an override — only accept if you've already agreed to it with them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            defense.memberIds.forEach { memberId ->
                val roleLabel = if (memberId == defense.adviserId) "Adviser" else "Panelist"
                val name = defense.nameOf(memberId)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        InitialsAvatar(name, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                roleLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ResponseBadge(defense.responseOf(memberId))
                        if (memberId == currentUid) {
                            Text(
                                "You",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            TextButton(onClick = { onOpenDm(memberId, name) }) {
                                Text("Message", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            val canRespond = currentUid in defense.memberIds &&
                defense.status == DefenseStatus.PENDING &&
                defense.slot != null &&
                defense.responseOf(currentUid) == ResponseStatus.PENDING
            if (canRespond) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Do you accept ${defense.slot?.label}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.respond(defense.id, accept = false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Decline") }
                    Button(
                        onClick = { vm.respond(defense.id, accept = true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Accept") }
                }
            }
        }
    }
}

/** An uppercase section label (e.g. "PICK A DATE") for the scheduling picker. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ResponseBadge(status: ResponseStatus) {
    val (text, bg, fg) = when (status) {
        ResponseStatus.ACCEPTED -> Triple("Accepted", ConfirmGreen, Color.White)
        ResponseStatus.DECLINED -> Triple("Declined", Color(0xFFB71C1C), Color.White)
        ResponseStatus.PENDING -> Triple("Pending", Color(0xFFF9A825), Color.Black)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
    }
}
