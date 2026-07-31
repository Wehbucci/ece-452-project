package com.example.grasp.ui.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grasp.ui.components.GameButton
import com.example.grasp.ui.components.GameCard
import com.example.grasp.ui.components.SproutGlyph
import com.example.grasp.ui.theme.FredokaFamily
import com.example.grasp.ui.theme.GameDanger
import com.example.grasp.ui.theme.GameDangerTint
import com.example.grasp.ui.theme.GraspTheme
import com.example.grasp.ui.theme.NunitoFamily
import com.example.grasp.ui.theme.PathCard
import com.example.grasp.ui.theme.PathFaint
import com.example.grasp.ui.theme.PathInk
import com.example.grasp.ui.theme.PathMuted
import com.example.grasp.ui.theme.PathNodeCurrent
import com.example.grasp.ui.theme.PathNodeDone
import com.example.grasp.ui.theme.PathScreenBg

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
 * Visually it is the app's first impression, so it sets the game shell up front: the sprout
 * mark, the lilac background, one bevelled card holding both fields, and the same chunky button
 * used everywhere else.
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
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }

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
            .background(PathScreenBg)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(78.dp)
                .background(PathCard, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SproutGlyph(
                stem = PathNodeDone,
                leaf = PathNodeCurrent,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Grasp",
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            color = PathInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isSignUpMode) "Create your account to start a path."
            else "Turn any topic into a clear path.",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = PathMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                FieldLabel("EMAIL")
                AuthField(
                    value = email,
                    onValueChange = { email = it; errorText = null },
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                )
                if (isSignUpMode) {
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("USERNAME")
                    AuthField(
                        value = username,
                        onValueChange = { username = it; errorText = null },
                        placeholder = "e.g. jordan_uw",
                        keyboardType = KeyboardType.Text,
                    )
                }
                Spacer(Modifier.height(16.dp))
                FieldLabel("PASSWORD")
                AuthField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    placeholder = "••••••••",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )
            }
        }

        if (errorText != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameDangerTint, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = errorText!!,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = GameDanger,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        GameButton(
            label = if (isSignUpMode) "Create account" else "Log in",
            onClick = {
                if (isSignUpMode) presenter.onSignUp(email, password, username)
                else presenter.onLogin(email, password)
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            leading = if (isLoading) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 0.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(10.dp))
                }
            } else {
                null
            },
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (isSignUpMode) "Already have an account? Log in"
            else "New here? Create an account",
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = PathNodeCurrent,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(enabled = !isLoading) {
                    isSignUpMode = !isSignUpMode
                    errorText = null // Clear errors when switching modes.
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )

        Spacer(Modifier.height(48.dp))
    }
}

/** Small caps label above a field — quieter than a floating Material label. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = PathMuted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** A borderless field on a tinted tray, matching Home's prompt box. */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PathScreenBg, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = PathInk,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
            cursorBrush = SolidColor(PathNodeCurrent),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = PathFaint,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    GraspTheme { LoginScreen(onAuthenticated = {}) }
}
