package dev.goose.fragment

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.runtime.PopResult
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenWithResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@Serializable
private data class PickerScreen(val id: String) : ScreenWithResult<Picked>

@Serializable
private data class Picked(val value: String) : PopResult

/**
 * The exact-correlation contract through the REAL fragment machinery: a genuine
 * [FragmentNavigator] over a real FragmentActivity's FragmentManager, with a contributed
 * [FragmentScreenNavigation] override producing genuine [FragmentNavigationRequest]s, the
 * shape a `startDialogForResult`-style adapter sees.
 */
@RunWith(AndroidJUnit4::class)
class FragmentRequestCorrelationTest {

    private fun withNavigator(
        block: (FragmentNavigator, MutableList<FragmentNavigationRequest>) -> Unit,
    ) {
        val activity = org.robolectric.Robolectric.buildActivity(FragmentActivity::class.java)
            .setup().get()
        val requests = mutableListOf<FragmentNavigationRequest>()
        val navigator = FragmentNavigator(
            fragmentManager = activity.supportFragmentManager,
            containerId = android.R.id.content,
            binders = emptyMap(),
            resultRouter = ResultRouter(),
            navigationOverrides = mapOf(
                PickerScreen::class to FragmentScreenNavigation { request -> requests += request },
            ),
            stackTag = "test-stack",
        )
        block(navigator, requests)
    }

    /** Two same-class custom destinations open concurrently; the FIRST answers last. */
    @Test
    fun twoSameClassDestinationsCompletingOutOfOrder() = withNavigator { navigator, requests ->
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate)
        val first = scope.async { navigator.goToForResult(PickerScreen("first")) }
        val second = scope.async { navigator.goToForResult(PickerScreen("second")) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, requests.size)

        // Answer in REVERSE order of opening: each caller must get its own dialog's answer.
        requests[1].deliverResult(Picked("for-second"))
        requests[0].deliverResult(Picked("for-first"))
        shadowOf(Looper.getMainLooper()).idle()

        var results: Pair<Picked?, Picked?>? = null
        scope.launch { results = first.await() to second.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Picked("for-first") to Picked("for-second"), results)
    }

    /** A plain same-class goTo while a caller awaits: its request cannot answer the awaiter. */
    @Test
    fun plainGoToRequestCannotAnswerTheAwaitingCaller() = withNavigator { navigator, requests ->
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate)
        val caller = scope.async { navigator.goToForResult(PickerScreen("awaited")) }
        navigator.goTo(PickerScreen("plain"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, requests.size)

        // The plain destination "answers": nobody may resume.
        requests[1].deliverResult(Picked("stolen?"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(caller.isCompleted)

        // The real dialog answers its own caller.
        requests[0].deliverResult(Picked("mine"))
        shadowOf(Looper.getMainLooper()).idle()
        var result: Picked? = null
        scope.launch { result = caller.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Picked("mine"), result)
    }

    /** deliverResult twice on one request: the second call must deliver nothing. */
    @Test
    fun deliverResultIsOneShotPerRequest() = withNavigator { navigator, requests ->
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate)
        val first = scope.async { navigator.goToForResult(PickerScreen("a")) }
        val second = scope.async { navigator.goToForResult(PickerScreen("b")) }
        shadowOf(Looper.getMainLooper()).idle()

        // Result callback then dismiss callback on the SAME dialog.
        requests[0].deliverResult(Picked("real"))
        requests[0].deliverResult(null)
        shadowOf(Looper.getMainLooper()).idle()

        var firstResult: Picked? = null
        scope.launch { firstResult = first.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Picked("real"), firstResult)
        // The double delivery consumed nothing from the second caller.
        assertFalse(second.isCompleted)
        requests[1].deliverResult(Picked("second"))
    }
}
