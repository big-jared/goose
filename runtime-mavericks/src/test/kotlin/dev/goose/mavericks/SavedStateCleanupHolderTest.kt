package dev.goose.mavericks

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pop-time cleanup vehicle for Mavericks SavedStateProviders: a [SavedStateCleanupHolder]
 * rides the entry's ViewModelStore and runs its (re-assignable) action exactly when the store
 * clears, so popped screens and flows unregister their providers from the activity registry.
 */
class SavedStateCleanupHolderTest {

    private class FakeOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun holderIn(owner: ViewModelStoreOwner, key: String): SavedStateCleanupHolder =
        ViewModelProvider(owner)[key, SavedStateCleanupHolder::class.java]

    @Test
    fun `clearing the owning store runs the cleanup action once`() {
        val owner = FakeOwner()
        var runs = 0
        holderIn(owner, "cleanup").onClearedAction = { runs++ }

        owner.viewModelStore.clear()

        assertEquals(1, runs)
    }

    @Test
    fun `the action assigned last is the one that runs`() {
        val owner = FakeOwner()
        val events = mutableListOf<String>()
        holderIn(owner, "cleanup").onClearedAction = { events += "stale registry" }
        holderIn(owner, "cleanup").onClearedAction = { events += "current registry" }

        owner.viewModelStore.clear()

        assertEquals(listOf("current registry"), events)
    }

    @Test
    fun `holders are per key, so one VM's cleanup never overwrites another's`() {
        val owner = FakeOwner()
        val events = mutableListOf<String>()
        holderIn(owner, "goose:ssrCleanup:VmA").onClearedAction = { events += "a" }
        holderIn(owner, "goose:ssrCleanup:VmB").onClearedAction = { events += "b" }

        owner.viewModelStore.clear()

        assertEquals(setOf("a", "b"), events.toSet())
    }

    @Test
    fun `a holder with no action clears quietly`() {
        val owner = FakeOwner()
        holderIn(owner, "cleanup")

        owner.viewModelStore.clear()
    }
}
