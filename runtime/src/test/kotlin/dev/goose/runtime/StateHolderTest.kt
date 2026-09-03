package dev.goose.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private data class CounterState(val count: Int = 0, val label: String = "")

private class CounterHolder : StateHolder<CounterState>(CounterState()) {
    var clearedCount = 0
    var scopeActiveDuringOnCleared: Boolean? = null

    fun increment() = setState { copy(count = count + 1) }

    fun label(label: String) = setState { copy(label = label) }

    fun launchForever(): Job = holderScope.launch { awaitCancellation() }

    override fun onCleared() {
        clearedCount++
        scopeActiveDuringOnCleared = holderScope.isActive
    }
}

/**
 * The presenter-lifecycle contract of [StateHolder]: [StateHolder.state] is a single stream
 * reduced by setState against the CURRENT state (updates compose, they don't clobber), and
 * clearing cancels [StateHolder.holderScope] BEFORE onCleared runs — work is already stopped by
 * the time cleanup code observes the teardown, matching ViewModel's own ordering.
 */
class StateHolderTest {

    // holderScope carries Dispatchers.Main.immediate, so every construction needs a main
    // dispatcher on this JVM; runTest below picks up this dispatcher's scheduler automatically.
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateStartsAtTheInitialValue() {
        assertEquals(CounterState(count = 0, label = ""), CounterHolder().state.value)
    }

    @Test
    fun setStateReducesAgainstTheCurrentState() {
        val holder = CounterHolder()

        holder.increment()
        holder.increment()
        holder.label("hi")

        // Each reducer saw its predecessor's output: neither field clobbered the other.
        assertEquals(CounterState(count = 2, label = "hi"), holder.state.value)
    }

    @Test
    fun clearCancelsTheScopeAndItsWorkThenRunsOnCleared() = runTest {
        val holder = CounterHolder()
        val work = holder.launchForever()
        runCurrent()
        assertTrue(work.isActive)

        holder.clear()

        assertTrue(work.isCancelled)
        assertEquals(1, holder.clearedCount)
        assertEquals(false, holder.scopeActiveDuringOnCleared)
    }

    @Test
    fun stateRemainsReadableAfterClear() {
        val holder = CounterHolder()
        holder.increment()

        holder.clear()

        // A late collector (a disposing composition) still sees the final state, no throw.
        assertEquals(1, holder.state.value.count)
        assertFalse(holder.state.value.label.isNotEmpty())
    }
}
