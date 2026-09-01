package dev.goose.fragment

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.ScreenRegistry
import dev.goose.runtime.GooseDecoration
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.providerOf
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Serializable
data class DecorationTestScreen(val id: String) : Screen

/** The CompositionLocal a decoration provides and the hosted screen reads — the whole point. */
val LocalPondTheme = staticCompositionLocalOf { "unthemed" }

private class FakeGraph(
    override val gooseDecorations: Set<GooseDecoration>,
) : GooseRuntimeAccessors, GooseFragmentAccessors {
    override val resultRouter = ResultRouter()
    override val navSerializersModule: SerializersModule =
        GooseRuntimeAccessors.provideNavSerializersModule(emptySet())
    override val screenEntries: Map<KClass<*>, ScreenEntry> = emptyMap()
    override val serializersModules: Set<SerializersModule> = emptySet()
    override val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder> = emptyMap()
    override val fragmentNavigationOverrides: Map<KClass<*>, FragmentScreenNavigation> = emptyMap()
    override val screenRegistry = ScreenRegistry(
        mapOf<KClass<*>, Provider<ScreenEntry>>(
            DecorationTestScreen::class to providerOf(
                ScreenEntry { _, _ -> BasicText("pond: ${LocalPondTheme.current}") }
            ),
        ),
    )
}

class DecorationTestApp : Application(), GooseGraphHolder {
    var decorations: Set<GooseDecoration> = emptySet()
    override val gooseGraph: Any by lazy { FakeGraph(decorations) }
}

/**
 * The fragment-host decoration contract: a ScreenFragment roots its own ComposeView, so the
 * graph's contributed [GooseDecoration]s (app theme, CompositionLocal providers) wrap the
 * screen content there — and an empty contribution set renders the screen bare, unchanged.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = DecorationTestApp::class)
class ScreenFragmentDecorationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun showScreen(decorations: Set<GooseDecoration>) {
        ApplicationProvider.getApplicationContext<DecorationTestApp>().decorations = decorations
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.installGooseNavigator(android.R.id.content)
        activity.gooseNavigator.goTo(DecorationTestScreen("d1"))
    }

    @Test
    fun decorationsWrapFragmentHostedScreens() {
        showScreen(
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
        // The decoration's own UI renders, and its provided local REACHES the screen content.
        composeRule.onNodeWithText("decoration frame").assertIsDisplayed()
        composeRule.onNodeWithText("pond: golden").assertIsDisplayed()
    }

    @Test
    fun noDecorationsRendersScreenBare() {
        showScreen(emptySet())
        composeRule.onNodeWithText("pond: unthemed").assertIsDisplayed()
    }
}
