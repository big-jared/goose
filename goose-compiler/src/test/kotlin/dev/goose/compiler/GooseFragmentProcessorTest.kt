@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import java.io.OutputStream
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The @GooseFragment grammar: the happy path generates a registration whose Bundle mapping
 * type-checks against the screen's own properties, and every rejected shape produces a compile
 * error naming the rule. Stubs stand in for android/goose types; matching is by qualified name.
 */
class GooseFragmentProcessorTest {

    private val runtimeStubs = SourceFile.kotlin(
        "RuntimeStubs.kt",
        """
        package dev.goose.runtime
        interface Screen
        fun interface ScreenEntry {
            fun Content(screen: Screen)
        }
        """.trimIndent(),
    )
    private val serializationStub = SourceFile.kotlin(
        "SerializationStub.kt",
        """
        package kotlinx.serialization
        annotation class Serializable
        """.trimIndent(),
    )
    private val metroStub = SourceFile.kotlin(
        "MetroStub.kt",
        """
        package dev.zacsweers.metro
        import kotlin.reflect.KClass
        annotation class ContributesTo(val scope: KClass<*>)
        annotation class Provides
        annotation class IntoMap
        annotation class ClassKey(val value: KClass<*>)
        abstract class AppScope
        """.trimIndent(),
    )
    private val androidStubs = SourceFile.kotlin(
        "AndroidStubs.kt",
        """
        package android.os
        class Bundle
        """.trimIndent(),
    )
    private val fragmentStub = SourceFile.kotlin(
        "FragmentStub.kt",
        """
        package androidx.fragment.app
        open class Fragment
        """.trimIndent(),
    )
    private val bundleOfStub = SourceFile.kotlin(
        "BundleOfStub.kt",
        """
        package androidx.core.os
        fun bundleOf(vararg pairs: Pair<String, Any?>): android.os.Bundle = android.os.Bundle()
        """.trimIndent(),
    )
    private val gooseFragmentStub = SourceFile.kotlin(
        "GooseFragmentStub.kt",
        """
        package dev.goose.fragment
        import dev.goose.runtime.Screen
        import dev.goose.runtime.ScreenEntry
        import kotlin.reflect.KClass
        annotation class GooseFragment(val screen: KClass<out Screen>, val scope: KClass<*> = Unit::class)
        fun <F : androidx.fragment.app.Fragment, S : Screen> fragmentScreenEntry(
            arguments: (S) -> android.os.Bundle = { android.os.Bundle() },
        ): ScreenEntry = ScreenEntry { }
        """.trimIndent(),
    )

    private fun compile(source: String): JvmCompilationResult {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                runtimeStubs, serializationStub, metroStub, androidStubs, fragmentStub,
                bundleOfStub, gooseFragmentStub,
                SourceFile.kotlin("Test.kt", source),
            )
            configureKsp {
                symbolProcessorProviders += GooseUiProcessorProvider()
            }
            inheritClassPath = true
            messageOutputStream = OutputStream.nullOutputStream()
        }
        return compilation.compile()
    }

    private fun assertError(source: String, expectedMessage: String) {
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            "expected \"$expectedMessage\" in:\n${result.messages}",
            result.messages.contains(expectedMessage),
        )
    }

    @Test
    fun dataClassScreenGeneratesTypedBundleMapping() {
        // The generated registration reads screen.termsId / screen.revision, so a wrong
        // property name would fail THIS compilation — the mapping is type-checked.
        val result = compile(
            """
            package test
            import androidx.fragment.app.Fragment
            import dev.goose.fragment.GooseFragment
            import dev.goose.runtime.Screen
            import kotlinx.serialization.Serializable

            @Serializable data class TermsScreen(val termsId: String, val revision: Int) : Screen

            @GooseFragment(TermsScreen::class)
            class TermsFragment : Fragment()
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun objectScreenGeneratesArgumentlessEntry() {
        val result = compile(
            """
            package test
            import androidx.fragment.app.Fragment
            import dev.goose.fragment.GooseFragment
            import dev.goose.runtime.Screen
            import kotlinx.serialization.Serializable

            @Serializable data object SupportScreen : Screen

            @GooseFragment(SupportScreen::class)
            class SupportFragment : Fragment()
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun scopedRegistrationContributesToTheGivenScope() {
        val result = compile(
            """
            package test
            import androidx.fragment.app.Fragment
            import dev.goose.fragment.GooseFragment
            import dev.goose.runtime.Screen
            import kotlinx.serialization.Serializable

            abstract class SessionScope
            @Serializable data class ChatScreen(val ticketId: String) : Screen

            @GooseFragment(ChatScreen::class, scope = SessionScope::class)
            class ChatFragment : Fragment()
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun nonFragmentClassIsRejected() = assertError(
        """
        package test
        import dev.goose.fragment.GooseFragment
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable data object HomeScreen : Screen

        @GooseFragment(HomeScreen::class)
        class NotAFragment
        """.trimIndent(),
        "must extend androidx.fragment.app.Fragment",
    )

    @Test
    fun nonSerializableScreenIsRejected() = assertError(
        """
        package test
        import androidx.fragment.app.Fragment
        import dev.goose.fragment.GooseFragment
        import dev.goose.runtime.Screen

        data object HomeScreen : Screen

        @GooseFragment(HomeScreen::class)
        class HomeFragment : Fragment()
        """.trimIndent(),
        "must be @Serializable",
    )

    @Test
    fun privateFragmentIsRejected() = assertError(
        """
        package test
        import androidx.fragment.app.Fragment
        import dev.goose.fragment.GooseFragment
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable data object HomeScreen : Screen

        @GooseFragment(HomeScreen::class)
        private class HomeFragment : Fragment()
        """.trimIndent(),
        "must not be private",
    )

    @Test
    fun nonPropertyConstructorParameterIsRejected() = assertError(
        """
        package test
        import androidx.fragment.app.Fragment
        import dev.goose.fragment.GooseFragment
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen(id: String) : Screen

        @GooseFragment(HomeScreen::class)
        class HomeFragment : Fragment()
        """.trimIndent(),
        "must be a val/var property",
    )
}
