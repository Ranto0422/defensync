package com.example.defensync.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.defensync.core.ServiceLocator
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

    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

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
                .onSuccess { _session.value = SessionState.LoggedIn(it) }
                .onFailure {
                    auth.signOut()
                    _session.value = SessionState.LoggedOut
                }
        }
    }

    fun clearError() { _form.value = _form.value.copy(error = null) }

    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return
        _form.value = AuthFormState(loading = true)
        viewModelScope.launch {
            runCatching { auth.login(email, password) }
                .onSuccess {
                    _form.value = AuthFormState()
                    _session.value = SessionState.LoggedIn(it)
                }
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
                .onSuccess {
                    _form.value = AuthFormState()
                    _session.value = SessionState.LoggedIn(it)
                }
                .onFailure { _form.value = AuthFormState(error = it.friendlyMessage()) }
        }
    }

    fun signOut() {
        auth.signOut()
        _session.value = SessionState.LoggedOut
    }

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
