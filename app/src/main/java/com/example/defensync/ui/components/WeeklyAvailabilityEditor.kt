package com.example.defensync.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.Weekday

/**
 * Per-day availability editor. Each day lists its time ranges (From – To), with a "+ Add" to append
 * another range (for split availability) and a remove button per range. Not scrollable itself.
 */
@Composable
fun WeeklyAvailabilityEditor(
    selection: Map<Weekday, List<TimeRange>>,
    onAddRange: (Weekday) -> Unit,
    onUpdateRange: (Weekday, Int, TimeRange) -> Unit,
    onRemoveRange: (Weekday, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Weekday.entries.forEach { day ->
            val ranges = selection[day].orEmpty()
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            day.full,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { onAddRange(day) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (ranges.isEmpty()) {
                        Text(
                            "Not available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ranges.forEachIndexed { index, range ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TimeBox(
                                    value = range.start,
                                    onValueChange = { onUpdateRange(day, index, range.copy(start = it)) },
                                    label = "From",
                                    modifier = Modifier.weight(1f)
                                )
                                Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TimeBox(
                                    value = range.end,
                                    onValueChange = { onUpdateRange(day, index, range.copy(end = it)) },
                                    label = "To",
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onRemoveRange(day, index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                            if (!range.isValid) {
                                Text(
                                    "End time must be after start",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
