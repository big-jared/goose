package dev.goose.gaggle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The abuse cases: rapid double-taps, back-spam, deep stacks, equal-value pushes, session
 * teardown, and the legacy corner under recreation. These are the tests that find the bugs
 * polite navigation never hits.
 */
@RunWith(AndroidJUnit4::class)
class GaggleHardeningTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun signIn() {
        composeRule.waitFor("Sign in as Goose Fan")
        composeRule.onNodeWithText("Sign in as Goose Fan").performClick()
        composeRule.waitFor("Shop")
    }

    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /**
     * Rapid double-taps are SAFE: depending on frame timing they land one or two pushes (two
     * equal entries are fine under per-push identity; equalStatsScreensAreIndependent pins the
     * two-entry semantics deterministically). Either way nothing crashes and back unwinds to
     * the catalog.
     */
    @Test
    fun rapidDoubleTapIsSafe() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn()
            composeRule.waitFor("Premium pond pellets")
            val node = composeRule.onAllNodes(hasText("Premium pond pellets", substring = true)).onFirst()
            node.performClick()
            runCatching { node.performClick() }
            composeRule.waitFor("Add to cart")
            repeat(3) { back(scenario) }
            composeRule.waitFor("Deal failed to load.")
        }
    }

    /** Back-spam far past the root neither crashes nor escapes the shell. */
    @Test
    fun backSpamStopsStableAtTheRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onAllNodes(hasText("Premium pond pellets", substring = true)).onFirst().performClick()
            composeRule.waitFor("Related")
            composeRule.onAllNodes(hasText("🛖 Floating nest platform", substring = true)).onFirst().performScrollTo().performClick()
            composeRule.waitFor("Related")
            repeat(6) { back(scenario) }
            composeRule.waitFor("Deal failed to load.")
            composeRule.onNode(hasText("Shop") and isSelected()).assertIsDisplayed()
        }
    }

    /** A deep stack of same-type screens survives recreation and unwinds cleanly. */
    @Test
    fun deepStackSurvivesRecreationAndUnwinds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onAllNodes(hasText("Premium pond pellets", substring = true)).onFirst().performClick()
            // Alternate pellets <-> nest via the RELATED buttons (emoji prefix distinguishes
            // the button from the current page's plain title): same screen type, 11 deep.
            repeat(10) { i ->
                composeRule.waitFor("Related")
                val next = if (i % 2 == 0) "🛖 Floating nest platform" else "🌾 Premium pond pellets"
                composeRule.onAllNodes(hasText(next, substring = true)).onFirst().performScrollTo().performClick()
                composeRule.waitForIdle()
            }
            scenario.recreate()
            composeRule.waitFor("Add to cart")
            repeat(11) { back(scenario) }
            composeRule.waitFor("Deal failed to load.")
        }
    }

    /** Equal-value pushes of the stats screen: independent StateHolders per push. */
    @Test
    fun equalStatsScreensAreIndependent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn()
            composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            composeRule.waitFor("Team stats")
            composeRule.onNodeWithText("Team stats").performClick()
            composeRule.waitFor("Spot a goose")
            composeRule.onNodeWithText("Spot a goose").performClick()
            composeRule.onNodeWithText("Spot a goose").performClick()
            composeRule.waitFor("Geese spotted: 2")
            composeRule.onNodeWithText("Open stats again").performClick()
            composeRule.waitFor("Geese spotted: 0")
            composeRule.onNodeWithText("Spot a goose").performClick()
            composeRule.waitFor("Geese spotted: 1")
            scenario.recreate()
            composeRule.waitForIdle()
            composeRule.waitFor("Geese spotted: 1")
            back(scenario)
            composeRule.waitFor("Geese spotted: 2")
        }
    }

    /** Logout disposes the session: a new login gets a FRESH cart, not the old one. */
    @Test
    fun relogingInGetsAFreshSession() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onAllNodes(hasText("Premium pond pellets", substring = true)).onFirst().performClick()
            composeRule.waitFor("Add to cart")
            composeRule.onNodeWithText("Add to cart").performClick()
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Checkout (1)")

            composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            composeRule.waitFor("Log out")
            composeRule.onNodeWithText("Log out").performClick()
            composeRule.waitFor("Leaving the pond?")
            composeRule.onNodeWithText("Sign out").performClick()
            signIn()
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Nothing here yet. Honk at the shop.")
        }
    }

    /** Typed legacy fragment args (including the Parcelable) survive recreation. */
    @Test
    fun legacyTermsTypedArgsSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn()
            composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            composeRule.waitFor("Terms (legacy)")
            composeRule.onNodeWithText("Terms (legacy)").performClick()
            waitForView("Terms TOS-7 rev 3 by Legal Goose")
            scenario.recreate()
            composeRule.waitForIdle()
            waitForView("Terms TOS-7 rev 3 by Legal Goose")
        }
    }

    /**
     * The support scope crosses the FragmentManager boundary, and the fragment-hosted screen's
     * ViewModel honors the same contract as on Nav3: retained across rotation, cleared on pop.
     */
    @Test
    fun supportScopeAndVmContractAcrossFragmentBoundary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn()
            composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            composeRule.waitFor("Support chat")
            composeRule.onNodeWithText("Support chat").performClick()
            composeRule.waitFor("Ticket T-42")
            composeRule.onNodeWithText("Send a honk").performClick()
            composeRule.onNodeWithText("Send a honk").performClick()
            composeRule.waitFor("Honks sent: 2")

            scenario.recreate()
            composeRule.waitForIdle()
            composeRule.waitFor("Honks sent: 2")

            back(scenario)
            composeRule.waitFor("Log out")
            composeRule.onNodeWithText("Support chat").performClick()
            composeRule.waitFor("Honks sent: 0")
        }
    }

    private fun waitForView(text: String) {
        composeRule.waitUntil(10_000) {
            runCatching {
                onView(withText(containsString(text))).check(matches(isDisplayed()))
            }.isSuccess
        }
    }
}
