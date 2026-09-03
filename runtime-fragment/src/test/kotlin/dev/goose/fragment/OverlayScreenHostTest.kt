@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.goose.fragment

import android.os.Looper
import androidx.compose.foundation.text.BasicText
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.metro.Goose
import dev.goose.runtime.Overlay
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.PopResult
import dev.goose.runtime.Presentation
import dev.goose.runtime.PresentedScreen
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenWithResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Serializable
data class HelpOverlayScreen(val topic: String) : OverlayScreen, ScreenWithResult<HelpAnswer>

@Serializable
data class HelpAnswer(val value: String) : PopResult

/** An app-defined presentation carrying only the dialog facet — no fragment binding anywhere. */
object TestDialogPresentation : Presentation, Overlay

@Serializable
data class PresentedDialogScreen(val id: String) : PresentedScreen {
    override val presentation: Presentation get() = TestDialogPresentation
}

object TestSheetPresentation : Presentation

@Serializable
data class SheetScreen(val id: String) : PresentedScreen {
    override val presentation: Presentation get() = TestSheetPresentation
}

/**
 * The fragment-host half of the [Overlay] facet and the presentation-keyed navigation map:
 * - an [OverlayScreen] (and a screen whose [Presentation] carries the facet) shows in a
 *   [ScreenDialogFragment] riding the back stack — no binding, no override,
 * - dismissal by any path resolves awaiting callers through the same back-stack rail as
 *   full-screen entries (null on dismiss, the passed result on pop),
 * - a `presentationNavigations` binding routes every screen using its token's class, and a
 *   per-screen override still beats it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = HostTestApp::class)
class OverlayScreenHostTest {

    private fun goose(): Goose = Goose.Builder()
        .addScreen(HelpOverlayScreen::class, ScreenEntry { _, _ -> BasicText("help dialog") })
        .addScreen(PresentedDialogScreen::class, ScreenEntry { _, _ -> BasicText("presented dialog") })
        .build()

    private fun launch(): FragmentActivity {
        ApplicationProvider.getApplicationContext<HostTestApp>().environment =
            GooseFragmentEnvironment(goose())
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.installGooseNavigator(android.R.id.content)
        return activity
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun FragmentActivity.dialogFragment(): ScreenDialogFragment? =
        supportFragmentManager.fragments.filterIsInstance<ScreenDialogFragment>().singleOrNull()

    @Test
    fun overlayScreenShowsInTheDialogHostOnTheBackStack() {
        val activity = launch()
        activity.gooseNavigator.goTo(HelpOverlayScreen("returns"))
        idle()

        val dialog = activity.dialogFragment()
        assertNotNull(dialog)
        assertNotNull(dialog!!.dialog)
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)

        activity.gooseNavigator.pop()
        idle()
        assertNull(activity.dialogFragment())
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun presentationOverlayFacetShowsInTheDialogHostToo() {
        val activity = launch()
        activity.gooseNavigator.goTo(PresentedDialogScreen("a"))
        idle()

        assertNotNull(activity.dialogFragment())
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun dismissingTheDialogResolvesTheAwaitingCallerWithNull() {
        val activity = launch()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val caller = scope.async { activity.gooseNavigator.goToForResult(HelpOverlayScreen("q")) }
        idle()

        activity.dialogFragment()!!.dismiss()
        idle()

        assertTrue(caller.isCompleted)
        assertNull(caller.getCompleted())
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun poppingWithAResultAnswersTheCaller() {
        val activity = launch()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val caller = scope.async { activity.gooseNavigator.goToForResult(HelpOverlayScreen("q")) }
        idle()

        activity.gooseNavigator.pop(HelpAnswer("42"))
        idle()

        assertEquals(HelpAnswer("42"), caller.getCompleted())
    }

    @Test
    fun presentationNavigationRoutesEveryScreenUsingTheToken() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val handled = mutableListOf<Screen>()
        val navigator = FragmentNavigator(
            fragmentManager = activity.supportFragmentManager,
            containerId = android.R.id.content,
            binders = emptyMap(),
            resultRouter = ResultRouter(),
            presentationNavigations = mapOf(
                TestSheetPresentation::class to FragmentScreenNavigation { request ->
                    handled += request.screen
                    assertEquals(TestSheetPresentation, request.presentation)
                },
            ),
            stackTag = "test-stack",
        )

        navigator.goTo(SheetScreen("a"))
        navigator.goTo(SheetScreen("b"))

        assertEquals(listOf<Screen>(SheetScreen("a"), SheetScreen("b")), handled)
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun perScreenOverrideBeatsThePresentationBinding() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val routes = mutableListOf<String>()
        val navigator = FragmentNavigator(
            fragmentManager = activity.supportFragmentManager,
            containerId = android.R.id.content,
            binders = emptyMap(),
            resultRouter = ResultRouter(),
            navigationOverrides = mapOf(
                SheetScreen::class to FragmentScreenNavigation { routes += "screen" },
            ),
            presentationNavigations = mapOf(
                TestSheetPresentation::class to FragmentScreenNavigation { routes += "presentation" },
            ),
            stackTag = "test-stack",
        )

        navigator.goTo(SheetScreen("a"))

        assertEquals(listOf("screen"), routes)
    }
}
