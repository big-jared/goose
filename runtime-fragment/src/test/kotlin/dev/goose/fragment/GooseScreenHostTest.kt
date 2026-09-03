package dev.goose.fragment

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.metro.Goose
import dev.goose.metro.GooseGraphHolder
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@Serializable
data class HostedScreen(val id: String) : Screen

@Serializable
data class LegacyBoundScreen(val id: String) : Screen

/** A stand-in for an app's fragment base class, hosting via [gooseScreenView] with chrome. */
class TestHostFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = gooseScreenView { content ->
        Column {
            BasicText("host chrome")
            content()
        }
    }
}

class HostTestApp : Application(), GooseGraphHolder {
    // Assigned per test. This suite is ALSO the proof that goose runs against a hand-built
    // Goose with no Metro-compiled graph anywhere.
    lateinit var environment: Any
    override val gooseGraph: Any get() = environment
}

/**
 * The screen-host contract, running on a hand-built [Goose]:
 * - the default host (ScreenFragment) renders registered screens,
 * - a `screenHost` factory replaces it, the custom fragment's wrap composes around the
 *   screen, and the pushed fragment is the custom class (base-class lifecycle preserved),
 * - binders still win over the host for legacy screens.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = HostTestApp::class)
class GooseScreenHostTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun goose(): Goose = Goose.Builder()
        .addScreen(HostedScreen::class, ScreenEntry { _, _ -> BasicText("hosted content") })
        .build()

    private fun launch(
        graph: Any,
        screenHost: KClass<out Fragment> = ScreenFragment::class,
    ): FragmentActivity {
        ApplicationProvider.getApplicationContext<HostTestApp>().environment = graph
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.installGooseNavigator(android.R.id.content, screenHost = screenHost)
        return activity
    }

    @Test
    fun defaultHostRendersRegisteredScreens() {
        val activity = launch(goose())
        activity.gooseNavigator.goTo(HostedScreen("a"))
        composeRule.onNodeWithText("hosted content").assertIsDisplayed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.supportFragmentManager.fragments.last() is ScreenFragment)
    }

    @Test
    fun screenHostReplacesTheDefaultAndWrapsContent() {
        val activity = launch(goose(), screenHost = TestHostFragment::class)
        activity.gooseNavigator.goTo(HostedScreen("a"))
        // The custom fragment's chrome renders AND the screen content renders inside it.
        composeRule.onNodeWithText("host chrome").assertIsDisplayed()
        composeRule.onNodeWithText("hosted content").assertIsDisplayed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.supportFragmentManager.fragments.last() is TestHostFragment)
    }

    @Test
    fun bindersWinOverTheScreenHost() {
        val bound = Fragment()
        val environment = GooseFragmentEnvironment(
            goose = goose(),
            binders = mapOf(LegacyBoundScreen::class to ScreenFragmentBinder { bound }),
        )
        val activity = launch(environment, screenHost = TestHostFragment::class)
        activity.gooseNavigator.goTo(LegacyBoundScreen("x"))
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(bound, activity.supportFragmentManager.fragments.last())
    }
}
