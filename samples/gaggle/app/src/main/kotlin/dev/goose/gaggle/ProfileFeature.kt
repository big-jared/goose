package dev.goose.gaggle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.auth.api.OrderHistoryScreen
import dev.goose.gaggle.auth.api.ProfileScreen
import dev.goose.gaggle.auth.api.SessionManager
import dev.goose.gaggle.auth.api.SignOutChoice
import dev.goose.gaggle.auth.api.SignOutConfirmScreen
import dev.goose.gaggle.auth.api.SupportFlowScreen
import dev.goose.gaggle.auth.api.TeamStatsScreen
import dev.goose.gaggle.auth.api.TermsScreen
import dev.goose.gaggle.auth.api.UserSession
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.StateHolder
import dev.goose.runtime.rememberStateHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Awaits the sign-out dialog's answer from a retained holder (the goToForResult contract), so
 * the pending question survives rotation while the dialog is up.
 */
class ProfileHolder(
    private val navigator: Navigator,
    private val sessionManager: SessionManager,
) : StateHolder<Unit>(Unit) {

    fun askSignOut() {
        holderScope.launch {
            val choice = navigator.goToForResult(SignOutConfirmScreen)
            if (choice?.signOut == true) sessionManager.logout()
        }
    }
}

/**
 * Demonstrates: the app module registering screens too, a scope-registered screen injecting
 * the session user, and logout as the deterministic end of the logged-in graph — gated behind
 * a forced-choice OverlayScreen dialog. The links below are the legacy corner: unmigrated
 * fragments riding the Nav3 stack.
 */
@GooseUi(ProfileScreen::class, scope = LoggedInScope::class)
@Composable
fun ProfileUi(modifier: Modifier, user: UserSession, sessionManager: SessionManager) {
    val navigator = LocalNavigator.current
    val holder = rememberStateHolder { nav -> ProfileHolder(nav, sessionManager) }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hi, ${user.userName}", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { navigator.goTo(TeamStatsScreen) }) { Text("Team stats") }
        OutlinedButton(onClick = { navigator.goTo(OrderHistoryScreen(orderCount = 0)) }) {
            Text("Order history (legacy)")
        }
        OutlinedButton(onClick = { navigator.goTo(TermsScreen("TOS-7", revision = 3)) }) {
            Text("Terms (legacy)")
        }
        OutlinedButton(onClick = { navigator.goTo(SupportFlowScreen) }) { Text("Support chat") }
        Button(onClick = holder::askSignOut) { Text("Log out") }
    }
}

/**
 * Demonstrates: a custom dialog as a screen with non-default window behavior — the screen's
 * dialogProperties disable outside-tap dismissal, so this question requires a real answer
 * (system back still counts as "stay": a null result).
 */
@GooseUi(SignOutConfirmScreen::class, scope = LoggedInScope::class)
@Composable
fun SignOutConfirmUi(modifier: Modifier, user: UserSession) {
    val navigator = LocalNavigator.current
    Card(modifier.fillMaxWidth(0.92f)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Leaving the pond?", style = MaterialTheme.typography.titleLarge)
            Text("Sign out of ${user.userName}? Your cart stays with the session and will be gone.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { navigator.pop() }) { Text("Stay") }
                Button(onClick = { navigator.pop(SignOutChoice(signOut = true)) }) { Text("Sign out") }
            }
        }
    }
}

data class TeamStatsState(
    val geeseSpotted: Int = 0,
    val honksHeard: Int = 0,
)

/**
 * Demonstrates: the presenter-agnostic StateHolder — pure Kotlin + coroutines, no Mavericks,
 * same entry-scoped lifecycle. The "Open stats again" button pushes the SAME screen value:
 * per-push identity gives each push independent state.
 */
class TeamStatsHolder(private val navigator: Navigator) :
    StateHolder<TeamStatsState>(TeamStatsState()) {

    fun spotGoose() = setState { copy(geeseSpotted = geeseSpotted + 1) }

    fun listenForHonk() {
        holderScope.launch {
            delay(50)
            setState { copy(honksHeard = honksHeard + 1) }
        }
    }

    fun done() {
        navigator.pop()
    }
}

@GooseUi(TeamStatsScreen::class)
@Composable
fun TeamStatsUi(modifier: Modifier) {
    val holder = rememberStateHolder { navigator -> TeamStatsHolder(navigator) }
    val state by holder.state.collectAsState()
    val navigator = LocalNavigator.current
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Team stats", style = MaterialTheme.typography.headlineMedium)
        Text("Geese spotted: ${state.geeseSpotted}")
        Text("Honks heard: ${state.honksHeard}")
        OutlinedButton(onClick = holder::spotGoose) { Text("Spot a goose") }
        OutlinedButton(onClick = holder::listenForHonk) { Text("Listen for a honk") }
        OutlinedButton(onClick = { navigator.goTo(TeamStatsScreen) }) { Text("Open stats again") }
        Button(onClick = holder::done) { Text("Back to profile") }
    }
}
