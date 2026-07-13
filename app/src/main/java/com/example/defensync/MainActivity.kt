package com.example.defensync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.defensync.ui.MainScaffold
import com.example.defensync.ui.auth.AuthScreen
import com.example.defensync.ui.session.SessionState
import com.example.defensync.ui.session.SessionViewModel
import com.example.defensync.ui.theme.DefensyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DefensyncTheme {
                DefenSyncApp()
            }
        }
    }
}

@Composable
fun DefenSyncApp(sessionVm: SessionViewModel = viewModel()) {
    val session by sessionVm.session.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = session) {
            SessionState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            SessionState.LoggedOut -> AuthScreen(sessionVm)

            is SessionState.LoggedIn -> MainScaffold(
                profile = state.profile,
                onSignOut = sessionVm::signOut
            )
        }
    }
}
