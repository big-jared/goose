package dev.goose.nav3

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.StackKey
import dev.goose.runtime.switchTo
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
private data object HomeRootScreen : Screen

@Serializable
private data object ProfileRootScreen : Screen

@Serializable
private data class DetailScreen(val id: String) : Screen

/**
 * The cross-stack navigation contract: goTo is ALWAYS a local push (screens carry no stack
 * affinity), and changing stacks is the explicit switchTo — reachable from anywhere in the
 * navigator tree via the parent-walking extension.
 */
@RunWith(AndroidJUnit4::class)
class StackRoutingTest {

    private val home = StackKey("home")
    private val profile = StackKey("profile")

    private val homeStack = mutableListOf<NavKey>(HomeRootScreen.pushed())
    private val profileStack = mutableListOf<NavKey>(ProfileRootScreen.pushed())

    private val host = GooseTabNavigator(
        stacksByKey = mapOf(home to homeStack, profile to profileStack),
        tabOrder = listOf(home, profile),
        currentStackState = mutableStateOf(home),
        resultRouter = ResultRouter(),
        parent = null,
        hostTag = "test-host",
    )

    private fun screens(stack: List<NavKey>) = stack.map { it.asScreen() }

    @Test
    fun switchToSwitchesAndChainedGoToPushesOntoTheNewStack() {
        host.switchTo(profile).goTo(DetailScreen("d1"))

        assertEquals(profile, host.currentStack)
        assertEquals(listOf(ProfileRootScreen, DetailScreen("d1")), screens(profileStack))
        assertEquals(listOf<Screen>(HomeRootScreen), screens(homeStack))
    }

    @Test
    fun goToPushesLocally_evenWhenTheScreenIsAnotherStacksRoot() {
        // No affinity: the same screen is pushable in any stack. Profile-as-a-page inside the
        // home stack is a feature, not a routing mistake.
        host.goTo(ProfileRootScreen)

        assertEquals(home, host.currentStack)
        assertEquals(listOf(HomeRootScreen, ProfileRootScreen), screens(homeStack))
        assertEquals(listOf<Screen>(ProfileRootScreen), screens(profileStack))
    }

    @Test
    fun switchToFromNestedStackWalksUpToTheHost() {
        // A flow hosted inside a home-stack entry: its own navigator, parented to the tab host.
        val flowStack = mutableListOf<NavKey>(DetailScreen("step1").pushed())
        val nested = Nav3Navigator(flowStack, ResultRouter(), parent = host, stackTag = "flow")

        nested.switchTo(profile).goTo(DetailScreen("order"))

        assertEquals(profile, host.currentStack)
        assertEquals(listOf(ProfileRootScreen, DetailScreen("order")), screens(profileStack))
        // The nested flow's own stack is untouched — the push landed on the host's new stack.
        assertEquals(listOf<Screen>(DetailScreen("step1")), screens(flowStack))
    }

    @Test
    fun switchToCurrentStackIsANoOp() {
        val navigator = host.switchTo(home)

        assertSame(host, navigator)
        assertEquals(home, host.currentStack)
        assertEquals(listOf<Screen>(HomeRootScreen), screens(homeStack))
    }

    @Test
    fun switchToUnhostedKeyThrows() {
        val lone = Nav3Navigator(
            mutableListOf<NavKey>(HomeRootScreen.pushed()),
            ResultRouter(),
            parent = null,
            stackTag = "lone",
        )

        assertThrows(IllegalStateException::class.java) {
            lone.switchTo(StackKey("nowhere"))
        }
    }

    @Test
    fun selectTabReselectStillPopsToRoot() {
        host.goTo(DetailScreen("d1"))
        host.goTo(DetailScreen("d2"))

        host.selectTab(home)

        assertEquals(listOf<Screen>(HomeRootScreen), screens(homeStack))
    }

    @Test
    fun switchToReselectDoesNotPopToRoot() {
        host.goTo(DetailScreen("d1"))

        host.switchTo(home)

        assertEquals(listOf(HomeRootScreen, DetailScreen("d1")), screens(homeStack))
    }
}
