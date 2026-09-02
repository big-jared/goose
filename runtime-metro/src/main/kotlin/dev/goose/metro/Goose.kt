package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenUi
import dev.goose.runtime.screenUi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.providerOf
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializerOrNull

/**
 * Everything goose hosts need, assembled once at startup — the analogue of Circuit's `Circuit`.
 *
 * There is exactly one way to build it, [Builder], and three ways to call the builder:
 * - Metro: goose's own [DefaultGooseModule] builds it from the contributed screen entries and
 *   serializers, so most apps write nothing. Apps with app-level configuration contribute a
 *   replacement provider (`@ContributesTo(AppScope::class, replaces = [DefaultGooseModule::class])`).
 * - Other DI (Dagger, Hilt, kotlin-inject): a provider in YOUR graph calls the builder with
 *   your own multibindings.
 * - No DI: call the builder in `Application.onCreate` and hand the result wherever a goose
 *   graph goes ([GooseGraphHolder], [GooseCompositionLocals]).
 */
class Goose private constructor(
    val screenRegistry: ScreenRegistry,
    val resultRouter: ResultRouter,
    val navSerializersModule: SerializersModule,
) {

    class Builder {
        private val entries = mutableMapOf<KClass<*>, Provider<ScreenEntry>>()
        private val bulkEntries = mutableMapOf<KClass<*>, Provider<ScreenEntry>>()
        private val serializers = mutableSetOf<SerializersModule>()

        /** Registers [entry] as the renderer for [screen]. Last registration per class wins. */
        fun addScreen(screen: KClass<out Screen>, entry: ScreenEntry): Builder = apply {
            entries[screen] = providerOf(entry)
        }

        /** Typed sugar over [addScreen] — the builder analogue of a `screenUi` registration. */
        inline fun <reified S : Screen> addUi(
            crossinline content: @Composable (screen: S, modifier: Modifier) -> Unit,
        ): Builder = addScreen(S::class, screenUi<S> { screen, modifier -> content(screen, modifier) })

        /** Typed sugar for a hand-written [ScreenUi] instance. */
        fun <S : Screen> addUi(screen: KClass<S>, ui: ScreenUi<S>): Builder = addScreen(screen, ui)

        /**
         * Bulk form for DI multibindings. Explicit [addScreen]/[addUi] registrations win over
         * bulk entries for the same screen class, regardless of call order.
         */
        fun addScreens(entries: Map<KClass<*>, Provider<ScreenEntry>>): Builder = apply {
            bulkEntries += entries
        }

        /** Explicit serializers, for custom @SerialName or heavy minification; reflective lookup covers the rest. */
        fun addSerializers(module: SerializersModule): Builder = apply { serializers += module }

        fun addSerializers(modules: Set<SerializersModule>): Builder = apply { serializers += modules }

        fun build(): Goose = Goose(
            screenRegistry = ScreenRegistry(bulkEntries + entries),
            resultRouter = ResultRouter(),
            navSerializersModule = buildNavSerializersModule(serializers),
        )
    }
}

/**
 * The [Goose] behind whatever the app hands hosts as its graph: the object itself (no-DI apps),
 * or a Metro graph exposing it through [GooseRuntimeAccessors].
 */
fun Any.asGoose(): Goose = when (this) {
    is Goose -> this
    is GooseRuntimeAccessors -> goose
    else -> error(
        "${this::class.qualifiedName} is neither a Goose nor a graph implementing " +
            "GooseRuntimeAccessors. Pass the Goose you built, or a merged AppScope graph.",
    )
}

/** Resolved once at the [GooseCompositionLocals] boundary; null outside a goose host. */
internal val LocalGoose = staticCompositionLocalOf<Goose?> { null }

/** The nearest [Goose]: the boundary-resolved one, or derived from [LocalGooseGraph]. */
@Composable
fun goose(): Goose = LocalGoose.current ?: LocalGooseGraph.current.asGoose()

/**
 * The combined serializers module for back-stack persistence. Screens serialize reflectively by
 * their own `@Serializable` serializer and class name by default; explicitly added modules take
 * precedence. An unknown class on restore decodes to null so hosts restart fresh instead of
 * crashing (a renamed screen after an app update, an unloaded dynamic feature).
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
internal fun buildNavSerializersModule(modules: Set<SerializersModule>): SerializersModule =
    SerializersModule {
        modules.forEach { include(it) }
        polymorphicDefaultSerializer(NavKey::class) { value ->
            @Suppress("UNCHECKED_CAST")
            value::class.serializerOrNull() as SerializationStrategy<NavKey>?
        }
        polymorphicDefaultSerializer(Screen::class) { value ->
            @Suppress("UNCHECKED_CAST")
            value::class.serializerOrNull() as SerializationStrategy<Screen>?
        }
        polymorphicDefaultDeserializer(Screen::class) { className ->
            className?.let {
                @Suppress("UNCHECKED_CAST")
                classForSerialName(it)?.kotlin?.serializerOrNull()
                    as DeserializationStrategy<Screen>?
            }
        }
        polymorphicDefaultDeserializer(NavKey::class) { className ->
            className?.let {
                @Suppress("UNCHECKED_CAST")
                classForSerialName(it)?.kotlin?.serializerOrNull()
                    as DeserializationStrategy<NavKey>?
            }
        }
    }

/**
 * Resolves a kotlinx serial-name discriminator to a JVM class. Default serial names are
 * dot-separated, so nested classes need progressive dollar substitution toward the binary name.
 */
private fun classForSerialName(name: String): Class<*>? {
    var candidate = name
    while (true) {
        try {
            return Class.forName(candidate)
        } catch (_: ClassNotFoundException) {
        } catch (_: LinkageError) {
            // A class that exists but cannot load (a dynamic-feature split's dangling
            // reference, a broken static init) is as gone as a missing one for restoration;
            // keep walking toward the binary name — a later candidate may still load.
        }
        val lastDot = candidate.lastIndexOf('.')
        if (lastDot < 0) return null
        candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1)
    }
}

