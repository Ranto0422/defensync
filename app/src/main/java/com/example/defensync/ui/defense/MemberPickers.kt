package com.example.defensync.ui.defense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.defensync.domain.model.UserProfile

private val AdviserGold = Color(0xFFF9A825)

/**
 * A single, de-duplicated professor list. Each professor appears once: tick the checkbox to put them
 * on the committee (panelist), tap the ★ to make them the adviser (exactly one). The committee is
 * `selectedIds` (adviser + panelists); `adviserId` is the one starred. This replaces the old two
 * lists that showed every professor twice.
 */
@Composable
fun PanelPicker(
    faculty: List<UserProfile>,
    selectedIds: Set<String>,
    adviserId: String?,
    onToggleMember: (String) -> Unit,
    onSetAdviser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val matches = faculty.filter { it.name.contains(q, ignoreCase = true) }
    val panelistIds = selectedIds.filter { it != adviserId }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectionSummary(
            adviserName = faculty.firstOrNull { it.uid == adviserId }?.name,
            panelistNames = panelistIds.mapNotNull { id -> faculty.firstOrNull { it.uid == id }?.name },
            onRemoveAdviser = { adviserId?.let(onToggleMember) },
            onRemovePanelist = { name ->
                faculty.firstOrNull { it.name == name && it.uid in panelistIds }?.uid?.let(onToggleMember)
            }
        )

        if (faculty.isNotEmpty()) {
            SearchField(query, { query = it }, "Search professors by name")
        }

        SectionCard("Professors") {
            Text(
                "Tick to add a panelist · tap ★ to set the adviser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            when {
                faculty.isEmpty() -> EmptyHint("No professors have signed up yet.")
                matches.isEmpty() -> EmptyHint("No professors match “$q”.")
                else -> matches.forEach { f ->
                    val selected = f.uid in selectedIds
                    val isAdviser = f.uid == adviserId
                    PickerRow(
                        name = f.name,
                        subtitle = when {
                            isAdviser -> "Adviser"
                            selected -> "Panelist"
                            else -> "Not on the committee"
                        },
                        subtitleColor = if (isAdviser) AdviserGold else null,
                        selected = selected
                    ) {
                        IconButton(onClick = { onSetAdviser(f.uid) }) {
                            Icon(
                                if (isAdviser) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isAdviser) "Adviser" else "Set as adviser",
                                tint = if (isAdviser) AdviserGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(checked = selected, onCheckedChange = { onToggleMember(f.uid) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionSummary(
    adviserName: String?,
    panelistNames: List<String>,
    onRemoveAdviser: () -> Unit,
    onRemovePanelist: (String) -> Unit
) {
    if (adviserName == null && panelistNames.isEmpty()) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Label("Adviser")
            Spacer(Modifier.height(4.dp))
            if (adviserName == null) {
                Text(
                    "Not chosen — tap ★ on a professor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Pill(adviserName, star = true) { onRemoveAdviser() }
            }
            Spacer(Modifier.height(12.dp))
            Label("Panelists (${panelistNames.size})")
            Spacer(Modifier.height(4.dp))
            if (panelistNames.isEmpty()) {
                Text(
                    "None yet — tick professors to add",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    panelistNames.forEach { name -> Pill(name, star = false) { onRemovePanelist(name) } }
                }
            }
        }
    }
}

/**
 * Searchable student list for group members. The owner is always included and locked; the rest are
 * optional co-students. Matches by name or student number.
 */
@Composable
fun StudentPicker(
    students: List<UserProfile>,
    ownerId: String,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val matches = students.filter {
        q.isBlank() ||
            it.name.contains(q, ignoreCase = true) ||
            it.studentNumber?.contains(q, ignoreCase = true) == true
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchField(query, { query = it }, "Search students by name or number")
        SectionCard("Group members") {
            when {
                students.isEmpty() -> EmptyHint("No students have signed up yet.")
                matches.isEmpty() -> EmptyHint("No students match “$q”.")
                else -> matches.forEach { s ->
                    val isOwner = s.uid == ownerId
                    val checked = s.uid in selectedIds || isOwner
                    PickerRow(
                        name = s.name,
                        subtitle = s.studentNumber?.takeIf { it.isNotBlank() }?.let { "No. $it" }
                            ?: s.role.label,
                        selected = checked
                    ) {
                        if (isOwner) {
                            Text(
                                "You",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Checkbox(
                            checked = checked,
                            enabled = !isOwner,
                            onCheckedChange = { onToggle(s.uid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    subtitleColor: Color? = null,
    trailing: @Composable () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name, selected)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (subtitleColor != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            trailing()
        }
    }
}

@Composable
private fun InitialsAvatar(name: String, highlighted: Boolean) {
    val container = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name),
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Pill(text: String, star: Boolean, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (star) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = AdviserGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove $text",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
