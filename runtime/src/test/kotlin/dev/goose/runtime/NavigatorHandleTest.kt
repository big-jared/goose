package dev.goose.runtime

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

private data class HandleScreen(val id: String) : Screen

private data class HandleAnswer(val text: String) : PopResult

private data class HandleAskScreen(val id: String) : ScreenWithResult<HandleAnswer>

/** Records every Navigator call as a string, plus whether it arrived on the main thread. */
private class RecordingNavigator(
    private val popReturns: Boolean = true,
    private val answer: HandleAnswer? = null,
) : Navigator {
    val calls = mutableListOf<String>()
    val calledOnMain = mutableListOf<Boolean>()

    override val parent: Navigator? = null
    override val backStack: List<Screen> = listOf(HandleScreen("root"))

    private fun record(call: String) {
        calls += call
        calledOnMain += Looper.myLooper() == Looper.getMainLooper()
    }

    override fun goTo(screen: Screen) = record("goTo:$screen")

    override fun pop(result: PopResult?): Boolean {
        record("pop:$result")
        return popReturns
    }

    override fun resetRoot(screen: Screen) = record("resetRoot:$screen")

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? {
        record("goToForResult:$screen")
        @Suppress("UNCHECKED_CAST")
        return answer as R?
    }
}

/**
 * The retained-presenter lifeline contract of [NavigatorHandle]: a presenter can hold the handle
 * forever and call it from any thread at any time. Calls made while no delegate is bound (the
 * recreation gap) queue and replay in order on bind; calls while bound on the main thread
 * dispatch synchronously; calls from other threads post to the main looper; and unbinding is
 * identity-guarded so a stale host tearing down cannot detach a newer bind. Under Robolectric the
 * test thread IS the main looper thread, and posted work runs only when the looper is idled.
 */
@RunWith(AndroidJUnit4::class)
class NavigatorHandleTest {

    private val handle = NavigatorHandle()

    @After
    fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun callsWhileUnboundQueueAndReplayInOrderOnBind() {
        val navigator = RecordingNavigator()

        handle.goTo(HandleScreen("a"))
        handle.pop()
        handle.resetRoot(HandleScreen("b"))
        handle.goTo(HandleScreen("c"))
        assertTrue(navigator.calls.isEmpty())

        handle.bind(navigator)

        assertEquals(
            listOf(
                "goTo:HandleScreen(id=a)",
                "pop:null",
                "resetRoot:HandleScreen(id=b)",
                "goTo:HandleScreen(id=c)",
            ),
            navigator.calls,
        )
        assertTrue(navigator.calledOnMain.all { it })
    }

    @Test
    fun boundCallOnMainThreadDispatchesImmediately() {
        val navigator = RecordingNavigator()
        handle.bind(navigator)

        handle.goTo(HandleScreen("now"))

        // No looper idling: the call must have run synchronously, not via a post.
        assertEquals(listOf("goTo:HandleScreen(id=now)"), navigator.calls)
    }

    @Test
    fun unbindingTheCurrentDelegateMakesLaterCallsQueueUntilRebind() {
        val navigator = RecordingNavigator()
        handle.bind(navigator)
        handle.unbind(navigator)

        handle.goTo(HandleScreen("gap"))
        assertTrue(navigator.calls.isEmpty())
        assertTrue(handle.backStack.isEmpty())

        handle.bind(navigator)
        assertEquals(listOf("goTo:HandleScreen(id=gap)"), navigator.calls)
    }

    @Test
    fun unbindingAStaleDelegateDoesNotDetachANewerBind() {
        val stale = RecordingNavigator()
        val current = RecordingNavigator()
        handle.bind(stale)
        handle.bind(current)

        // The old host's onDispose runs after the new host bound: it must be a no-op.
        handle.unbind(stale)

        handle.goTo(HandleScreen("x"))
        assertEquals(listOf("goTo:HandleScreen(id=x)"), current.calls)
        assertTrue(stale.calls.isEmpty())
        assertEquals(current.backStack, handle.backStack)
    }

    @Test
    fun popOnMainThreadWhileBoundReturnsTheDelegatesRealResult() {
        val popped = NavigatorHandle().also { it.bind(RecordingNavigator(popReturns = true)) }
        val atRoot = NavigatorHandle().also { it.bind(RecordingNavigator(popReturns = false)) }

        assertTrue(popped.pop())
        assertFalse(atRoot.pop())
    }

    @Test
    fun popWhileUnboundReturnsTrueAndReplaysOnBind() {
        val navigator = RecordingNavigator(popReturns = false)

        // Unbound there is no real answer; true means "accepted for dispatch".
        assertTrue(handle.pop())

        handle.bind(navigator)
        assertEquals(listOf("pop:null"), navigator.calls)
    }

    @Test
    fun boundCallFromABackgroundThreadIsPostedToTheMainLooper() {
        val navigator = RecordingNavigator()
        handle.bind(navigator)

        val background = Thread { handle.goTo(HandleScreen("bg")) }
        background.start()
        background.join()

        // Posted, not run inline on the background thread.
        assertTrue(navigator.calls.isEmpty())

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("goTo:HandleScreen(id=bg)"), navigator.calls)
        assertEquals(listOf(true), navigator.calledOnMain)
    }

    @Test
    fun popFromABackgroundThreadWhileBoundReportsAcceptedAndRunsOnMain() {
        val navigator = RecordingNavigator(popReturns = false)
        handle.bind(navigator)

        var reported = false
        val background = Thread { reported = handle.pop() }
        background.start()
        background.join()

        // Off-main the real result is unknowable; the handle reports accepted-for-dispatch.
        assertTrue(reported)

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("pop:null"), navigator.calls)
        assertEquals(listOf(true), navigator.calledOnMain)
    }

    @Test
    fun goToForResultSuspendsUntilADelegateBindsThenDelegates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val navigator = RecordingNavigator(answer = HandleAnswer("yes"))

            val caller = async { handle.goToForResult(HandleAskScreen("q")) }
            runCurrent()
            assertFalse(caller.isCompleted)
            assertTrue(navigator.calls.isEmpty())

            handle.bind(navigator)
            runCurrent()

            assertEquals(HandleAnswer("yes"), caller.await())
            assertEquals(listOf("goToForResult:HandleAskScreen(id=q)"), navigator.calls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun goToForResultWhileBoundDelegatesDirectly() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val navigator = RecordingNavigator(answer = null)
            handle.bind(navigator)

            val caller = async { handle.goToForResult(HandleAskScreen("q")) }
            runCurrent()

            // Dismissed-without-answering surfaces as null, straight from the delegate.
            assertEquals(null, caller.await())
            assertEquals(listOf("goToForResult:HandleAskScreen(id=q)"), navigator.calls)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
