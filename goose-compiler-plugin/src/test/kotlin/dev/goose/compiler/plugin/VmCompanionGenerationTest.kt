@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler.plugin

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated-factory contract: a concrete MavericksViewModel with no hand-written factory
 * gets a nested `GooseFactory` extending GeneratedGooseVmFactory (which Mavericks' reflective
 * nested-class scan finds), hand-written companions suppress generation, non-ViewModels are
 * untouched, and intermediate VM bases opt in via the extraViewModelBases plugin option.
 * Stubs stand in for Mavericks and runtime-mavericks; the plugin matches by name.
 */
class VmCompanionGenerationTest {

    private val mavericksStub = SourceFile.kotlin(
        "Mavericks.kt",
        """
        package com.airbnb.mvrx
        interface MavericksState
        abstract class MavericksViewModel<S : MavericksState>(initialState: S)
        class ViewModelContext
        interface MavericksViewModelFactory<VM : MavericksViewModel<S>, S : MavericksState> {
            fun create(viewModelContext: ViewModelContext, state: S): VM? = null
            fun initialState(viewModelContext: ViewModelContext): S? = null
        }
        """.trimIndent(),
    )
    private val gooseStub = SourceFile.kotlin(
        "GeneratedGooseVmFactory.kt",
        """
        package dev.goose.mavericks
        import com.airbnb.mvrx.MavericksState
        import com.airbnb.mvrx.MavericksViewModel
        import com.airbnb.mvrx.MavericksViewModelFactory
        import com.airbnb.mvrx.ViewModelContext
        abstract class GeneratedGooseVmFactory :
            MavericksViewModelFactory<MavericksViewModel<MavericksState>, MavericksState> {
            override fun create(
                viewModelContext: ViewModelContext,
                state: MavericksState,
            ): MavericksViewModel<MavericksState>? = null
        }
        """.trimIndent(),
    )

    private fun compile(source: String, extraBases: String? = null): JvmCompilationResult =
        KotlinCompilation().apply {
            sources = listOf(mavericksStub, gooseStub, SourceFile.kotlin("Test.kt", source))
            compilerPluginRegistrars = listOf(GoosePluginRegistrar())
            if (extraBases != null) {
                commandLineProcessors = listOf(GooseCommandLineProcessor())
                pluginOptions = listOf(
                    PluginOption("dev.goose.compiler-plugin", "extraViewModelBases", extraBases),
                )
            }
            inheritClassPath = true
            verbose = false
        }.compile()

    /** Instantiates the way Mavericks' `Class.instance()` does: ONE-param ctor, null arg. */
    private fun JvmCompilationResult.gooseFactoryOf(fqName: String): Any? =
        classLoader.loadClass(fqName).declaredClasses
            .firstOrNull { it.simpleName == "GooseFactory" }
            ?.declaredConstructors
            ?.first { it.parameterTypes.size == 1 }
            ?.newInstance(null)

    @Test
    fun viewModelWithoutFactoryGetsGeneratedNestedFactory() {
        val result = compile(
            """
            package test
            import com.airbnb.mvrx.MavericksState
            import com.airbnb.mvrx.MavericksViewModel

            data class ProfileState(val id: String = "") : MavericksState
            class ProfileViewModel(initialState: ProfileState) :
                MavericksViewModel<ProfileState>(initialState)
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val factory = checkNotNull(result.gooseFactoryOf("test.ProfileViewModel")) {
            "no GooseFactory generated"
        }
        assertEquals("dev.goose.mavericks.GeneratedGooseVmFactory", factory.javaClass.superclass.name)
        // Mavericks' discovery contract: a nested class assignable to MavericksViewModelFactory.
        val factoryInterface = result.classLoader.loadClass("com.airbnb.mvrx.MavericksViewModelFactory")
        assertTrue(factoryInterface.isAssignableFrom(factory.javaClass))
        // Behavior via the stub superclass: create is inherited and callable.
        val ctx = result.classLoader.loadClass("com.airbnb.mvrx.ViewModelContext")
            .getDeclaredConstructor().newInstance()
        val state = result.classLoader.loadClass("test.ProfileState")
            .getDeclaredConstructor(String::class.java).newInstance("x")
        val create = factory.javaClass.methods.first { it.name == "create" }
        assertNull(create.invoke(factory, ctx, state))
    }

    @Test
    fun handWrittenCompanionSuppressesGeneration() {
        val result = compile(
            """
            package test
            import com.airbnb.mvrx.MavericksState
            import com.airbnb.mvrx.MavericksViewModel

            data class HomeState(val id: String = "") : MavericksState
            class HomeViewModel(initialState: HomeState) :
                MavericksViewModel<HomeState>(initialState) {
                companion object {
                    const val MARKER = "hand-written"
                }
            }
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertNull(result.gooseFactoryOf("test.HomeViewModel"))
    }

    @Test
    fun nonViewModelsAndAbstractViewModelsGetNoFactory() {
        val result = compile(
            """
            package test
            import com.airbnb.mvrx.MavericksState
            import com.airbnb.mvrx.MavericksViewModel

            data class SomeState(val id: String = "") : MavericksState
            class NotAViewModel
            abstract class BaseViewModel(initialState: SomeState) :
                MavericksViewModel<SomeState>(initialState)
            """.trimIndent(),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertNull(result.gooseFactoryOf("test.NotAViewModel"))
        assertNull(result.gooseFactoryOf("test.BaseViewModel"))
    }

    @Test
    fun intermediateBaseNeedsTheExtraBasesOption() {
        val source = """
            package test
            import com.airbnb.mvrx.MavericksState
            import com.airbnb.mvrx.MavericksViewModel

            data class DeepState(val id: String = "") : MavericksState
            abstract class AppViewModel<S : MavericksState>(initialState: S) :
                MavericksViewModel<S>(initialState)
            class DeepViewModel(initialState: DeepState) : AppViewModel<DeepState>(initialState)
        """.trimIndent()

        // Detection is syntactic, so the intermediate base is invisible by default...
        val without = compile(source)
        assertEquals(without.messages, KotlinCompilation.ExitCode.OK, without.exitCode)
        assertNull(without.gooseFactoryOf("test.DeepViewModel"))

        // ...and the plugin option names it.
        val with = compile(source, extraBases = "AppViewModel")
        assertEquals(with.messages, KotlinCompilation.ExitCode.OK, with.exitCode)
        val factory = checkNotNull(with.gooseFactoryOf("test.DeepViewModel"))
        assertEquals("dev.goose.mavericks.GeneratedGooseVmFactory", factory.javaClass.superclass.name)
    }
}
