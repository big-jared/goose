@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The @GooseUi grammar, enforced: every rejected shape produces a compile error naming the rule,
 * and the happy path generates a registration that itself compiles. Stub declarations stand in
 * for compose/metro/goose types; the processor matches by qualified name only.
 */
class GooseUiProcessorTest {

    private val stubs = SourceFile.kotlin(
        "Stubs.kt",
        """
        package dev.goose.runtime
        import kotlin.reflect.KClass
        annotation class GooseUi(val screen: KClass<out Screen>, val scope: KClass<*> = Unit::class)
        interface Screen
        interface Navigator
        fun interface ScreenEntry {
            fun Content(screen: Screen, modifier: androidx.compose.ui.Modifier)
        }
        """.trimIndent(),
    )
    private val composeStub = SourceFile.kotlin(
        "ComposeStub.kt",
        """
        package androidx.compose.runtime
        annotation class Composable
        """.trimIndent(),
    )
    private val modifierStub = SourceFile.kotlin(
        "ModifierStub.kt",
        """
        package androidx.compose.ui
        class Modifier
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
        annotation class AssistedFactory
        annotation class Qualifier
        abstract class AppScope
        """.trimIndent(),
    )
    private val mavericksStub = SourceFile.kotlin(
        "MavericksStub.kt",
        """
        package com.airbnb.mvrx
        abstract class MavericksViewModel<S>(initialState: S)
        """.trimIndent(),
    )
    private val daggerStub = SourceFile.kotlin(
        "DaggerStub.kt",
        """
        package dagger.assisted
        annotation class AssistedFactory
        """.trimIndent(),
    )
    private val gooseVmStub = SourceFile.kotlin(
        "GooseVmStub.kt",
        """
        package dev.goose.mavericks
        import dev.goose.runtime.Navigator
        import dev.goose.runtime.Screen
        fun <VM, S> screenViewModel(
            screen: Screen,
            vmClass: Class<VM>,
            stateClass: Class<S>,
            create: (S, Navigator) -> VM,
        ): VM = throw NotImplementedError()
        """.trimIndent(),
    )

    private fun compile(source: String): JvmCompilationResult {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                stubs, composeStub, modifierStub, serializationStub, metroStub, mavericksStub,
                daggerStub, gooseVmStub,
                SourceFile.kotlin("Test.kt", source),
            )
            configureKsp {
                symbolProcessorProviders += GooseUiProcessorProvider()
            }
            inheritClassPath = true
            messageOutputStream = java.io.OutputStream.nullOutputStream()
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
    fun validFunctionCompilesAndRegisters() {
        val result = compile(
            """
            package test
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import dev.goose.runtime.GooseUi
            import dev.goose.runtime.Screen
            import kotlinx.serialization.Serializable

            @Serializable class HomeScreen : Screen

            @GooseUi(HomeScreen::class)
            @Composable
            fun HomeUi(screen: HomeScreen, modifier: Modifier) { }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun scopedRegistrationContributesToTheGivenScope() {
        val result = compile(
            """
            package test
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import dev.goose.runtime.GooseUi
            import dev.goose.runtime.Screen
            import kotlinx.serialization.Serializable

            abstract class SessionScope
            @Serializable class GiftScreen : Screen

            @GooseUi(GiftScreen::class, scope = SessionScope::class)
            @Composable
            fun GiftUi(screen: GiftScreen, modifier: Modifier) { }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = result.sourcesGeneratedBySymbolProcessor
            .first { it.name == "GiftUiGooseModule.kt" }.readText()
        assertTrue(generated, "@dev.zacsweers.metro.ContributesTo(test.SessionScope::class)" in generated)
    }

    @Test
    fun screenMustBeSerializable() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen

        class HomeScreen : Screen

        @GooseUi(HomeScreen::class)
        @Composable
        fun HomeUi(screen: HomeScreen) { }
        """.trimIndent(),
        "must be @Serializable",
    )

    @Test
    fun functionMustBeComposable() = assertError(
        """
        package test
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen

        @GooseUi(HomeScreen::class)
        fun HomeUi(screen: HomeScreen) { }
        """.trimIndent(),
        "must be @Composable",
    )

    @Test
    fun functionMustNotBePrivate() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen

        @GooseUi(HomeScreen::class)
        @Composable
        private fun HomeUi(screen: HomeScreen) { }
        """.trimIndent(),
        "must not be private",
    )

    @Test
    fun functionMustBeTopLevel() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen

        class Holder {
            @GooseUi(HomeScreen::class)
            @Composable
            fun HomeUi(screen: HomeScreen) { }
        }
        """.trimIndent(),
        "must be a top-level function",
    )

    @Test
    fun functionMustNotBeExtension() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen

        @GooseUi(HomeScreen::class)
        @Composable
        fun String.HomeUi(screen: HomeScreen) { }
        """.trimIndent(),
        "must not be an extension function",
    )

    @Test
    fun functionMustNotBeGeneric() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen

        @GooseUi(HomeScreen::class)
        @Composable
        fun <T> HomeUi(screen: HomeScreen, unused: T) { }
        """.trimIndent(),
        "must not be generic",
    )

    @Test
    fun sameNameFunctionsInOnePackageCollide() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen
        @Serializable class OtherScreen : Screen

        @GooseUi(HomeScreen::class)
        @Composable
        fun Content(screen: HomeScreen) { }

        @GooseUi(OtherScreen::class)
        @Composable
        fun Content(screen: OtherScreen) { }
        """.trimIndent(),
        "two annotated functions named",
    )

    @Test
    fun reservedParameterNamesAreRejected() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen

        @GooseUi(HomeScreen::class)
        @Composable
        fun HomeUi(screen: HomeScreen, gooseScreen: String) { }
        """.trimIndent(),
        "reserved for generated code",
    )

    @Test
    fun viewModelWithoutAssistedFactoryIsRejected() = assertError(
        """
        package test
        import androidx.compose.runtime.Composable
        import com.airbnb.mvrx.MavericksViewModel
        import dev.goose.runtime.GooseUi
        import dev.goose.runtime.Screen
        import kotlinx.serialization.Serializable

        @Serializable class HomeScreen : Screen
        class HomeState
        class HomeViewModel(initialState: HomeState) : MavericksViewModel<HomeState>(initialState)

        @GooseUi(HomeScreen::class)
        @Composable
        fun HomeUi(screen: HomeScreen, vm: HomeViewModel) { }
        """.trimIndent(),
        "no nested @AssistedFactory",
    )

    /** A Dagger/Anvil app's existing assisted factory is accepted as-is — no Metro migration. */
    @Test
    fun daggerAssistedFactoryIsAccepted() {
        val result = compile(
            """
            package test
            import androidx.compose.runtime.Composable
            import com.airbnb.mvrx.MavericksViewModel
            import dev.goose.runtime.GooseUi
            import dev.goose.runtime.Navigator
            import dev.goose.runtime.Screen
            import kotlinx.serialization.Serializable

            @Serializable class HomeScreen : Screen
            class HomeState
            class HomeViewModel(initialState: HomeState, navigator: Navigator) :
                MavericksViewModel<HomeState>(initialState) {
                @dagger.assisted.AssistedFactory
                interface Factory {
                    fun create(initialState: HomeState, navigator: Navigator): HomeViewModel
                }
            }

            @GooseUi(HomeScreen::class)
            @Composable
            fun HomeUi(screen: HomeScreen, vm: HomeViewModel) { }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
