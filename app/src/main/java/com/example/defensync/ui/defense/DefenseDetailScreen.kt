package com.example.defensync.ui.defense

import androidx.compose.foundation.background
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.defensync.ui.components.TimeField
import com.example.defensync.ui.components.WeeklyAvailabilityEditor

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
                },
                actions = {
                    if (defense != null) {
                        // Group chat lives in the app bar as a badged icon — the convention for a
                        // secondary destination — instead of a full-width bar eating the layout.
                        if (currentUid in defense.participantIds) {
                            IconButton(onClick = { onOpenChat(defense.id, defense.title) }) {
                                BadgedBox(badge = {
                                    if (defense.unreadMessages > 0) {
                                        Badge { Text(defense.unreadMessages.coerceAtMost(99).toString()) }
                                    }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Open group chat"
                                    )
                                }
                            }
                        }

                        val canEdit = defense.canEdit(currentUid) &&
                            defense.status != DefenseStatus.CANCELLED
                        val canDelete = currentUid == defense.studentId
                        if (canEdit || canDelete) {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                if (canEdit) {
                                    DropdownMenuItem(
                                        text = { Text("Edit group") },
                                        onClick = { menuOpen = false; onEdit(defense.id) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                }
                                if (canDelete) {
                                    DropdownMenuItem(
                                        text = {
                                            Text("Delete defense", color = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { menuOpen = false; showDeleteConfirm = true },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    )
                                }
                            }
                        }
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
            HeroCard(defense)

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

            OverlapCard(defense, previewSlot)

            AlignedTimesCard(defense, currentUid, vm, missing, slots, onPreview = { previewSlot = it })

            ResponsesCard(defense, currentUid, vm, onOpenDm)

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
private fun HeroCard(defense: DefenseRequest) {
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

            Spacer(Modifier.height(18.dp))

            // Scheduled time — the single most important fact — gets a prominent leading icon.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Scheduled time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        defense.slot?.label ?: "Not chosen yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (defense.slot == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
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

            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // People — who this group is. Panelists' response status lives in the responses card.
            val studentNames = defense.studentNames.ifEmpty { listOf(defense.studentName) }
                .filter { it.isNotBlank() }
            studentNames.forEach { name ->
                PersonLine(name = name, role = "Student")
                Spacer(Modifier.height(10.dp))
            }
            PersonLine(name = defense.adviserName, role = "Adviser", accent = true)
        }
    }
}

@Composable
private fun PersonLine(name: String, role: String, accent: Boolean = false) {
    val container = if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    val content = if (accent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(name, container = container, content = content)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                name.ifBlank { "Unassigned" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun InitialsAvatar(
    name: String,
    container: Color,
    content: Color,
    size: Dp = 40.dp
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
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

    // Panel members need to fill this in, so start expanded.
    var expanded by remember(defense.id) { mutableStateOf(true) }

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
private fun OverlapCard(defense: DefenseRequest, previewSlot: AlignedSlot? = null) {
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
            AlignmentGrid(defense, previewSlot = previewSlot)
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

                else -> DefenseTimePicker(defense, vm, slots, onPreview)
            }
        }
    }
}

/**
 * Lets the student set the defense time. Suggested 3-hour windows appear as quick-pick chips that
 * populate the day + start; the student can also nudge the start to any exact minute (e.g. 10:33)
 * via the [TimeField]. The time must fit a window the whole panel is free, so the confirm button is
 * only enabled when the chosen 3-hour block sits inside a shared overlap window.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DefenseTimePicker(
    defense: DefenseRequest,
    vm: DefenseViewModel,
    slots: List<AlignedSlot>,
    onPreview: (AlignedSlot?) -> Unit
) {
    var day by remember(defense.id) {
        mutableStateOf(defense.slot?.weekday ?: slots.firstOrNull()?.weekday ?: Weekday.MON)
    }
    var start by remember(defense.id) {
        mutableStateOf(defense.slot?.start ?: slots.firstOrNull()?.start ?: TimeConfig.DAY_START)
    }
    var showOverride by remember(defense.id) { mutableStateOf(false) }

    // Mirror the current pick onto the overlap chart above; clear it when the picker goes away.
    LaunchedEffect(day, start) { onPreview(AlignedSlot(day, start)) }
    DisposableEffect(Unit) { onDispose { onPreview(null) } }

    val windows = SchedulingEngine.overlapRanges(defense.memberIds.map { defense.rangesOf(it, day) })
    val fits = SchedulingEngine.fitsBlock(windows, start)
    val end = start + TimeConfig.DEFENSE

    // Weekday selector — wraps instead of scrolling sideways.
    Text("Pick a day", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Weekday.entries.forEach { w ->
            FilterChip(
                selected = w == day,
                onClick = { day = w },
                label = { Text(w.short) }
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    // Suggested 3-hour windows for the chosen day, as a tappable vertical list.
    val daySlots = slots.filter { it.weekday == day }
    Text("Suggested windows · ${day.full}", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    if (daySlots.isEmpty()) {
        Hint("No 3-hour window everyone shares this day — set an exact time below.")
    } else {
        daySlots.forEach { slot ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = slot.start == start, onClick = { start = slot.start })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = slot.start == start, onClick = { start = slot.start })
                Spacer(Modifier.width(4.dp))
                Text(
                    "${TimeConfig.label(slot.start)} – ${TimeConfig.label(slot.start + TimeConfig.DEFENSE)}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Or set an exact time", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        TimeField(
            value = start,
            onValueChange = { start = it },
            label = "Defense start",
            minMinutes = TimeConfig.DAY_START,
            maxMinutes = TimeConfig.DAY_END - TimeConfig.DEFENSE
        )
    }
    Spacer(Modifier.height(10.dp))

    if (fits) {
        Text(
            "${TimeConfig.label(start)} – ${TimeConfig.label(end)} · the whole panel is free",
            style = MaterialTheme.typography.bodyMedium,
            color = ConfirmGreen
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.selectSlot(defense.id, AlignedSlot(day, start)) },
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
                    "${day.full}, ${TimeConfig.label(start)} – ${TimeConfig.label(end)} isn't inside a " +
                        "3-hour window the whole panel is free.\n\n" +
                        "Only do this if you've already contacted your adviser and panelists — for " +
                        "example in the group chat — and they've agreed to this time. When they open " +
                        "this defense they'll see a warning that it's outside the availability they " +
                        "set, then accept or decline as usual."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverride = false
                    vm.selectSlot(defense.id, AlignedSlot(day, start))
                }) { Text("Set time & notify", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showOverride = false }) { Text("Cancel") }
            }
        )
    }
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
                    slot.start
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
                        InitialsAvatar(
                            name,
                            container = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            content = MaterialTheme.colorScheme.secondary,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                roleLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (memberId == currentUid) {
                            Text(
                                "You",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else {
                            IconButton(onClick = { onOpenDm(memberId, name) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Message $name",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        ResponseBadge(defense.responseOf(memberId))
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
    val (text, color) = when (status) {
        ResponseStatus.ACCEPTED -> "Accepted" to ConfirmGreen
        ResponseStatus.DECLINED -> "Declined" to Color(0xFFB71C1C)
        ResponseStatus.PENDING -> "Pending" to Color(0xFF9E9E9E)
    }
    Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
}
