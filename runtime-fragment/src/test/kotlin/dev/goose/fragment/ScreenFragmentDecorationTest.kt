package dev.goose.fragment

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.metro.GooseEnvironment
import dev.goose.metro.GooseGraphHolder
import dev.goose.runtime.GooseDecoration
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import kotlinx.serialization.Serializable
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@Serializable
data class DecorationTestScreen(val id: String) : Screen

@Serializable
data class LegacyBoundScreen(val id: String) : Screen

/** The CompositionLocal a decoration provides and the hosted screen reads — the whole point. */
val LocalPondTheme = staticCompositionLocalOf { "unthemed" }

class DecorationTestApp : Application(), GooseGraphHolder {
    // Tests assign a hand-built environment: this suite is ALSO the proof that goose runs
    // against GooseEnvironment with no Metro graph anywhere.
    lateinit var environment: Any
    override val gooseGraph: Any get() = environment
}

/**
 * Two contracts at once, both running WITHOUT a Metro-compiled graph:
 *
 * 1. Decorations: a ScreenFragment roots its own ComposeView, so the environment's contributed
 *    [GooseDecoration]s wrap the screen content there, and an empty set renders it bare.
 * 2. GooseEnvironment: the hand-assembled builder satisfies every fragment-host seam — the
 *    registry renders the screen, and a plain environment WITHOUT GooseFragmentAccessors
 *    installs fine (no legacy binders is a supported state); [GooseFragmentEnvironment] adds
 *    legacy binders on top.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = DecorationTestApp::class)
class ScreenFragmentDecorationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun environment(decorations: Set<GooseDecoration>): GooseEnvironment {
        val builder = GooseEnvironment.Builder()
            .addEntry(
                DecorationTestScreen::class,
                ScreenEntry { _, _ -> BasicText("pond: ${LocalPondTheme.current}") },
            )
        decorations.forEach { builder.addDecoration(it) }
        return builder.build()
    }

    private fun launch(graph: Any): FragmentActivity {
        ApplicationProvider.getApplicationContext<DecorationTestApp>().environment = graph
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.installGooseNavigator(android.R.id.content)
        return activity
    }

    @Test
    fun decorationsWrapFragmentHostedScreens() {
        val environment = environment(
            setOf(
                GooseDecoration { content ->
                    Column {
                        BasicText("decoration frame")
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalPondTheme provides "golden",
                        ) { content() }
                    }
                },
            ),
        )
        launch(environment).gooseNavigator.goTo(DecorationTestScreen("d1"))
        // The decoration's own UI renders, and its provided local REACHES the screen content.
        composeRule.onNodeWithText("decoration frame").assertIsDisplayed()
        composeRule.onNodeWithText("pond: golden").assertIsDisplayed()
    }

    @Test
    fun noDecorationsRendersScreenBare() {
        launch(environment(emptySet())).gooseNavigator.goTo(DecorationTestScreen("d1"))
        composeRule.onNodeWithText("pond: unthemed").assertIsDisplayed()
    }

    @Test
    fun fragmentEnvironmentBindsLegacyFragments() {
        val bound = Fragment()
        val activity = launch(
            GooseFragmentEnvironment(
                base = environment(emptySet()),
                binders = mapOf(LegacyBoundScreen::class to ScreenFragmentBinder { bound }),
            ),
        )
        activity.gooseNavigator.goTo(LegacyBoundScreen("x"))
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(bound, activity.supportFragmentManager.fragments.last())
    }
}
