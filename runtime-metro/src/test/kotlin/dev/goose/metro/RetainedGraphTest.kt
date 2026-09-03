package dev.goose.metro

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The non-composable retained-graph contract ([retainedGraph]): the graph is created once per
 * owner-and-key and reused, and clearing the owner's store releases it exactly once — onRelease
 * first, then [AutoCloseable.close] when the graph implements it.
 */
class RetainedGraphTest {

    private class FakeOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class ClosingGraph(private val events: MutableList<String>) : AutoCloseable {
        override fun close() {
            events += "close"
        }
    }

    @Test
    fun `the graph is created once and reused across calls`() {
        val owner = FakeOwner()
        var creations = 0

        val first = retainedGraph(owner) { creations++; Any() }
        val second = retainedGraph(owner) { creations++; Any() }

        assertSame(first, second)
        assertEquals(1, creations)
    }

    @Test
    fun `distinct keys retain distinct graphs`() {
        val owner = FakeOwner()

        val checkout = retainedGraph(owner, key = "checkout") { Any() }
        val support = retainedGraph(owner, key = "support") { Any() }

        assertNotSame(checkout, support)
        assertSame(checkout, retainedGraph(owner, key = "checkout") { Any() })
    }

    @Test
    fun `clearing the owner runs onRelease before AutoCloseable close, exactly once`() {
        val owner = FakeOwner()
        val events = mutableListOf<String>()
        retainedGraph(owner, onRelease = { events += "onRelease" }) { ClosingGraph(events) }

        owner.viewModelStore.clear()

        assertEquals(listOf("onRelease", "close"), events)
    }

    @Test
    fun `a graph that is not AutoCloseable still gets onRelease with the graph instance`() {
        val owner = FakeOwner()
        val graph = Any()
        var released: Any? = null
        retainedGraph(owner, onRelease = { released = it }) { graph }

        owner.viewModelStore.clear()

        assertSame(graph, released)
    }

    @Test
    fun `the latest onRelease wins when the graph is re-obtained`() {
        val owner = FakeOwner()
        val events = mutableListOf<String>()
        retainedGraph(owner, onRelease = { events += "stale" }) { Any() }
        retainedGraph(owner, onRelease = { events += "current" }) { Any() }

        owner.viewModelStore.clear()

        assertEquals(listOf("current"), events)
    }

    @Test
    fun `clearing an owner whose holder never built a graph is a no-op`() {
        val owner = FakeOwner()
        val holder = ViewModelProvider(owner)["empty", RetainedGraphHolder::class.java]

        owner.viewModelStore.clear()

        assertEquals(null, holder.graph)
    }
}
