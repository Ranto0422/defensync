package com.example.defensync.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.defensync.domain.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable message thread UI (list + input). Used by both the per-group chat and 1-on-1 DMs.
 * Long-pressing your own message offers Edit / Delete.
 */
@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    currentUid: String,
    sending: Boolean,
    onSend: (String) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    showSenderNames: Boolean = true,
    onEdit: (id: String, body: String) -> Unit = { _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onOpenDm: (userId: String, name: String) -> Unit = { _, _ -> }
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    var text by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete message?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget!!)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    Column(modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isMine = msg.senderId == currentUid,
                        showSenderName = showSenderNames,
                        onEditRequest = { editingId = it.id; text = it.body },
                        onDeleteRequest = { deleteTarget = it },
                        onOpenDm = onOpenDm
                    )
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (editingId != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Editing message",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingId = null; text = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(if (editingId != null) "Edit message…" else "Message…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    IconButton(
                        onClick = {
                            val id = editingId
                            if (id != null) {
                                onEdit(id, text)
                                editingId = null
                            } else {
                                onSend(text)
                            }
                            text = ""
                        },
                        enabled = text.isNotBlank() && !sending
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    isMine: Boolean,
    showSenderName: Boolean,
    onEditRequest: (ChatMessage) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onOpenDm: (userId: String, name: String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine && showSenderName && msg.senderName.isNotBlank()) {
            Text(
                "${msg.senderName}  ›  Message",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onOpenDm(msg.senderId, msg.senderName) }
                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
            )
        }
        Box {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        if (isMine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(14.dp)
                    )
                    .combinedClickable(
                        onClick = { if (!isMine) onOpenDm(msg.senderId, msg.senderName) },
                        onLongClick = { if (isMine) menuOpen = true }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(msg.body, style = MaterialTheme.typography.bodyLarge)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menuOpen = false; onEditRequest(msg) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuOpen = false; onDeleteRequest(msg.id) }
                )
            }
        }
        Spacer(Modifier.padding(top = 1.dp))
        Text(
            formatTime(msg.createdAt) + if (msg.edited) " · edited" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
