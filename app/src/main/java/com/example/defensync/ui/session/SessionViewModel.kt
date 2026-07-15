package com.example.defensync.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.defensync.core.ServiceLocator
import com.example.defensync.core.network.SavedAccount
import com.example.defensync.domain.model.Role
import com.example.defensync.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the user is in the auth lifecycle. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val profile: UserProfile) : SessionState
}

/** Transient state for the login/sign-up forms. */
data class AuthFormState(
    val loading: Boolean = false,
    val error: String? = null
)

class SessionViewModel : ViewModel() {

    private val auth = ServiceLocator.authRepository
    private val server = ServiceLocator.serverConfig

    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    // Accounts remembered on this device + whether we're currently adding another one.
    private val _accounts = MutableStateFlow(auth.accounts)
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _addingAccount = MutableStateFlow(false)
    val addingAccount: StateFlow<Boolean> = _addingAccount.asStateFlow()

    // One-shot message shown as a snackbar after switching/signing out (e.g. "Switched to …").
    private val _switchMessage = MutableStateFlow<String?>(null)
    val switchMessage: StateFlow<String?> = _switchMessage.asStateFlow()
    fun consumeSwitchMessage() { _switchMessage.value = null }

    // Editable API address, so a physical device can point at the LAN machine running XAMPP.
    private val _serverUrl = MutableStateFlow(server.baseUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        restoreSession()
    }

    /** On launch, if we have a saved token, try to fetch the profile; otherwise show login. */
    private fun restoreSession() {
        if (!auth.hasToken) {
            _session.value = SessionState.LoggedOut
            return
        }
        viewModelScope.launch {
            runCatching { auth.me() }
                .onSuccess {
                    auth.rememberActive(it)       // add a pre-existing session to the switcher list
                    _accounts.value = auth.accounts
                    _session.value = SessionState.LoggedIn(it)
                }
                .onFailure {
                    auth.signOut()
                    _session.value = SessionState.LoggedOut
                }
        }
    }

    fun clearError() { _form.value = _form.value.copy(error = null) }

    /** Persist the API base URL (normalized) and apply it to every subsequent request. */
    fun updateServerUrl(url: String) {
        server.baseUrl = url
        _serverUrl.value = server.baseUrl
    }

    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return
        _form.value = AuthFormState(loading = true)
        viewModelScope.launch {
            runCatching { auth.login(email, password) }
                .onSuccess { onAuthed(it) }
                .onFailure { _form.value = AuthFormState(error = it.friendlyMessage()) }
        }
    }

    fun signUp(name: String, email: String, password: String, role: Role, studentNumber: String?) {
        if (name.isBlank()) {
            _form.value = AuthFormState(error = "Please enter your name")
            return
        }
        if (role == Role.STUDENT && studentNumber.isNullOrBlank()) {
            _form.value = AuthFormState(error = "Please enter your student number")
            return
        }
        if (!validate(email, password)) return
        _form.value = AuthFormState(loading = true)
        viewModelScope.launch {
            runCatching { auth.register(name, email, password, role, studentNumber) }
                .onSuccess { onAuthed(it) }
                .onFailure { _form.value = AuthFormState(error = it.friendlyMessage()) }
        }
    }

    private fun onAuthed(profile: UserProfile) {
        _form.value = AuthFormState()
        _addingAccount.value = false
        _accounts.value = auth.accounts
        _session.value = SessionState.LoggedIn(profile)
    }

    /** Show the sign-in screen to add another account without dropping the current session. */
    fun addAccount() {
        _form.value = AuthFormState()
        _addingAccount.value = true
    }

    fun cancelAddAccount() {
        _form.value = AuthFormState()
        _addingAccount.value = false
    }

    /**
     * One-tap switch to a remembered account. Updates the session IN PLACE (no Loading state), so the
     * user stays on whatever screen they're on and sees the change instantly; a snackbar confirms it.
     * The full profile (availability, student number) is then fetched in the background.
     */
    fun switchAccount(account: SavedAccount) {
        val current = (_session.value as? SessionState.LoggedIn)?.profile
        if (current?.uid == account.uid) return
        auth.activate(account)
        _session.value = SessionState.LoggedIn(account.toProfile())
        _accounts.value = auth.accounts
        _switchMessage.value = "Switched to ${account.name.ifBlank { account.email }}"
        enrichActiveProfile()
    }

    /** Fetch the full profile for the active token and update in place (stays LoggedIn). */
    private fun enrichActiveProfile() {
        viewModelScope.launch {
            runCatching { auth.me() }
                .onSuccess {
                    auth.rememberActive(it)
                    _accounts.value = auth.accounts
                    _session.value = SessionState.LoggedIn(it)
                }
                .onFailure {
                    // Token no longer valid (e.g. signed in elsewhere) — ask them to sign in again.
                    _accounts.value = auth.accounts
                    _addingAccount.value = true
                }
        }
    }

    fun signOut() {
        val currentUid = (_session.value as? SessionState.LoggedIn)?.profile?.uid
        val next = currentUid?.let { auth.signOutActive(it) }
        _accounts.value = auth.accounts
        if (next == null) {
            auth.signOut()
            _session.value = SessionState.LoggedOut
            return
        }
        // Fall back to the next remembered account in place, staying on the current screen.
        _session.value = SessionState.LoggedIn(next.toProfile())
        _switchMessage.value = "Signed out · now ${next.name.ifBlank { next.email }}"
        enrichActiveProfile()
    }

    private fun SavedAccount.toProfile() =
        UserProfile(uid = uid, name = name, email = email, role = Role.from(role))

    private fun validate(email: String, password: String): Boolean {
        if (email.isBlank()) {
            _form.value = AuthFormState(error = "Please enter your email")
            return false
        }
        if (password.length < 6) {
            _form.value = AuthFormState(error = "Password must be at least 6 characters")
            return false
        }
        return true
    }

    private fun Throwable.friendlyMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}
