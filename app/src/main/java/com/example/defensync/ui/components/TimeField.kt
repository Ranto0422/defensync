package com.example.defensync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.defensync.domain.model.TimeConfig

/**
 * A compact time control: shows the current time as a tidy chip (e.g. "4:35 PM") with a clock icon,
 * and opens a Material3 time dialog on tap. Because the chip sizes to its content the AM/PM label is
 * always fully visible, and the dialog gives full minute precision (10:33, 4:35, …).
 *
 * [value]/[onValueChange] are minutes-from-midnight. Confirmed values are clamped into
 * [[minMinutes], [maxMinutes]] (defaults to the day window).
 */
@Composable
fun TimeField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minMinutes: Int = TimeConfig.DAY_START,
    maxMinutes: Int = TimeConfig.DAY_END
) {
    var showDialog by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { showDialog = true },
        label = { Text(TimeConfig.label(value), maxLines = 1) },
        leadingIcon = {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.height(18.dp)
            )
        },
        modifier = modifier
    )

    if (showDialog) {
        TimePickerDialog(
            title = label,
            initialMinutes = value.coerceIn(minMinutes, maxMinutes),
            onDismiss = { showDialog = false },
            onConfirm = { picked ->
                onValueChange(picked.coerceIn(minMinutes, maxMinutes))
                showDialog = false
            }
        )
    }
}

/**
 * A time control rendered as a filled rounded box showing the current time (e.g. "9:00 AM"), no
 * caption or icon. Tapping opens the same [TimePickerDialog] as [TimeField]. Minutes-from-midnight.
 */
@Composable
fun TimeBox(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minMinutes: Int = TimeConfig.DAY_START,
    maxMinutes: Int = TimeConfig.DAY_END
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            TimeConfig.label(value),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }

    if (showDialog) {
        TimePickerDialog(
            title = label,
            initialMinutes = value.coerceIn(minMinutes, maxMinutes),
            onDismiss = { showDialog = false },
            onConfirm = { picked ->
                onValueChange(picked.coerceIn(minMinutes, maxMinutes))
                showDialog = false
            }
        )
    }
}

/**
 * Material3 time dialog. Defaults to keypad entry ([TimeInput]) — the fastest way to type an exact
 * minute like ":33" — with a toggle to the clock dial ([TimePicker]). [initialMinutes] and the
 * confirmed value are minutes-from-midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = initialMinutes % 60,
        is24Hour = false
    )
    var keypad by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (keypad) TimeInput(state = state) else TimePicker(state = state)
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { keypad = !keypad }) {
                        Text(if (keypad) "Use clock" else "Use keypad")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
