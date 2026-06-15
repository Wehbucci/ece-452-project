package com.example.grasp.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.grasp.ui.theme.GraspTheme

/**
 * Login screen (View).
 *
 * ──────────────────────────────────────────────────────────────────────────────────────
 * HOW EVERY SCREEN IN GRASP IS WIRED FOR MVP:
 *
 *   1. The Composable owns small `remember`ed UI states (what's on screen right now).
 *   2. It creates its [LoginPresenter] (held across recompositions with `remember`).
 *   3. It builds an anonymous `View` whose methods just write those UI states / navigate.
 *   4. `DisposableEffect` attaches the View to the Presenter on enter, detaches on exit.
 *   5. User events (typing, taps) call `presenter.onX(...)`; the Presenter decides what to
 *      do and pushes results back through the View. The Composable holds NO logic.
 *
 * Note on naming: navigation lambdas (e.g. [onAuthenticated]) are named differently from the
 * View's methods (e.g. `onLoggedIn`) on purpose where same names would shadow and recurse.
 * ──────────────────────────────────────────────────────────────────────────────────────
 *
 * @param onAuthenticated called once login succeeds; the nav layer routes on to Home.
 */
@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    presenterFactory: () -> LoginContract.Presenter = { LoginPresenter() },
) {
    // (1) UI state
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // (2) Presenter
    val presenter = remember { presenterFactory() }

    // (3) View implementation
    val view = remember(onAuthenticated) {
        object : LoginContract.View {
            override fun showError(message: String?) { errorText = message }
            override fun showLoading(loading: Boolean) { isLoading = loading }
            override fun onLoggedIn() = onAuthenticated()
        }
    }

    // (4) Attach / detach
    DisposableEffect(presenter, view) {
        presenter.attach(view)
        onDispose { presenter.detach() }
    }

    // (5) The dumb UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Grasp",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Turn any topic into a clear path.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorText = null },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = errorText != null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorText != null) {
            Text(
                text = errorText!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { presenter.onSubmit(email, password) },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Log in")
            }
        }

        // Account creation is a placeholder for the skeleton (see LoginPresenter TODO).
        TextButton(onClick = { presenter.onSubmit(email, password) }) {
            Text("New here? Create an account")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    GraspTheme { LoginScreen(onAuthenticated = {}) }
}
