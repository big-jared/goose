@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import java.io.OutputStream
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The @GooseFragmentNavigation / @GooseFragmentBinder grammar, enforced: every rejected shape
 * produces a compile error naming the rule, and the happy paths generate registrations that
 * themselves compile. Stub declarations stand in for fragment/metro/goose types; the processor
 * matches by qualified name only.
 */
class GooseFragmentContributionsTest {

    private val runtimeStub = SourceFile.kotlin(
        "RuntimeStub.kt",
        """
        package dev.goose.runtime
        interface Screen
        interface Presentation
        """.trimIndent(),
    )
    private val fragmentStub = SourceFile.kotlin(
        "FragmentStub.kt",
        """
        package androidx.fragment.app
        open class Fragment
        """.trimIndent(),
    )
    private val gooseFragmentStub = SourceFile.kotlin(
        "GooseFragmentStub.kt",
        """
        package dev.goose.fragment
        import dev.goose.runtime.Screen
        import kotlin.reflect.KClass
        annotation class GooseFragmentNavigation(val screen: KClass<out Screen>)
        annotation class GooseFragmentBinder(val screen: KClass<out Screen>)
        annotation class GoosePresentationNavigation(val presentation: KClass<out dev.goose.runtime.Presentation>)
        annotation class PresentationNavigations
        class FragmentNavigationRequest
        fun interface FragmentScreenNavigation {
            fun navigate(request: FragmentNavigationRequest)
        }
        fun interface ScreenFragmentBinder {
            fun createFragment(screen: Screen): androidx.fragment.app.Fragment
        }
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
        annotation class Qualifier
        @Qualifier annotation class Named(val value: String)
        abstract class AppScope
        """.trimIndent(),
    )

    private fun compile(source: String): JvmCompilationResult {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                runtimeStub, fragmentStub, gooseFragmentStub, metroStub,
                SourceFile.kotlin("Test.kt", source),
            )
            configureKsp {
                symbolProcessorProviders += GooseFragmentProcessorProvider()
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
    fun navigationClassWithDependenciesCompilesAndRegisters() {
        val result = compile(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen
            class Analytics

            @GooseFragmentNavigation(HelpScreen::class)
            class HelpNavigation(private val analytics: Analytics) : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun navigationObjectCompilesAndRegisters() {
        val result = compile(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            object HelpNavigation : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun binderClassCompilesAndRegisters() {
        val result = compile(
            """
            package test
            import androidx.fragment.app.Fragment
            import dev.goose.fragment.GooseFragmentBinder
            import dev.goose.fragment.ScreenFragmentBinder
            import dev.goose.runtime.Screen

            class DetailScreen : Screen

            @GooseFragmentBinder(DetailScreen::class)
            class DetailFragmentBinder : ScreenFragmentBinder {
                override fun createFragment(screen: Screen): Fragment = Fragment()
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun qualifiersOnConstructorParametersCarryOver() {
        val result = compile(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen
            import dev.zacsweers.metro.Named

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            class HelpNavigation(@param:Named("helpUrl") private val url: String) : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = result.sourceFor("HelpNavigationGooseNavigationModule.kt")
        assertTrue(generated, generated.contains("@dev.zacsweers.metro.Named(value = \"helpUrl\")"))
    }

    @Test
    fun mustImplementTheInteropInterface() {
        assertError(
            """
            package test
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            class HelpNavigation
            """.trimIndent(),
            "must implement FragmentScreenNavigation",
        )
    }

    @Test
    fun binderMustImplementItsOwnInterface() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentBinder
            import dev.goose.runtime.Screen

            class DetailScreen : Screen

            @GooseFragmentBinder(DetailScreen::class)
            class DetailBinder : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
            "must implement ScreenFragmentBinder",
        )
    }

    @Test
    fun privateClassIsRejected() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            private class HelpNavigation : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
            "must not be private",
        )
    }

    @Test
    fun abstractClassIsRejected() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            abstract class HelpNavigation : FragmentScreenNavigation
            """.trimIndent(),
            "must not be abstract",
        )
    }

    @Test
    fun nestedClassIsRejected() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            class Outer {
                @GooseFragmentNavigation(HelpScreen::class)
                class HelpNavigation : FragmentScreenNavigation {
                    override fun navigate(request: FragmentNavigationRequest) { }
                }
            }
            """.trimIndent(),
            "must be top-level",
        )
    }

    @Test
    fun genericClassIsRejected() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            class HelpNavigation<T> : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
            "must not be generic",
        )
    }

    @Test
    fun privateConstructorIsRejected() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            class HelpNavigation private constructor() : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
            "the primary constructor must not be private",
        )
    }

    @Test
    fun interfaceIsRejected() {
        assertError(
            """
            package test
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            interface HelpNavigation : FragmentScreenNavigation
            """.trimIndent(),
            "must be a class or object",
        )
    }

    @Test
    fun generatedModuleKeysByScreenAndBindsTheInterface() {
        val result = compile(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GooseFragmentNavigation
            import dev.goose.runtime.Screen

            class HelpScreen : Screen

            @GooseFragmentNavigation(HelpScreen::class)
            class HelpNavigation : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = result.sourceFor("HelpNavigationGooseNavigationModule.kt")
        assertTrue(generated, generated.contains("@dev.zacsweers.metro.ClassKey(test.HelpScreen::class)"))
        assertTrue(generated, generated.contains("@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)"))
        assertTrue(generated, generated.contains(": dev.goose.fragment.FragmentScreenNavigation = test.HelpNavigation()"))
    }

    @Test
    fun presentationNavigationKeysByPresentationUnderTheQualifier() {
        val result = compile(
            """
            package test
            import dev.goose.fragment.FragmentNavigationRequest
            import dev.goose.fragment.FragmentScreenNavigation
            import dev.goose.fragment.GoosePresentationNavigation
            import dev.goose.runtime.Presentation

            object BottomSheet : Presentation

            @GoosePresentationNavigation(BottomSheet::class)
            class BottomSheetNavigation : FragmentScreenNavigation {
                override fun navigate(request: FragmentNavigationRequest) { }
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = result.sourceFor("BottomSheetNavigationGoosePresentationModule.kt")
        assertTrue(generated, generated.contains("@dev.zacsweers.metro.ClassKey(test.BottomSheet::class)"))
        assertTrue(generated, generated.contains("@dev.goose.fragment.PresentationNavigations"))
        assertTrue(
            generated,
            generated.contains(": dev.goose.fragment.FragmentScreenNavigation = test.BottomSheetNavigation()"),
        )
    }

    @Test
    fun presentationNavigationMustImplementTheInteropInterface() {
        assertError(
            """
            package test
            import dev.goose.fragment.GoosePresentationNavigation
            import dev.goose.runtime.Presentation

            object BottomSheet : Presentation

            @GoosePresentationNavigation(BottomSheet::class)
            class BottomSheetNavigation
            """.trimIndent(),
            "must implement FragmentScreenNavigation",
        )
    }

    private fun JvmCompilationResult.sourceFor(fileName: String): String =
        sourcesGeneratedBySymbolProcessor.first { it.name == fileName }.readText()
}
