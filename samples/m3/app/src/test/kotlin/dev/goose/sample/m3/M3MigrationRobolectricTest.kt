package dev.goose.sample.m3

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.sample.m3.settings.SettingsActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3MigrationRobolectricTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    /**
     * The whole migration story in one pass, crossing the fragment/compose boundary four times:
     * legacy fragment → compose screen (on the fragment stack) → shared activity-scoped VM →
     * compose VM awaiting a LEGACY fragment's result → compose popping a typed result back to the
     * LEGACY home fragment's VM.
     */
    @Test
    fun fragmentAndComposeInteropRoundTrip() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Legacy home renders; shared counter starts at 0.
            onView(withText(containsString("Shared counter: 0"))).check(matches(isDisplayed()))
            onView(withText(containsString("Shared counter: 0"))).perform(click())

            // Into the migrated compose profile, riding the legacy fragment back stack.
            onView(withText("Open profile (compose screen)")).perform(click())
            composeRule.waitFor("Migrated Profile (compose)")

            // Same CounterViewModel instance as the fragment: already at 1, increment to 2.
            composeRule.onNodeWithText("Shared counter from legacy home: 1").assertIsDisplayed()
            composeRule.onNodeWithText("+1 (same VM as fragment)").performClick()
            composeRule.waitFor("Shared counter from legacy home: 2")

            // Compose VM awaits a legacy fragment's typed result.
            composeRule.onNodeWithText("Ask legacy detail for a result").performClick()
            onView(withText("Send result and close")).check(matches(isDisplayed()))
            onView(withText("Send result and close")).perform(click())
            composeRule.waitFor("Legacy answered: hello from legacy detail asked-by-compose")

            // Compose pops a typed result back to the legacy home fragment's VM.
            composeRule.onNodeWithText("Done").performClick()
            onView(withText("profile → counter was 2")).check(matches(isDisplayed()))
        }
    }

    /** Direction 2: a legacy fragment hosted on a Nav3-owned stack via FragmentScreen. */
    @Test
    fun legacyFragmentOnNav3Stack() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            composeRule.waitFor("Settings (converted, Nav3 stack)")
            composeRule.onNodeWithText("About (legacy fragment on Nav3 stack)").performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(10_000) {
                runCatching {
                    onView(withText(containsString("Legacy About fragment")))
                        .check(matches(isDisplayed()))
                }.isSuccess
            }
        }
    }
}

private fun ComposeTestRule.waitFor(text: String) {
    waitUntil(10_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
}
