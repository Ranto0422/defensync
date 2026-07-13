package com.example.defensync.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    defenseId: String,
    vm: DefenseViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val defenses by vm.defenses.collectAsStateWithLifecycle()
    val defense = defenses.firstOrNull { it.id == defenseId }
    val faculty by vm.faculty.collectAsStateWithLifecycle()
    val allStudents by vm.students.collectAsStateWithLifecycle()
    val submit by vm.submit.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshPeople() }
    LaunchedEffect(submit) {
        if (submit is SubmitState.Success) {
            vm.resetSubmit()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit group") },
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

        val owner = defense.studentId
        var step by remember(defense.id) { mutableStateOf(1) }
        // Committee = adviser + panelists as one set, mirroring the create wizard.
        var selectedIds by remember(defense.id) {
            mutableStateOf((listOf(defense.adviserId) + defense.panelistIds).toSet())
        }
        var adviserId by remember(defense.id) { mutableStateOf<String?>(defense.adviserId.ifBlank { null }) }
        var studentIds by remember(defense.id) { mutableStateOf(defense.students.toSet()) }

        val panelists = faculty.filter { it.uid in selectedIds && it.uid != adviserId }
        val adviser = faculty.firstOrNull { it.uid == adviserId }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (step == 1) {
                Text(
                    "Editing “${defense.title.ifBlank { "Untitled defense" }}”. Tick the professors on the " +
                        "panel and star your adviser. Changing the panel resets the chosen time and " +
                        "everyone's responses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PanelPicker(
                    faculty = faculty,
                    selectedIds = selectedIds,
                    adviserId = adviserId,
                    onToggleMember = { uid ->
                        selectedIds = if (uid in selectedIds) {
                            if (adviserId == uid) adviserId = null
                            selectedIds - uid
                        } else selectedIds + uid
                    },
                    onSetAdviser = { uid -> selectedIds = selectedIds + uid; adviserId = uid }
                )

                val canNext = adviser != null && panelists.isNotEmpty()
                Button(
                    onClick = { step = 2 },
                    enabled = canNext,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Next: members") }
            } else {
                Text(
                    "The group owner is always included. Add or remove co-students for a group thesis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StudentPicker(
                    students = allStudents,
                    ownerId = owner,
                    selectedIds = studentIds,
                    onToggle = { uid ->
                        studentIds = if (uid in studentIds) studentIds - uid else studentIds + uid
                    }
                )

                if (submit is SubmitState.Error) {
                    Text(
                        (submit as SubmitState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { step = 1 },
                        enabled = submit != SubmitState.Submitting,
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                    Button(
                        onClick = {
                            vm.updateMembers(
                                defenseId = defense.id,
                                adviserId = adviserId.orEmpty(),
                                panelistIds = panelists.map { it.uid },
                                studentIds = studentIds.toList()
                            )
                        },
                        enabled = adviser != null && panelists.isNotEmpty() &&
                            submit != SubmitState.Submitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (submit == SubmitState.Submitting) {
                            CircularProgressIndicator(
                                Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save changes")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
