package dev.goose.metro

import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private data class ProductScreen(val id: String) : Screen

private data class ScopedScreen(val id: String) : Screen

private data object UnregisteredScreen : Screen

/** A provider that counts invocations, to observe the registry's per-class memoization. */
private class CountingProvider(private val entry: ScreenEntry) : Provider<ScreenEntry> {
    var invocations = 0

    override fun invoke(): ScreenEntry {
        invocations++
        return entry
    }
}

/**
 * The registry lookup contract: entries resolve by screen class and are memoized per class in
 * the registry that OWNS them, a miss fails with the classpath hint, and child registries
 * (GooseScope) shadow their parent for their own screens while falling back for everything else.
 */
class ScreenRegistryTest {

    private val rootEntry = ScreenEntry { _, _ -> }
    private val scopedEntry = ScreenEntry { _, _ -> }

    @Test
    fun `a registered screen resolves its entry`() {
        val registry = ScreenRegistry(mapOf(ProductScreen::class to CountingProvider(rootEntry)))

        assertSame(rootEntry, registry.entryFor(ProductScreen("p1")))
    }

    @Test
    fun `an unregistered screen fails with the classpath hint`() {
        val registry = ScreenRegistry(emptyMap())

        val failure = assertThrows(IllegalStateException::class.java) {
            registry.entryFor(UnregisteredScreen)
        }

        assertTrue(failure.message.orEmpty().contains("UnregisteredScreen"))
        assertTrue(failure.message.orEmpty().contains("No ScreenEntry"))
    }

    @Test
    fun `entries are memoized per screen class, not per screen instance`() {
        val provider = CountingProvider(rootEntry)
        val registry = ScreenRegistry(mapOf(ProductScreen::class to provider))

        registry.entryFor(ProductScreen("first"))
        registry.entryFor(ProductScreen("second"))
        registry.entryFor(ProductScreen("first"))

        assertEquals(1, provider.invocations)
    }

    @Test
    fun `a child registry resolves its own entry over the parent's for the same class`() {
        val parent = ScreenRegistry(mapOf(ProductScreen::class to CountingProvider(rootEntry)))
        val child = ScreenRegistry(
            mapOf(ProductScreen::class to CountingProvider(scopedEntry)),
            parent = parent,
        )

        assertSame(scopedEntry, child.entryFor(ProductScreen("p1")))
        assertSame(rootEntry, parent.entryFor(ProductScreen("p1")))
    }

    @Test
    fun `a child registry falls back to the parent for unscoped screens`() {
        val parent = ScreenRegistry(mapOf(ProductScreen::class to CountingProvider(rootEntry)))
        val child = ScreenRegistry(
            mapOf(ScopedScreen::class to CountingProvider(scopedEntry)),
            parent = parent,
        )

        assertSame(rootEntry, child.entryFor(ProductScreen("p1")))
        assertSame(scopedEntry, child.entryFor(ScopedScreen("s1")))
    }

    @Test
    fun `parent-owned entries resolved through a child are cached in the parent`() {
        val provider = CountingProvider(rootEntry)
        val parent = ScreenRegistry(mapOf(ProductScreen::class to provider))
        val child = ScreenRegistry(emptyMap(), parent = parent)

        child.entryFor(ProductScreen("p1"))
        parent.entryFor(ProductScreen("p1"))
        child.entryFor(ProductScreen("p2"))

        assertEquals(1, provider.invocations)
    }

    @Test
    fun `child-scoped screens never leak into the parent registry`() {
        val parent = ScreenRegistry(emptyMap())
        val child = ScreenRegistry(
            mapOf(ScopedScreen::class to CountingProvider(scopedEntry)),
            parent = parent,
        )

        assertSame(scopedEntry, child.entryFor(ScopedScreen("s1")))
        assertThrows(IllegalStateException::class.java) { parent.entryFor(ScopedScreen("s1")) }
    }
}
