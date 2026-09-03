package dev.goose.gaggle

import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shop features layered onto the core flows: the observable add-to-cart state, the review
 * summary + write-a-review result flow, the support chat's agent, the status panel embedded as
 * a NESTED fragment (not navigation), and the fragment-interop annotations (the FAQ binder and
 * the hours dialog override). Button-only, cold launch each test.
 */
@RunWith(AndroidJUnit4::class)
class GaggleShopTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun signIn() {
        composeRule.waitFor("Sign in as Goose Fan")
        composeRule.onNodeWithText("Sign in as Goose Fan").performClick()
        composeRule.waitFor("Shop")
    }

    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun openPellets() {
        composeRule.waitFor("Premium pond pellets")
        composeRule.onNode(hasText("Premium pond pellets", substring = true)).performClick()
        composeRule.waitFor("Add to cart")
    }

    /** The button reads the cart: it flips to "In cart · n" and keeps counting. */
    @Test
    fun addToCartReflectsCartState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            openPellets()
            composeRule.onNodeWithText("Add to cart").performClick()
            composeRule.waitFor("In cart · 1 · add another")
            composeRule.onNode(hasText("In cart", substring = true)).performClick()
            composeRule.waitFor("In cart · 2 · add another")

            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("×2 · $8")
            composeRule.waitFor("Total: $8")
            composeRule.onNodeWithText("Checkout (1)").assertIsDisplayed()
        }
    }

    /** The cart badge state survives leaving and re-entering the product page. */
    @Test
    fun addToCartStateSurvivesReentry() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            openPellets()
            composeRule.onNodeWithText("Add to cart").performClick()
            composeRule.waitFor("In cart · 1 · add another")
            back(it)
            composeRule.waitFor("Premium pond pellets")
            openPelletsAlreadyCarted()
        }
    }

    private fun openPelletsAlreadyCarted() {
        composeRule.onNode(hasText("Premium pond pellets", substring = true)).performClick()
        composeRule.waitFor("In cart · 1 · add another")
    }

    /** Seeded aggregates render, and a posted review (typed result) updates them live. */
    @Test
    fun writeReviewRoundTrip() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            openPellets()
            composeRule.waitFor("4.0 · 4 review(s)")
            composeRule.onNodeWithText("Write a review").performScrollTo().performClick()
            composeRule.waitFor("Review Premium pond pellets")
            // Post is gated until a rating and a phrase are chosen.
            composeRule.onAllNodesWithText("☆")[4].performClick()
            composeRule.onNodeWithText("My gosling approves").performScrollTo().performClick()
            composeRule.onNodeWithText("Post review").performScrollTo().performClick()
            composeRule.waitFor("4.2 · 5 review(s)")
            composeRule.waitFor("You")
        }
    }

    /** The agent replies deterministically, and the transcript counts honks. */
    @Test
    fun supportChatAgentReplies() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            composeRule.waitFor("Support chat")
            composeRule.onNodeWithText("Support chat").performClick()
            composeRule.waitFor("Agent Goose on ticket T-42")
            composeRule.onNodeWithText("Send a honk").performClick()
            composeRule.waitFor("HONK received, loud and clear. A specialist goose is on it.")
            composeRule.waitFor("Honks sent: 1")
            composeRule.onNodeWithText("Ask about order").performClick()
            composeRule.waitFor("Your order is paddling through the pond. Expected: two sunrises.")
            composeRule.waitFor("Honks sent: 2")
        }
    }

    /**
     * The status panel is EMBEDDED as a nested fragment (no back stack entry) and still
     * resolves the SupportScope session — the GooseScopeOwner walk across the FM boundary.
     */
    @Test
    fun embeddedStatusPanelResolvesScopedSession() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            composeRule.waitFor("Support chat")
            composeRule.onNodeWithText("Support chat").performClick()
            composeRule.waitFor("Ticket T-42 · Status: Open · Avg reply: 1 min")
            // The chat is up simultaneously: the panel rides alongside, not on, the stack.
            composeRule.waitFor("Agent Goose on ticket T-42")
        }
    }

    /**
     * The @GooseFragmentBinder route: the migrated chat screen navigates BY TYPED SCREEN to a
     * legacy fragment on the child FragmentManager stack, and a legacy popBackStack() resumes
     * the chat with its ViewModel intact (the detour neither cleared nor rebuilt it).
     */
    @Test
    fun faqBinderPushesLegacyFragmentAndLegacyPopResumesChat() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSupportChat()
            composeRule.onNodeWithText("Send a honk").performClick()
            composeRule.waitFor("Honks sent: 1")

            composeRule.onNodeWithText("Pond FAQ").performScrollTo().performClick()
            waitForLegacyView(it, "FAQ: honk etiquette")
            onView(withText("Back to chat")).perform(click())
            composeRule.waitFor("Honks sent: 1")
        }
    }

    /**
     * The @GooseFragmentNavigation route: SupportHoursScreen appears as a legacy DialogFragment
     * (the adapter's transaction), not a stack push — the chat stays where it was underneath.
     */
    @Test
    fun hoursNavigationOverrideShowsLegacyDialog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSupportChat()
            composeRule.onNodeWithText("Pond hours").performScrollTo().performClick()
            waitForDialogText("Agents paddle in from sunrise to sunset.")
            onView(withText("Honk, got it")).inRoot(isDialog()).perform(click())
            composeRule.waitFor("Agent Goose on ticket T-42")
        }
    }

    private fun openSupportChat() {
        signIn()
        composeRule.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        composeRule.waitFor("Support chat")
        composeRule.onNodeWithText("Support chat").performClick()
        composeRule.waitFor("Agent Goose on ticket T-42")
    }

    /**
     * Robolectric quirk, not a goose one: a fragment view added to the support flow's CHILD
     * FragmentManager after initial layout never gets a measure pass (compose drives its own
     * frames; nothing schedules a view traversal for the nested container). Force one so
     * Espresso's isDisplayed sees real bounds. On a device the normal traversal does this.
     */
    private fun waitForLegacyView(scenario: ActivityScenario<MainActivity>, text: String) {
        composeRule.waitUntil(10_000) {
            scenario.onActivity { a ->
                val container = a.findViewById<View>(R.id.gaggle_support_container)
                if (container != null && container.width > 0) {
                    container.measure(
                        View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.EXACTLY),
                    )
                    container.layout(container.left, container.top, container.right, container.bottom)
                }
            }
            runCatching {
                onView(withText(containsString(text))).check(matches(isDisplayed()))
            }.isSuccess
        }
    }

    private fun waitForDialogText(text: String) {
        composeRule.waitUntil(10_000) {
            runCatching {
                onView(withText(containsString(text))).inRoot(isDialog()).check(matches(isDisplayed()))
            }.isSuccess
        }
    }

    /** The checkout's confirm step reports the REAL unit count through the typed result. */
    @Test
    fun checkoutCarriesRealItemCount() {
        ActivityScenario.launch(MainActivity::class.java).use {
            signIn()
            openPellets()
            composeRule.onNodeWithText("Add to cart").performClick()
            composeRule.waitFor("In cart · 1 · add another")
            composeRule.onNode(hasText("In cart", substring = true)).performClick()
            composeRule.waitFor("In cart · 2 · add another")
            composeRule.onNodeWithText("Cart", useUnmergedTree = true).performClick()
            composeRule.waitFor("Checkout (1)")
            composeRule.onNodeWithText("Checkout (1)").performClick()
            composeRule.waitFor("Step 1: Shipping")
            composeRule.onNodeWithText("Choose address").performClick()
            composeRule.waitFor("1 Goose Way, Pondside")
            composeRule.onNodeWithText("1 Goose Way, Pondside").performClick()
            composeRule.waitFor("Ship to: 1 Goose Way, Pondside")
            composeRule.onNodeWithText("Next: gift note").performClick()
            composeRule.waitFor("Step 2: Gift note")
            composeRule.onNodeWithText("Next: confirm").performClick()
            composeRule.waitFor("Items: 2 · Order total: $8")
            composeRule.onNodeWithText("Place order").performClick()
            composeRule.waitFor("Order placed: 2 item(s) to 1 Goose Way, Pondside")
        }
    }
}
