package com.example.defensync.ui.schedule

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.defensync.ui.components.WeeklyAvailabilityEditor

@Composable
fun ScheduleScreen(vm: ScheduleViewModel = viewModel()) {
    val selection by vm.selection.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Default weekly schedule", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add the time ranges you're usually free. Use “Add” for split availability in a day. " +
                    "This is your template — every new defense group starts from it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            WeeklyAvailabilityEditor(
                selection = selection,
                onAddRange = vm::addRange,
                onUpdateRange = vm::updateRange,
                onRemoveRange = vm::removeRange
            )
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        ui.saving -> "Saving…"
                        ui.justSaved -> "Saved ✓"
                        ui.dirty -> "Unsaved changes"
                        else -> "All changes saved"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = vm::save, enabled = ui.dirty && !ui.saving) { Text("Save") }
            }
        }
    }
}
