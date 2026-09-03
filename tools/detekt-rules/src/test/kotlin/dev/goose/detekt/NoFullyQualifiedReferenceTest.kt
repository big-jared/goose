package dev.goose.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoFullyQualifiedReferenceTest {

    private val rule = NoFullyQualifiedReference()

    @Test
    fun `flags fully qualified constructor call`() {
        val findings = rule.lint(
            """
            fun props() = androidx.compose.ui.window.DialogProperties()
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `flags fully qualified type reference`() {
        val findings = rule.lint(
            """
            fun host(fm: androidx.fragment.app.FragmentManager) = fm
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `flags fully qualified annotation argument`() {
        val findings = rule.lint(
            """
            @OptIn(com.airbnb.mvrx.InternalMavericksApi::class)
            fun risky() = Unit
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a chained call once, at the qualified reference`() {
        val findings = rule.lint(
            """
            val module = dev.goose.metro.Goose.Builder().build()
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("dev.goose.metro.Goose"))
    }

    @Test
    fun `ignores imports and package directives`() {
        val findings = rule.lint(
            """
            package dev.goose.sample

            import androidx.fragment.app.FragmentManager

            fun host(fm: FragmentManager) = fm
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }

    @Test
    fun `ignores ordinary member chains and companion access`() {
        val findings = rule.lint(
            """
            fun scope(dispatchers: Dispatchers) = Dispatchers.Main.immediate
            fun chain(screen: Screen) = screen.transitions.enter
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }
}
