package com.example.defensync.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    defenseId: String,
    title: String,
    currentUid: String,
    onBack: () -> Unit,
    onOpenDm: (userId: String, name: String) -> Unit,
    vm: ChatViewModel = viewModel()
) {
    LaunchedEffect(defenseId) { vm.start(defenseId) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "Group chat" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        ChatContent(
            messages = messages,
            currentUid = currentUid,
            sending = sending,
            onSend = vm::send,
            emptyText = "No messages yet.\nCoordinate a time with your panel here.",
            modifier = Modifier.fillMaxSize().padding(padding),
            showSenderNames = true,
            onEdit = vm::edit,
            onDelete = vm::delete,
            onOpenDm = onOpenDm
        )
    }
}
