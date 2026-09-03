@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler.plugin

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.lang.reflect.Modifier
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The readResolve contract, end to end: object Screens deserialize to the SAME instance, the
 * frontend warning is gone, hand-written readResolve is left alone, and non-Screens (including
 * Serializable objects outside goose) are untouched. A control compile without the plugin pins
 * that the identity break genuinely occurs in this harness, so the "same instance" assertions
 * can't pass vacuously.
 */
class ReadResolveGenerationTest {

    private val screenStub = SourceFile.kotlin(
        "Screen.kt",
        """
        package dev.goose.runtime
        interface Screen : java.io.Serializable
        """.trimIndent(),
    )

    private fun compile(vararg sources: SourceFile, withPlugin: Boolean = true): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            if (withPlugin) compilerPluginRegistrars = listOf(GoosePluginRegistrar())
            inheritClassPath = true
            verbose = false
        }.compile()

    private fun roundTrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(value) }
        val loader = value.javaClass.classLoader
        return object : ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())) {
            override fun resolveClass(desc: ObjectStreamClass): Class<*> =
                Class.forName(desc.name, false, loader)
        }.readObject()
    }

    private fun JvmCompilationResult.instanceOf(fqName: String): Any =
        classLoader.loadClass(fqName).getField("INSTANCE").get(null)

    /**
     * Sanity control: the harness does surface compiler warnings (deprecation proves it), and
     * without the plugin a serializable object deserializes to a DIFFERENT instance. As of
     * Kotlin 2.4 the "must implement 'readResolve'" text lives in the IDE inspection, not the
     * CLI compiler, so the identity break is the compiler-visible symptom to pin here.
     */
    @Test
    fun `control - without the plugin, deserialization breaks singleton identity`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Foo.kt",
                """
                package test
                @Deprecated("old")
                fun old() = Unit
                fun caller() = old()
                data object FooScreen : dev.goose.runtime.Screen
                """.trimIndent(),
            ),
            withPlugin = false,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue("warnings should be captured:\n" + result.messages, result.messages.contains("old"))
        val instance = result.instanceOf("test.FooScreen")
        assertNotSame(instance, roundTrip(instance))
    }

    @Test
    fun `serializable object gets readResolve - no warning, deserializes to the same instance`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Foo.kt",
                """
                package test
                data object FooScreen : dev.goose.runtime.Screen
                """.trimIndent(),
            ),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertFalse(result.messages, result.messages.contains("readResolve"))
        val instance = result.instanceOf("test.FooScreen")
        assertSame(instance, roundTrip(instance))
    }

    @Test
    fun `serializable found through a deep interface chain`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Deep.kt",
                """
                package test
                interface PopResult
                interface ScreenWithResult<R : PopResult> : dev.goose.runtime.Screen
                data class Picked(val x: Int) : PopResult
                data object PickerScreen : ScreenWithResult<Picked>
                """.trimIndent(),
            ),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertFalse(result.messages, result.messages.contains("readResolve"))
        val instance = result.instanceOf("test.PickerScreen")
        assertSame(instance, roundTrip(instance))
    }

    @Test
    fun `hand-written readResolve is left alone`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Own.kt",
                """
                package test
                data object OwnScreen : dev.goose.runtime.Screen {
                    private fun readResolve(): Any = OwnScreen
                }
                """.trimIndent(),
            ),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val instance = result.instanceOf("test.OwnScreen")
        assertSame(instance, roundTrip(instance))
    }

    @Test
    fun `object with a body keeps its members`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Body.kt",
                """
                package test
                data object BodyScreen : dev.goose.runtime.Screen {
                    fun greeting(): String = "hi"
                }
                """.trimIndent(),
            ),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertFalse(result.messages, result.messages.contains("readResolve"))
        val instance = result.instanceOf("test.BodyScreen")
        assertSame(instance, roundTrip(instance))
        val greeting = instance.javaClass.getMethod("greeting").invoke(instance)
        assertEquals("hi", greeting)
    }

    @Test
    fun `non-Screen objects and data classes are untouched`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Others.kt",
                """
                package test
                data object PlainObject
                // Serializable but not a Screen: none of goose's business, and the runtime's
                // Screen-scoped keep rule wouldn't preserve a generated readResolve through R8.
                data object SerializableUtil : java.io.Serializable
                data class DataScreen(val id: String) : dev.goose.runtime.Screen
                """.trimIndent(),
            ),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertFalse(
            "PlainObject must not get readResolve",
            result.classLoader.loadClass("test.PlainObject").declaredMethods.any { it.name == "readResolve" },
        )
        assertFalse(
            "SerializableUtil must not get readResolve (plugin is scoped to Screen implementors)",
            result.classLoader.loadClass("test.SerializableUtil").declaredMethods.any { it.name == "readResolve" },
        )
        assertFalse(
            "DataScreen must not get readResolve",
            result.classLoader.loadClass("test.DataScreen").declaredMethods.any { it.name == "readResolve" },
        )
        val dataScreen = result.classLoader.loadClass("test.DataScreen")
            .getConstructor(String::class.java).newInstance("a")
        val copy = roundTrip(dataScreen)
        assertNotSame(dataScreen, copy)
        assertEquals(dataScreen, copy)
    }

    @Test
    fun `generated readResolve is private`() {
        val result = compile(
            screenStub,
            SourceFile.kotlin(
                "Foo.kt",
                """
                package test
                data object FooScreen : dev.goose.runtime.Screen
                """.trimIndent(),
            ),
        )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val method = result.classLoader.loadClass("test.FooScreen")
            .declaredMethods.single { it.name == "readResolve" }
        assertTrue("readResolve should be private", Modifier.isPrivate(method.modifiers))
    }
}
