package dev.goose.nav3

import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

private data class RecordedScreen(val id: String) : Screen

/** A NavKey from some other navigation setup — neither a push record nor a Goose screen. */
private data class ForeignKey(val id: String) : NavKey

/**
 * The unwrap contract at the NavKey boundary: [asScreen] unwraps push records, passes raw
 * screens through (hosts may seed or mutate a stack directly), and REFUSES any other NavKey —
 * a foreign key on a Goose stack is a wiring bug, not something to render.
 */
class PushRecordsTest {

    @Test
    fun asScreenUnwrapsAPushRecord() {
        val screen = RecordedScreen("x")
        assertSame(screen, screen.pushed().asScreen())
    }

    @Test
    fun asScreenPassesARawScreenThrough() {
        val screen: NavKey = RecordedScreen("raw")
        assertSame(screen, screen.asScreen())
    }

    @Test
    fun asScreenRejectsAForeignNavKey() {
        assertThrows(IllegalStateException::class.java) {
            ForeignKey("alien").asScreen()
        }
    }

    @Test
    fun pushingTheSameScreenTwiceMakesDistinctRecordsOverEqualScreens() {
        val screen = RecordedScreen("dup")

        val first = screen.pushed()
        val second = screen.pushed()

        assertNotEquals(first, second)
        assertEquals(first.screen, second.screen)
    }
}
