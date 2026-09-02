package dev.goose.gaggle.auth.api

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import dev.goose.metro.GooseScopeAccessors
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import dev.goose.runtime.ScreenWithResult
import dev.goose.runtime.StackKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable

/**
 * Demonstrates: the flagship child-graph use case. Everything behind login lives in a
 * LoggedInScope graph: session-scoped dependencies (the user, the cart) exist only while
 * someone is signed in, and logging out disposes the graph and everything in it.
 */
abstract class LoggedInScope private constructor()

/** The signed-in user. One per login; a fresh one after re-login. */
@SingleIn(LoggedInScope::class)
@Inject
class UserSession(val userName: String)

@ContributesTo(LoggedInScope::class)
interface UserSessionAccessor {
    val userSession: UserSession
}

/**
 * The logged-in graph. Features contribute screens and dependencies to [LoggedInScope] from
 * their own modules; `GooseScope(graph)` in the app shell makes them resolvable.
 */
@GraphExtension(LoggedInScope::class)
interface LoggedInGraph : GooseScopeAccessors {

    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    interface Factory {
        fun createLoggedInGraph(@Provides userName: String): LoggedInGraph
    }
}

/**
 * The app's login state machine, app-scoped: it outlives every screen and activity recreation.
 * Compose observes [current] to switch between the login screen and the signed-in shell.
 * Logout is the deterministic disposal point for the logged-in graph.
 */
@SingleIn(AppScope::class)
@Inject
class SessionManager(
    private val graphFactory: LoggedInGraph.Factory,
) {
    var current: LoggedInGraph? by mutableStateOf(null)
        private set

    /** A deep link that arrived before login; honored right after (see MainActivity). */
    var pendingProductId: String? by mutableStateOf(null)

    fun login(userName: String) {
        current = graphFactory.createLoggedInGraph(userName)
    }

    fun logout() {
        current = null
    }
}

@ContributesTo(AppScope::class)
interface SessionManagerAccessor {
    val sessionManager: SessionManager
}

/** The signed-in shell's stack keys, shared so any feature can jump stacks via switchTo. */
object GaggleTabs {
    val Shop = StackKey("shop")
    val Cart = StackKey("cart")
    val Profile = StackKey("profile")
}

@Serializable
data object LoginScreen : Screen

// ---- Shell screens (the profile tab), declared here so any feature can navigate to them ----

@Serializable
data object ProfileScreen : Screen

/** Pops in with a scale+fade instead of the default slide (ScreenTransitions). */
@Serializable
data object TeamStatsScreen : Screen, ScreenTransitions {
    override fun enterTransition() =
        (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)))
            .togetherWith(fadeOut(tween(200)))

    override fun exitTransition() =
        fadeIn(tween(200))
            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)))
}

/**
 * A forced-choice confirmation dialog: dismissOnClickOutside = false, so tapping away does
 * NOT dismiss it — the user must pick a button (system back still answers "stay", as null).
 */
@Serializable
data object SignOutConfirmScreen : OverlayScreen, ScreenWithResult<SignOutChoice> {
    override fun dialogProperties() = DialogProperties(dismissOnClickOutside = false)
}

@Serializable
data class SignOutChoice(val signOut: Boolean) : PopResult

/** A LEGACY fragment destination with typed arguments (see the app's legacy package). */
@Serializable
data class OrderHistoryScreen(val orderCount: Int) : Screen

/** Another legacy fragment, typed args including a Parcelable at the registration. */
@Serializable
data class TermsScreen(val termsId: String, val revision: Int) : Screen

/** A legacy-owned scoped flow (fragment + child FragmentManager + its own graph). */
@Serializable
data object SupportFlowScreen : Screen
