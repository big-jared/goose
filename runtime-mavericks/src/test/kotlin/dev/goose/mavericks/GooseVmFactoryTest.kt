package dev.goose.mavericks

import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.InternalMavericksApi
import com.airbnb.mvrx.Mavericks
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.MavericksViewModelProvider
import com.airbnb.mvrx.ViewModelContext
import com.airbnb.mvrx.withState
import dev.goose.runtime.Navigator
import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenWithResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

internal data class PinState(val count: Int = 0) : MavericksState

/** The shape of a migrated screen ViewModel: goose factory companion, navigator constructor arg. */
internal class PinViewModel(state: PinState, val navigator: Navigator) :
    MavericksViewModel<PinState>(state) {
    companion object :
        MavericksViewModelFactory<PinViewModel, PinState> by gooseVmFactory(PinViewModel::class)
}

/** A same-state VM of the WRONG class, for the created-outside-its-own-call guard. */
internal class ImposterViewModel(state: PinState) : MavericksViewModel<PinState>(state)

internal class RecordingNavigator : Navigator {
    override val parent: Navigator? = null
    override val backStack: List<Screen> = emptyList()

    override fun goTo(screen: Screen) = Unit

    override fun pop(result: PopResult?): Boolean = false

    override fun resetRoot(screen: Screen) = Unit

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? = null
}

/**
 * The ThreadLocal handoff behind [gooseVmFactory]: Mavericks resolves factories through the VM's
 * companion with no seam for extra arguments, so [screenViewModel] parks the navigator and the
 * creation lambda in [GooseVmLocator] around the synchronous `get()` call. Pins that the factory
 * wires the scope's navigator and initial state through, that the ThreadLocal is cleaned up on
 * every exit path (so nothing leaks past the creation call), and that nested or out-of-scope
 * creation fails fast with actionable guidance.
 */
@OptIn(InternalMavericksApi::class)
@RunWith(AndroidJUnit4::class)
class GooseVmFactoryTest {

    private val navigator = RecordingNavigator()

    @Before
    fun setUp() {
        Mavericks.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun vmContext(): ViewModelContext {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        return ActivityViewModelContext(activity, args = null)
    }

    private fun pinScope(create: (PinState, Navigator) -> MavericksViewModel<*>) =
        GooseVmLocator.Scope(navigator) { state, nav -> create(state as PinState, nav) }

    @Test
    fun `creation outside a goose scope fails with the screenViewModel guidance`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            PinViewModel.create(vmContext(), PinState())
        }

        assertTrue(failure.message.orEmpty().contains("outside a Goose host"))
        assertTrue(failure.message.orEmpty().contains("screenViewModel"))
    }

    @Test
    fun `creation inside a scope delegates to the lambda with the scope's navigator and state`() {
        val vm = GooseVmLocator.withScope(pinScope { state, nav -> PinViewModel(state, nav) }) {
            checkNotNull(PinViewModel.create(vmContext(), PinState(count = 7)))
        }

        assertNotNull(vm)
        assertSame(navigator, vm.navigator)
        assertEquals(7, withState(vm) { it }.count)
    }

    @Test
    fun `a lambda producing the wrong ViewModel class fails fast`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            GooseVmLocator.withScope(pinScope { state, _ -> ImposterViewModel(state) }) {
                PinViewModel.create(vmContext(), PinState())
            }
        }

        assertTrue(failure.message.orEmpty().contains("ImposterViewModel"))
    }

    @Test
    fun `the ThreadLocal is set inside withScope and cleared after it returns`() {
        val scope = pinScope { state, nav -> PinViewModel(state, nav) }

        val seenInside = GooseVmLocator.withScope(scope) { GooseVmLocator.current }

        assertSame(scope, seenInside)
        assertNull(GooseVmLocator.current)
    }

    @Test
    fun `the ThreadLocal is cleared even when the block throws`() {
        val scope = pinScope { state, nav -> PinViewModel(state, nav) }

        assertThrows(IllegalStateException::class.java) {
            GooseVmLocator.withScope(scope) { error("creation blew up") }
        }

        assertNull(GooseVmLocator.current)
    }

    @Test
    fun `nested goose VM creation fails fast`() {
        val scope = pinScope { state, nav -> PinViewModel(state, nav) }

        val failure = assertThrows(IllegalStateException::class.java) {
            GooseVmLocator.withScope(scope) {
                GooseVmLocator.withScope(scope) { }
            }
        }

        assertTrue(failure.message.orEmpty().contains("Nested"))
        assertNull(GooseVmLocator.current)
    }

    @Test
    fun `the scope is thread-local, invisible to other threads`() {
        val scope = pinScope { state, nav -> PinViewModel(state, nav) }
        var seenOnOtherThread: GooseVmLocator.Scope? = scope

        GooseVmLocator.withScope(scope) {
            val thread = Thread { seenOnOtherThread = GooseVmLocator.current }
            thread.start()
            thread.join()
        }

        assertNull(seenOnOtherThread)
    }

    @Test
    fun `MavericksViewModelProvider resolves the companion factory and wires the navigator through`() {
        val vm = GooseVmLocator.withScope(pinScope { state, nav -> PinViewModel(state, nav) }) {
            MavericksViewModelProvider.get(
                viewModelClass = PinViewModel::class.java,
                stateClass = PinState::class.java,
                viewModelContext = vmContext(),
                key = "pin",
            )
        }

        assertSame(navigator, vm.navigator)
        assertNull(GooseVmLocator.current)
    }

    @Test
    fun `an existing ViewModel is returned without consulting the goose scope`() {
        val context = vmContext()
        var creations = 0
        val scope = pinScope { state, nav ->
            creations++
            PinViewModel(state, nav)
        }

        val first = GooseVmLocator.withScope(scope) {
            MavericksViewModelProvider.get(
                viewModelClass = PinViewModel::class.java,
                stateClass = PinState::class.java,
                viewModelContext = context,
                key = "pin",
            )
        }
        // No scope around the second lookup: retrieval must not re-create.
        val second = MavericksViewModelProvider.get(
            viewModelClass = PinViewModel::class.java,
            stateClass = PinState::class.java,
            viewModelContext = context,
            key = "pin",
        )

        assertSame(first, second)
        assertEquals(1, creations)
    }
}
