package dev.goose.metro

import dev.goose.runtime.ResultRouter
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializerOrNull

/**
 * Wiring every Goose app graph inherits by merging AppScope. Also the accessor surface hosts use:
 * `gooseGraph<GooseRuntimeAccessors>().screenRegistry`.
 */
@ContributesTo(AppScope::class)
interface GooseRuntimeAccessors {
    val screenRegistry: ScreenRegistry
    val resultRouter: ResultRouter
    val navSerializersModule: SerializersModule

    @Multibinds(allowEmpty = true)
    val screenEntries: Map<KClass<*>, ScreenEntry>

    @Multibinds(allowEmpty = true)
    val serializersModules: Set<SerializersModule>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideResultRouter(): ResultRouter = ResultRouter()

        /** The root of the registry chain; child scopes chain onto it via GooseScope. */
        @Provides
        @SingleIn(AppScope::class)
        fun provideScreenRegistry(entries: Map<KClass<*>, Provider<ScreenEntry>>): ScreenRegistry =
            ScreenRegistry(entries)

        /**
         * The combined serializers module for back-stack persistence.
         *
         * By default screens are serialized reflectively by their own `@Serializable` serializer
         * and class name, so features need no registration at all. Explicit registration via
         * `screenSerializers { subclass(MyScreen::class) }` (contributed `@IntoSet`) still works,
         * takes precedence, and avoids the reflective lookup on aggressively minified builds.
         */
        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        @Provides
        @SingleIn(AppScope::class)
        fun provideNavSerializersModule(modules: Set<SerializersModule>): SerializersModule =
            SerializersModule {
                modules.forEach { include(it) }
                polymorphicDefaultSerializer(NavKey::class) { value ->
                    @Suppress("UNCHECKED_CAST")
                    value::class.serializerOrNull() as SerializationStrategy<NavKey>?
                }
                // Screens also serialize as a FIELD of the per-push record, whose base type is
                // Screen; same reflective defaults under that base.
                polymorphicDefaultSerializer(dev.goose.runtime.Screen::class) { value ->
                    @Suppress("UNCHECKED_CAST")
                    value::class.serializerOrNull() as SerializationStrategy<dev.goose.runtime.Screen>?
                }
                polymorphicDefaultDeserializer(dev.goose.runtime.Screen::class) { className ->
                    className?.let {
                        @Suppress("UNCHECKED_CAST")
                        classForSerialName(it)?.kotlin?.serializerOrNull()
                            as DeserializationStrategy<dev.goose.runtime.Screen>?
                    }
                }
                polymorphicDefaultDeserializer(NavKey::class) { className ->
                    // An unknown class (renamed/removed in an app update, unloaded dynamic
                    // feature, or a custom @SerialName without explicit registration) returns
                    // null; the decode then fails as a SerializationException, which the
                    // resilient stack saver treats as "restart fresh" instead of crashing.
                    className?.let {
                        @Suppress("UNCHECKED_CAST")
                        classForSerialName(it)?.kotlin?.serializerOrNull()
                            as DeserializationStrategy<NavKey>?
                    }
                }
            }
    }
}

/**
 * Resolves a kotlinx serial-name discriminator to a JVM class. The default serial name is the
 * dot-separated Kotlin name, so nested classes ("a.b.Outer.Inner") need progressive dollar
 * substitution toward the JVM binary name before Class.forName finds them. Returns null when no
 * candidate resolves.
 */
private fun classForSerialName(name: String): Class<*>? {
    var candidate = name
    while (true) {
        try {
            return Class.forName(candidate)
        } catch (_: ClassNotFoundException) {
        } catch (_: LinkageError) {
            // A class that exists but cannot load (a dynamic-feature split's dangling
            // reference, a broken static init) is as gone as a missing one for restoration.
        }
        val lastDot = candidate.lastIndexOf('.')
        if (lastDot < 0) return null
        candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1)
    }
}
