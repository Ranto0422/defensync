package com.example.defensync.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.defensync.domain.model.Role
import com.example.defensync.ui.session.SessionViewModel

@Composable
fun AuthScreen(vm: SessionViewModel) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf(Role.STUDENT) }
    var studentNumber by remember { mutableStateOf("") }
    val form by vm.form.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "DefenSync",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Align defense schedules with your panel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (isSignUp) "Create account" else "Sign in",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(16.dp))

                if (isSignUp) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSignUp) {
                    Spacer(Modifier.height(8.dp))
                    PasswordRules(password)

                    Spacer(Modifier.height(12.dp))
                    val mismatch = confirmPassword.isNotEmpty() && confirmPassword != password
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = if (confirmVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                Icon(
                                    if (confirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (confirmVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mismatch) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Passwords don't match",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isSignUp) {
                    Spacer(Modifier.height(16.dp))
                    Text("I am a…", style = MaterialTheme.typography.labelLarge)
                    Role.entries.forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = role == r, onClick = { role = r }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = role == r, onClick = { role = r })
                            Text(r.label)
                        }
                    }

                    // Students are searched by name + student number when added to a group.
                    if (role == Role.STUDENT) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = studentNumber,
                            onValueChange = { studentNumber = it },
                            label = { Text("Student number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (form.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        form.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(20.dp))
                val signUpReady = name.isNotBlank() &&
                    (role != Role.STUDENT || studentNumber.isNotBlank()) &&
                    passwordRules(password).all { it.met } &&
                    confirmPassword == password
                Button(
                    onClick = {
                        if (isSignUp) {
                            vm.signUp(name, email, password, role, studentNumber.takeIf { role == Role.STUDENT })
                        } else {
                            vm.signIn(email, password)
                        }
                    },
                    enabled = !form.loading && (!isSignUp || signUpReady),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (form.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (isSignUp) "Create account" else "Sign in")
                    }
                }

                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSignUp) "Already have an account? Sign in"
                        else "New here? Create an account"
                    )
                }
            }
        }
    }
}

private data class PasswordRule(val label: String, val met: Boolean)

/** The password requirements for a new account and whether the given value satisfies each. */
private fun passwordRules(password: String): List<PasswordRule> = listOf(
    PasswordRule("At least 8 characters", password.length >= 8),
    PasswordRule("Contains an uppercase letter", password.any { it.isUpperCase() }),
    PasswordRule("Contains a lowercase letter", password.any { it.isLowerCase() }),
    PasswordRule("Contains a number", password.any { it.isDigit() }),
    PasswordRule("Contains a special character", password.any { !it.isLetterOrDigit() })
)

@Composable
private fun PasswordRules(password: String) {
    val met = Color(0xFF2E7D32)
    val unmet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        passwordRules(password).forEach { rule ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rule.met) "✓" else "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.met) met else unmet
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    rule.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.met) met else unmet
                )
            }
        }
    }
}
