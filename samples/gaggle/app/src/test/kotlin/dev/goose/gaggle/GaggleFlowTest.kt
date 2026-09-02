package dev.goose.gaggle

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The core Gaggle workflows, end to end on Robolectric. Every test is button-only (no text
 * input) and starts from a cold launch; see samples/README.md for the claim -> test map.
 */
@RunWith(AndroidJUnit4::class)
class GaggleFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun launch(intent: Intent? = null): ActivityScenario<MainActivity> =
        if (intent == null) {
            ActivityScenario.launch(MainActivity::class.java)
        } else {
            ActivityScenario.launch<MainActivity>(
                intent.setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            )
        }

    private fun signIn() {
        composeRule.waitFor("Sign in as Goose Fan")
        composeRule.onNodeWithText("Sign in as Goose Fan").performClick()
        composeRule.waitFor("Shop")
    }

    /**
     * Login gates the app; logout goes through the forced-choice confirm dialog, then tears
     * the logged-in graph down and returns to login.
     */
    @Test
    fun loginAndLogout() {
        launch().use {
            signIn()
            composeRule.onNode(hasText("Shop") and isSelected()).assertIsDisplayed()
            composeRule.onNodeWithText("Profile").performClick()
            composeRule.waitFor("Hi, Goose Fan")
            composeRule.onNodeWithText("Log out").performClick()
            composeRule.waitFor("Leaving the pond?")
            composeRule.onNodeWithText("Sign out").performClick()
            composeRule.waitFor("Sign in as Goose Fan")
        }
    }

    /** The confirm dialog answering "stay" (a pop) leaves the session untouched. */
    @Test
    fun signOutConfirmStayKeepsSession() {
        launch().use {
            signIn()
            composeRule.onNodeWithText("Profile").performClick()
            composeRule.waitFor("Hi, Goose Fan")
            composeRule.onNodeWithText("Log out").performClick()
            composeRule.waitFor("Leaving the pond?")
            composeRule.onNodeWithText("Stay").performClick()
            composeRule.waitFor("Hi, Goose Fan")
        }
    }

    /** Mavericks Async.Fail for real: the deal's first load fails, retry succeeds. */
    @Test
    fun asyncFailThenRetry() {
        launch().use {
            signIn()
            composeRule.waitFor("Deal failed to load. ")
            composeRule.onNodeWithText("Retry").performClick()
            composeRule.waitFor("Golden egg incubator")
        }
    }

    /** The deal banner is a real product: tapping it opens the detail screen. */
    @Test
    fun dealBannerOpensProduct() {
        launch().use {
            signIn()
            composeRule.waitFor("Deal failed to load. ")
            composeRule.onNodeWithText("Retry").performClick()
            composeRule.waitFor("Golden egg incubator")
            composeRule.onNode(hasText("Golden egg incubator", substring = true)).performClick()
            composeRule.waitFor("Add to cart")
        }
    }

    /** The peek OverlayScreen: pops itself and pushes the full page in one frame. */
    @Test
    fun peekDialogPromotesToFullPage() {
        launch().use {
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onAllNodesWithText("Peek").onFirst().performClick()
            composeRule.waitFor("A quick look. The full page has related products and add-to-cart.")
            composeRule.onNodeWithText("Open full page").performClick()
            composeRule.waitFor("Add to cart")
            composeRule.onNodeWithText("Related").performScrollTo().assertIsDisplayed()
        }
    }

    /** Cross-feature session dependency: the catalog adds to the cart living in LoggedInScope. */
    @Test
    fun addToCartAcrossFeatures() {
        launch().use {
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onNode(hasText("Premium pond pellets", substring = true)).performClick()
            composeRule.waitFor("Add to cart")
            composeRule.onNodeWithText("Add to cart").performClick()
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Checkout (1)")
            composeRule.onNodeWithText("Premium pond pellets").assertIsDisplayed()
        }
    }

    /** The dialog answers goToForResult; declining keeps the item, confirming removes it. */
    @Test
    fun removeDialogResult() {
        launch().use {
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onNode(hasText("Premium pond pellets", substring = true)).performClick()
            composeRule.waitFor("Add to cart")
            composeRule.onNodeWithText("Add to cart").performClick()
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Remove")
            composeRule.onNodeWithText("Remove").performClick()
            composeRule.waitFor("Keep it")
            composeRule.onNodeWithText("Keep it").performClick()
            composeRule.waitFor("Checkout (1)")
            composeRule.onNodeWithText("Remove").performClick()
            composeRule.waitFor("Remove it")
            composeRule.onNodeWithText("Remove it").performClick()
            composeRule.waitFor("Nothing here yet. Honk at the shop.")
        }
    }

    /**
     * The full checkout: typed picker result, checkout-scoped gift note, typed wizard result to
     * the cart, then an atomic cross-tab jump into a typed LEGACY fragment.
     */
    @Test
    fun checkoutEndToEndIntoLegacyOrderHistory() {
        launch().use {
            signIn()
            addPelletsToCart()
            composeRule.onNodeWithText("Checkout (1)").performClick()
            composeRule.waitFor("Step 1: Shipping")
            composeRule.onNodeWithText("Choose address").performClick()
            composeRule.waitFor("1 Goose Way, Pondside")
            composeRule.onNodeWithText("1 Goose Way, Pondside").performClick()
            composeRule.waitFor("Ship to: 1 Goose Way, Pondside")
            composeRule.onNodeWithText("Next: gift note").performClick()
            composeRule.waitFor("Step 2: Gift note")
            composeRule.onNodeWithText("Write gift note").performClick()
            composeRule.waitFor("Note: Happy hatching!")
            composeRule.onNodeWithText("Next: confirm").performClick()
            composeRule.waitFor("Gift note: Happy hatching!")
            composeRule.onNodeWithText("Place order").performClick()
            composeRule.waitFor("Order placed: 1 item(s) to 1 Goose Way, Pondside")
            composeRule.onNodeWithText("View order history").performClick()
            composeRule.waitUntil(10_000) {
                runCatching {
                    onView(withText(containsString("Order history: 1 order(s)")))
                        .check(matches(isDisplayed()))
                }.isSuccess
            }
        }
    }

    /** Recreation at EVERY wizard step: flow VM, checkout graph, and stack all survive. */
    @Test
    fun wizardSurvivesRecreationAtEveryStep() {
        launch().use { scenario ->
            signIn()
            addPelletsToCart()
            composeRule.onNodeWithText("Checkout (1)").performClick()
            composeRule.waitFor("Step 1: Shipping")
            scenario.recreate()
            composeRule.waitFor("Step 1: Shipping")

            composeRule.onNodeWithText("Choose address").performClick()
            composeRule.waitFor("1 Goose Way, Pondside")
            scenario.recreate()
            composeRule.waitFor("1 Goose Way, Pondside")
            composeRule.onNodeWithText("1 Goose Way, Pondside").performClick()
            composeRule.waitFor("Ship to: 1 Goose Way, Pondside")

            composeRule.onNodeWithText("Next: gift note").performClick()
            composeRule.waitFor("Step 2: Gift note")
            composeRule.onNodeWithText("Write gift note").performClick()
            composeRule.waitFor("Note: Happy hatching!")
            scenario.recreate()
            composeRule.waitFor("Note: Happy hatching!")

            composeRule.onNodeWithText("Next: confirm").performClick()
            composeRule.waitFor("Step 3: Confirm")
            scenario.recreate()
            composeRule.waitFor("Ship to: 1 Goose Way, Pondside")
        }
    }

    /** A cold-start deep link parks until login, then lands on the product atomically. */
    @Test
    fun coldDeepLinkParksUntilLogin() {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("gaggle://product/pond-2"))).use {
            signIn()
            composeRule.waitFor("Floating nest platform")
            composeRule.onNodeWithText("$29").assertIsDisplayed()
        }
    }

    /** A warm deep link from ANOTHER tab jumps tabs and pushes in one atomic navigation. */
    @Test
    fun warmDeepLinkJumpsTabs() {
        launch().use { scenario ->
            signIn()
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Nothing here yet. Honk at the shop.")
            scenario.onActivity { activity ->
                val graph = (activity.application as dev.goose.metro.GooseGraphHolder).gooseGraph
                activity.handleDeepLink(
                    Intent(Intent.ACTION_VIEW, Uri.parse("gaggle://product/pond-4")),
                    (graph as dev.goose.gaggle.auth.api.SessionManagerAccessor).sessionManager,
                )
            }
            composeRule.waitFor("Honk amplifier")
            composeRule.onNode(hasText("Shop") and isSelected()).assertIsDisplayed()
        }
    }

    /** Tab stacks are independent and survive switching away and recreation. */
    @Test
    fun tabStacksSurviveSwitchAndRecreation() {
        launch().use { scenario ->
            signIn()
            composeRule.waitFor("Premium pond pellets")
            composeRule.onNode(hasText("Premium pond pellets", substring = true)).performClick()
            composeRule.waitFor("Add to cart")
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Nothing here yet. Honk at the shop.")
            composeRule.onNodeWithText("Shop", useUnmergedTree = true).performClick()
            composeRule.waitFor("Add to cart")
            scenario.recreate()
            composeRule.waitFor("Add to cart")
        }
    }

    private fun addPelletsToCart() {
        composeRule.waitFor("Premium pond pellets")
        composeRule.onNode(hasText("Premium pond pellets", substring = true)).performClick()
        composeRule.waitFor("Add to cart")
        composeRule.onNodeWithText("Add to cart").performClick()
        composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
        composeRule.waitFor("Checkout (1)")
    }
}

internal fun ComposeTestRule.waitFor(text: String) {
    waitUntil(10_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeTestRule.onNode(matcher: androidx.compose.ui.test.SemanticsMatcher) =
    onAllNodes(matcher).onFirst()
