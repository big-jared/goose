package dev.goose.sample.m2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M2FlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Cross-stack result through the nested checkout wizard, launched from the cart tab. */
    @Test
    fun nestedCheckoutDeliversResultToCartViewModel() {
        composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
        composeRule.waitFor("Checkout")
        composeRule.onNodeWithText("Checkout").performClick()
        composeRule.waitFor("Use home address")
        composeRule.onNodeWithText("Use home address").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitFor("Ship to: 1 Goose Way, Pondside")
        composeRule.onNodeWithText("Confirm order").performClick()
        // The whole nested flow popped off the tab stack; the cart VM got the typed result.
        composeRule.waitFor("Last order shipped to: 1 Goose Way, Pondside")
    }

    /** Tab switch preserves the hidden tab's stack and ViewModels. */
    @Test
    fun tabStackSurvivesTabSwitch() {
        composeRule.waitFor("alpha")
        composeRule.onNodeWithText("alpha").performClick()
        composeRule.waitFor("Buy now")
        composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
        composeRule.waitFor("Checkout")
        composeRule.onNodeWithText("Catalog", useUnmergedTree = true).performClick()
        // Still on the item detail, not the catalog root.
        composeRule.waitFor("Buy now")
        composeRule.onNodeWithText("Buy now").assertIsDisplayed()
    }

    /** Cross-module: catalog's Buy now launches the cart feature's wizard and awaits its result. */
    @Test
    fun buyNowFromCatalogGetsCheckoutResult() {
        composeRule.waitFor("bravo")
        composeRule.onNodeWithText("bravo").performClick()
        composeRule.waitFor("Buy now")
        composeRule.onNodeWithText("Buy now").performClick()
        composeRule.waitFor("Use home address")
        composeRule.onNodeWithText("Use home address").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitFor("Confirm order")
        composeRule.onNodeWithText("Confirm order").performClick()
        composeRule.waitFor("Shipped to: 1 Goose Way, Pondside")
    }

    /** Back at a non-primary tab's root falls back to the primary tab. */
    @Test
    fun backAtCartRootReturnsToCatalogTab() {
        composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
        composeRule.waitFor("Checkout")
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitFor("alpha")
        composeRule.onNodeWithText("alpha").assertIsDisplayed()
    }

    /** OverlayScreen renders as a dialog over the cart; pop closes it. */
    @Test
    fun cartInfoDialogShowsAndCloses() {
        composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
        composeRule.waitFor("Cart info")
        composeRule.onNodeWithText("Cart info").performClick()
        composeRule.waitFor("About this cart")
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitFor("Checkout")
        composeRule.onNodeWithText("Checkout").assertIsDisplayed()
    }
}

private fun AndroidComposeTestRule<*, *>.waitFor(text: String) {
    waitUntil(10_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
}
