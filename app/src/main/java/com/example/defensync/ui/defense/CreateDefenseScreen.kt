package com.example.defensync.ui.defense

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.defensync.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDefenseScreen(
    student: UserProfile,
    vm: DefenseViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val faculty by vm.faculty.collectAsStateWithLifecycle()
    val allStudents by vm.students.collectAsStateWithLifecycle()
    val submit by vm.submit.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(1) }
    var title by remember { mutableStateOf("") }
    // The committee is one set of professors; exactly one of them is starred as adviser.
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var adviserId by remember { mutableStateOf<String?>(null) }
    var coStudentIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val adviser = faculty.firstOrNull { it.uid == adviserId }
    val panelists = faculty.filter { it.uid in selectedIds && it.uid != adviserId }

    LaunchedEffect(Unit) { vm.refreshPeople() }
    LaunchedEffect(submit) {
        if (submit is SubmitState.Success) {
            vm.resetSubmit()
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (step == 1) "New group · Panel" else "New group · Members") },
                navigationIcon = {
                    IconButton(onClick = { if (step == 2) step = 1 else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
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
                    "Name the defense and build your committee: tick the professors on your panel and " +
                        "star the one who's your adviser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Thesis / defense title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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

                val canNext = title.isNotBlank() && adviser != null && panelists.isNotEmpty()
                Button(
                    onClick = { step = 2 },
                    enabled = canNext,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Next: add members") }
            } else {
                Text(
                    "Add co-students if this is a group thesis. You're included as the owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StudentPicker(
                    students = allStudents,
                    ownerId = student.uid,
                    selectedIds = coStudentIds,
                    onToggle = { uid ->
                        coStudentIds = if (uid in coStudentIds) coStudentIds - uid else coStudentIds + uid
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
                            vm.createDefense(
                                title, student, adviser!!, panelists,
                                coStudentIds.filter { it != student.uid }
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
                            Text("Create group")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
