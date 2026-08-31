package dev.goose.gaggle.auth.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goose.gaggle.auth.api.LoginScreen
import dev.goose.gaggle.auth.api.SessionManager
import dev.goose.runtime.GooseUi

/**
 * Demonstrates: a screen with NO ViewModel. Its one injected dependency (the app-scoped
 * SessionManager) arrives through the @GooseUi registration; tapping Sign in flips the app
 * shell into the logged-in graph. Deliberately button-only: no text input anywhere in Gaggle.
 */
@GooseUi(LoginScreen::class)
@Composable
fun LoginUi(modifier: Modifier, sessionManager: SessionManager) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🪿 Gaggle", style = MaterialTheme.typography.displaySmall)
        Text("Everything your pond needs", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { sessionManager.login("Goose Fan") }) {
            Text("Sign in as Goose Fan")
        }
    }
}
