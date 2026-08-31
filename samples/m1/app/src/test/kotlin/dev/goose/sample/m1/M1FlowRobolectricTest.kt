package dev.goose.sample.m1

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M1FlowRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Happy path: navigate, mutate VM state, pop with a typed result, observe it in the caller. */
    @Test
    fun resultRoundTrip() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextCount("ada") > 0
        }
        composeRule.onNodeWithText("ada").performClick()
        composeRule.onNodeWithText("Follow").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTextCount("Last visited: ada (followed!)") > 0
        }
        composeRule.onNodeWithText("Last visited: ada (followed!)").assertIsDisplayed()
    }

    /**
     * Config-change retention + saved-state wiring: recreate() runs the full
     * onSaveInstanceState/restore cycle. The back stack must survive (contributed polymorphic
     * serializers — an unregistered screen would crash the save), the Mavericks VM must be
     * retained (entry-scoped ViewModelStore), and the awaiting goToForResult must still deliver
     * its result to the (retained) caller VM after the activity was rebuilt.
     */
    @Test
    fun stateAndResultSurviveRecreation() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextCount("ada") > 0
        }
        composeRule.onNodeWithText("ada").performClick()
        composeRule.onNodeWithText("Follow").performClick()
        composeRule.onNodeWithText("Add a goose to notes").performClick()
        composeRule.onNodeWithText("Add a goose to notes").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // VM state retained across recreation.
        composeRule.onNodeWithText("Following ✓").assertIsDisplayed()
        composeRule.onNodeWithText("Notes (persisted): 🪿🪿").assertIsDisplayed()

        // The suspended goToForResult still answers the retained caller VM.
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTextCount("Last visited: ada (followed!)") > 0
        }
        composeRule.onNodeWithText("Last visited: ada (followed!)").assertIsDisplayed()
    }

    /**
     * ViewModels are ARGUMENT-scoped, not type-scoped: popping ada's profile clears her VM, and
     * grace's profile gets a fresh one — ada's notes cannot leak into it.
     */
    @Test
    fun viewModelsAreArgumentScoped() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextCount("ada") > 0
        }
        composeRule.onNodeWithText("ada").performClick()
        composeRule.onNodeWithText("Add a goose to notes").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTextCount("Notes (persisted): 🪿") > 0
        }
        composeRule.onNodeWithText("Done").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextCount("grace") > 0
        }
        composeRule.onNodeWithText("grace").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTextCount("Profile: grace") > 0
        }
        composeRule.onNodeWithText("Notes (persisted): —").assertIsDisplayed()
    }

    /**
     * The presenter-agnostic StateHolder honors the same lifecycle contract as screenViewModel:
     * retained across recreation, async work on holderScope lands, cleared on pop (reopening
     * starts fresh).
     */
    @Test
    fun stateHolderFollowsTheEntryLifecycle() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextCount("Team stats") > 0
        }
        composeRule.onNodeWithText("Team stats").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCount("Spot a goose") > 0 }
        composeRule.onNodeWithText("Spot a goose").performClick()
        composeRule.onNodeWithText("Spot a goose").performClick()
        composeRule.onNodeWithText("Listen for a honk").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCount("Honks heard: 1") > 0 }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Geese spotted: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Honks heard: 1").assertIsDisplayed()

        // Pop clears the holder; a fresh visit starts over.
        composeRule.onNodeWithText("Back to team").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCount("Team stats") > 0 }
        composeRule.onNodeWithText("Team stats").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCount("Geese spotted: 0") > 0 }
    }

    /** Back press = dismissed without answering: caller resumes with null, not a hang. */
    @Test
    fun backDeliversNullResult() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTextCount("ada") > 0
        }
        composeRule.onNodeWithText("ada").performClick()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTextCount("Last visited: ada (no answer)") > 0
        }
        composeRule.onNodeWithText("Last visited: ada (no answer)").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(
    text: String,
): Int = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
