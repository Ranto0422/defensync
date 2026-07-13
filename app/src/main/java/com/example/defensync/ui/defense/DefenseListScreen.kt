package com.example.defensync.ui.defense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.defensync.domain.model.DefenseRequest
import com.example.defensync.domain.model.DefenseStatus
import com.example.defensync.domain.model.Role
import com.example.defensync.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefenseListScreen(
    profile: UserProfile,
    vm: DefenseViewModel,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit
) {
    val defenses by vm.defenses.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (profile.role == Role.STUDENT) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New defense") }
                )
            }
        }
    ) { padding ->
        if (defenses.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (profile.role == Role.STUDENT)
                        "No defenses yet.\nTap “New defense” to propose one."
                    else
                        "No defense invitations yet.\nStudents will appear here when they invite you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(defenses, key = { it.id }) { defense ->
                    DefenseCard(
                        defense = defense,
                        currentUid = profile.uid,
                        onClick = { onOpen(defense.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefenseCard(defense: DefenseRequest, currentUid: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    defense.title.ifBlank { "Untitled defense" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (defense.unreadMessages > 0) {
                        androidx.compose.material3.Badge(
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text("${defense.unreadMessages} 💬") }
                    }
                    StatusChip(defense.status)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Student: ${defense.studentName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                defense.slot?.label ?: "Time not chosen yet",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))

            val needsMyAvailability = defense.status == DefenseStatus.SCHEDULING &&
                currentUid in defense.memberIds &&
                !defense.hasAvailability(currentUid)
            val awaitingMyResponse = defense.status == DefenseStatus.PENDING &&
                currentUid in defense.memberIds &&
                defense.responseOf(currentUid) == com.example.defensync.domain.model.ResponseStatus.PENDING
            val highlight = needsMyAvailability || awaitingMyResponse
            Text(
                when {
                    needsMyAvailability -> "⚑ Add your availability"
                    awaitingMyResponse -> "⚑ Awaiting your response"
                    defense.status == DefenseStatus.SCHEDULING -> "Choosing a time"
                    else -> "${defense.acceptedCount}/${defense.memberIds.size} accepted"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun StatusChip(status: DefenseStatus) {
    val (bg, fg) = when (status) {
        DefenseStatus.SCHEDULING -> Color(0xFF1565C0) to Color.White
        DefenseStatus.CONFIRMED -> Color(0xFF1B5E20) to Color.White
        DefenseStatus.PENDING -> Color(0xFFF9A825) to Color.Black
        DefenseStatus.DECLINED -> Color(0xFFB71C1C) to Color.White
        DefenseStatus.CANCELLED -> Color(0xFF616161) to Color.White
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(status.label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}
