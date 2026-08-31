package dev.goose.sample.m4

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The adoption question a Dagger app asks: can goose inject from our existing graph? The
 * greeting on screen comes from a repository provided by a plain Dagger component, consumed by
 * the Metro graph via @Includes, injected into a goose Mavericks ViewModel.
 */
@RunWith(AndroidJUnit4::class)
class M4DaggerInteropRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun daggerProvidedDependencyReachesGooseScreen() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Legacy repository says: Honk from Dagger"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Legacy repository says: Honk from Dagger").assertExists()
    }
}
