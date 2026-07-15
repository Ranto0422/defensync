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
    val addingAccount by sessionVm.addingAccount.collectAsStateWithLifecycle()
    val accounts by sessionVm.accounts.collectAsStateWithLifecycle()
    val switchMessage by sessionVm.switchMessage.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            // Adding another account: show sign-in, but let them back out to the current session.
            addingAccount -> AuthScreen(sessionVm, onCancel = sessionVm::cancelAddAccount)

            session is SessionState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            session is SessionState.LoggedOut -> AuthScreen(sessionVm)

            session is SessionState.LoggedIn -> MainScaffold(
                profile = (session as SessionState.LoggedIn).profile,
                accounts = accounts,
                switchMessage = switchMessage,
                onSwitchMessageShown = sessionVm::consumeSwitchMessage,
                onSwitchAccount = sessionVm::switchAccount,
                onAddAccount = sessionVm::addAccount,
                onSignOut = sessionVm::signOut
            )
        }
    }
}
