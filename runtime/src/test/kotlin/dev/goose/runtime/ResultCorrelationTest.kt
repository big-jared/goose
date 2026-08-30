package dev.goose.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Answer(val text: String) : PopResult

private data class AskScreen(val id: String) : ScreenWithResult<Answer>

/** A navigator whose awaited navigations hand the awaiter to the test instead of a host. */
private class FakeNavigator(
    router: ResultRouter,
    private val onAwaited: (Screen, ResultAwaiter) -> Unit,
) : BaseNavigator(router) {
    override val parent: Navigator? = null
    override val backStack: List<Screen> = emptyList()
    val plainPushes = mutableListOf<Screen>()

    override fun goTo(screen: Screen) {
        plainPushes += screen
    }

    override fun goToAwaited(screen: Screen, awaiter: ResultAwaiter) = onAwaited(screen, awaiter)

    override fun pop(result: PopResult?): Boolean = false

    override fun resetRoot(screen: Screen) = Unit

    fun keyFor(screen: Screen): String = resultKeyFor(screen)
}

/**
 * The exact-correlation contract for destinations that bypass stack discipline (custom fragment
 * adapters showing dialogs or activities): every awaited navigation carries its own caller, so
 * out-of-order answers, plain same-class pushes, double delivery, cancellation, and navigation
 * failures all resolve (or refuse to resolve) exactly the right request.
 */
class ResultCorrelationTest {

    @Test
    fun outOfOrderAnswersResolveTheirOwnCallers() = runTest {
        val router = ResultRouter()
        val awaiters = mutableListOf<ResultAwaiter>()
        val navigator = FakeNavigator(router) { _, a -> awaiters += a }

        val first = async { navigator.goToForResult(AskScreen("first")) }
        runCurrent()
        val second = async { navigator.goToForResult(AskScreen("second")) }
        runCurrent()

        // The FIRST dialog answers while the second is still open. LIFO by key would hand this
        // to the second caller; exact correlation must not.
        awaiters[0].complete(Answer("for-first"))
        awaiters[1].complete(Answer("for-second"))
        assertEquals(Answer("for-first"), first.await())
        assertEquals(Answer("for-second"), second.await())
    }

    @Test
    fun plainGoToNeverStealsAnAwaiter() = runTest {
        val router = ResultRouter()
        val awaiters = mutableListOf<ResultAwaiter>()
        val navigator = FakeNavigator(router) { _, a -> awaiters += a }

        val caller = async { navigator.goToForResult(AskScreen("awaited")) }
        runCurrent()
        // A plain push of the SAME class while the caller waits: no awaiter is created for it,
        // so nothing that destination does can answer the outstanding request.
        navigator.goTo(AskScreen("plain"))
        assertEquals(1, awaiters.size)
        assertEquals(1, navigator.plainPushes.size)
        assertFalse(caller.isCompleted)

        awaiters[0].complete(Answer("mine"))
        assertEquals(Answer("mine"), caller.await())
    }

    @Test
    fun completeIsOneShot() = runTest {
        val router = ResultRouter()
        val awaiters = mutableListOf<ResultAwaiter>()
        val navigator = FakeNavigator(router) { _, a -> awaiters += a }

        val caller = async { navigator.goToForResult(AskScreen("x")) }
        runCurrent()
        awaiters[0].complete(Answer("first"))
        awaiters[0].complete(Answer("second")) // dismiss callback firing after the result
        assertEquals(Answer("first"), caller.await())
        assertEquals(0, router.pendingCountFor(navigator.keyFor(AskScreen("x"))))
    }

    @Test
    fun cancellationUnregistersAndLateDeliveryNoOps() = runTest {
        val router = ResultRouter()
        val awaiters = mutableListOf<ResultAwaiter>()
        val navigator = FakeNavigator(router) { _, a -> awaiters += a }

        val job = launch { navigator.goToForResult(AskScreen("x")) }
        runCurrent()
        assertEquals(1, router.pendingCountFor(navigator.keyFor(AskScreen("x"))))
        job.cancelAndJoin()
        assertEquals(0, router.pendingCountFor(navigator.keyFor(AskScreen("x"))))

        // The screen the user was looking at eventually answers: nobody is resumed, nothing throws.
        awaiters[0].complete(Answer("late"))
    }

    @Test
    fun navigationFailureLeavesNoOrphanedAwaiter() = runTest {
        val router = ResultRouter()
        val navigator = FakeNavigator(router) { _, _ -> error("adapter boom") }

        val attempt = async { runCatching { navigator.goToForResult(AskScreen("x")) } }
        runCurrent()
        assertTrue(attempt.await().exceptionOrNull() is IllegalStateException)
        // The registration made before the failed navigation must be gone.
        assertEquals(0, router.pendingCountFor(navigator.keyFor(AskScreen("x"))))
    }
}
