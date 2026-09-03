package dev.goose.nav3

import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.runtime.PopResult
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenWithResult
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private data object NavRootScreen : Screen

private data class NavStepScreen(val id: String) : Screen

private data class NavConfirmation(val text: String) : PopResult

private data class NavConfirmScreen(val id: String) : ScreenWithResult<NavConfirmation>

/**
 * The single-stack navigator's mechanics beyond routing (which StackRoutingTest pins):
 *
 * - Every push wraps the screen in a distinct record, so equal screens are distinct entries.
 * - pop at the root bubbles to the parent (or reports unhandled); pops deliver results.
 * - resetRoot replaces the whole stack with a FRESH record and resumes every awaiting caller
 *   with null instead of leaving goToForResult suspended forever.
 * - Result keys are scoped by stackTag: same-class awaits in two stacks never cross-deliver.
 */
@RunWith(AndroidJUnit4::class)
class Nav3NavigatorTest {

    private fun navigatorOn(
        stack: MutableList<NavKey>,
        router: ResultRouter = ResultRouter(),
        parent: Nav3Navigator? = null,
        tag: String = "test-stack",
    ) = Nav3Navigator(stack, router, parent, tag)

    @Test
    fun pushingEqualScreensTwiceCreatesDistinctEntries() {
        val stack = mutableListOf<NavKey>(NavRootScreen.pushed())
        val navigator = navigatorOn(stack)

        navigator.goTo(NavStepScreen("x"))
        navigator.goTo(NavStepScreen("x"))

        assertEquals(listOf(NavRootScreen, NavStepScreen("x"), NavStepScreen("x")), navigator.backStack)
        assertNotEquals(stack[1], stack[2])
    }

    @Test
    fun resetRootClearsTheStackAndSeedsTheNewRoot() {
        val stack = mutableListOf<NavKey>(NavRootScreen.pushed())
        val navigator = navigatorOn(stack)
        navigator.goTo(NavStepScreen("a"))
        navigator.goTo(NavStepScreen("b"))

        navigator.resetRoot(NavStepScreen("new-root"))

        assertEquals(listOf<Screen>(NavStepScreen("new-root")), navigator.backStack)
    }

    @Test
    fun resetRootToAnEqualScreenStillGetsAFreshPushRecord() {
        val stack = mutableListOf<NavKey>(NavRootScreen.pushed())
        val oldRootKey = stack.single()
        val navigator = navigatorOn(stack)

        navigator.resetRoot(NavRootScreen)

        assertEquals(NavRootScreen, stack.single().asScreen())
        // A fresh record: the "same" root after a reset is a NEW entry (state, ViewModels).
        assertNotEquals(oldRootKey, stack.single())
    }

    @Test
    fun resetRootResumesEveryAwaitingCallerWithNull() = runTest {
        val stack = mutableListOf<NavKey>(NavRootScreen.pushed())
        val navigator = navigatorOn(stack)

        val first = async { navigator.goToForResult(NavConfirmScreen("first")) }
        runCurrent()
        val second = async { navigator.goToForResult(NavConfirmScreen("second")) }
        runCurrent()

        navigator.resetRoot(NavRootScreen)
        runCurrent()

        assertNull(first.await())
        assertNull(second.await())
    }

    @Test
    fun popDeliversTheResultToTheAwaitingCaller() = runTest {
        val navigator = navigatorOn(mutableListOf(NavRootScreen.pushed()))

        val caller = async { navigator.goToForResult(NavConfirmScreen("q")) }
        runCurrent()

        assertTrue(navigator.pop(NavConfirmation("yes")))
        assertEquals(NavConfirmation("yes"), caller.await())
    }

    @Test
    fun popAtRootBubblesToTheParent() {
        val parentStack = mutableListOf<NavKey>(NavRootScreen.pushed())
        val parent = navigatorOn(parentStack, tag = "parent")
        parent.goTo(NavStepScreen("hosts-the-flow"))
        val flowStack = mutableListOf<NavKey>(NavStepScreen("flow-root").pushed())
        val flow = navigatorOn(flowStack, parent = parent, tag = "flow")

        assertTrue(flow.pop())

        // The parent popped its entry hosting the flow; the flow's own stack is untouched —
        // its root screen isn't the flow navigator's to remove.
        assertEquals(listOf<Screen>(NavRootScreen), parent.backStack)
        assertEquals(listOf<Screen>(NavStepScreen("flow-root")), flow.backStack)
    }

    @Test
    fun popAtRootWithoutParentReportsUnhandled() {
        val navigator = navigatorOn(mutableListOf(NavRootScreen.pushed()))

        assertFalse(navigator.pop())

        assertEquals(listOf<Screen>(NavRootScreen), navigator.backStack)
    }

    @Test
    fun sameClassAwaitsInTwoStacksNeverCrossDeliver() = runTest {
        // One router (as in production, the app graph's singleton), two stacks with their own tags.
        val router = ResultRouter()
        val stackA = navigatorOn(mutableListOf(NavRootScreen.pushed()), router, tag = "stack-a")
        val stackB = navigatorOn(mutableListOf(NavRootScreen.pushed()), router, tag = "stack-b")

        val callerA = async { stackA.goToForResult(NavConfirmScreen("q")) }
        runCurrent()
        val callerB = async { stackB.goToForResult(NavConfirmScreen("q")) }
        runCurrent()

        stackB.pop(NavConfirmation("for-b"))
        runCurrent()

        // B's answer resolved B's caller only; A is still waiting on its own stack.
        assertEquals(NavConfirmation("for-b"), callerB.await())
        assertFalse(callerA.isCompleted)

        stackA.pop(NavConfirmation("for-a"))
        assertEquals(NavConfirmation("for-a"), callerA.await())
    }
}
