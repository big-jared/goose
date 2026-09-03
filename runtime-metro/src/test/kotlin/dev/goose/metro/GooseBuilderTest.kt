package dev.goose.metro

import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.providerOf
import kotlin.reflect.KClass
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private data object AlphaScreen : Screen

private data object BetaScreen : Screen

/**
 * The builder assembly contract: explicit [Goose.Builder.addScreen]/[Goose.Builder.addUi]
 * registrations win over bulk DI multibindings regardless of call order, the last explicit
 * registration per class wins, and [asGoose] accepts exactly the two graph shapes hosts may be
 * handed (a Goose, or a graph implementing [GooseRuntimeAccessors]).
 */
class GooseBuilderTest {

    private val entryA = ScreenEntry { _, _ -> }
    private val entryB = ScreenEntry { _, _ -> }

    private fun bulk(vararg pairs: Pair<KClass<*>, ScreenEntry>): Map<KClass<*>, Provider<ScreenEntry>> =
        pairs.associate { (klass, entry) -> klass to providerOf(entry) }

    @Test
    fun `the last explicit registration for a screen class wins`() {
        val goose = Goose.Builder()
            .addScreen(AlphaScreen::class, entryA)
            .addScreen(AlphaScreen::class, entryB)
            .build()

        assertSame(entryB, goose.screenRegistry.entryFor(AlphaScreen))
    }

    @Test
    fun `an explicit registration beats a bulk entry added afterwards`() {
        val goose = Goose.Builder()
            .addScreen(AlphaScreen::class, entryA)
            .addScreens(bulk(AlphaScreen::class to entryB))
            .build()

        assertSame(entryA, goose.screenRegistry.entryFor(AlphaScreen))
    }

    @Test
    fun `an explicit registration beats a bulk entry added before it`() {
        val goose = Goose.Builder()
            .addScreens(bulk(AlphaScreen::class to entryB))
            .addScreen(AlphaScreen::class, entryA)
            .build()

        assertSame(entryA, goose.screenRegistry.entryFor(AlphaScreen))
    }

    @Test
    fun `bulk entries resolve where no explicit registration exists`() {
        val goose = Goose.Builder()
            .addScreens(bulk(AlphaScreen::class to entryA, BetaScreen::class to entryB))
            .build()

        assertSame(entryA, goose.screenRegistry.entryFor(AlphaScreen))
        assertSame(entryB, goose.screenRegistry.entryFor(BetaScreen))
    }

    @Test
    fun `addUi registers under the reified screen class`() {
        val goose = Goose.Builder()
            .addUi<AlphaScreen> { _, _ -> }
            .build()

        // The sugar wraps the content in its own entry; resolving without throwing is the contract.
        goose.screenRegistry.entryFor(AlphaScreen)
    }

    @Test
    fun `asGoose returns a Goose unchanged`() {
        val goose = Goose.Builder().build()

        assertSame(goose, (goose as Any).asGoose())
    }

    @Test
    fun `asGoose unwraps a graph implementing GooseRuntimeAccessors`() {
        val built = Goose.Builder().build()
        val graph = object : GooseRuntimeAccessors {
            override val goose: Goose = built
        }

        assertSame(built, (graph as Any).asGoose())
    }

    @Test
    fun `asGoose rejects an arbitrary object with guidance`() {
        val failure = assertThrows(IllegalStateException::class.java) { "not a graph".asGoose() }

        assertTrue(failure.message.orEmpty().contains("GooseRuntimeAccessors"))
    }
}
