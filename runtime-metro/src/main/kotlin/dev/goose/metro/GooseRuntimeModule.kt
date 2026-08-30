package dev.goose.metro

import dev.goose.runtime.ResultRouter
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
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
                polymorphicDefaultDeserializer(NavKey::class) { className ->
                    className?.let {
                        @Suppress("UNCHECKED_CAST")
                        Class.forName(it).kotlin.serializerOrNull() as DeserializationStrategy<NavKey>?
                    }
                }
            }
    }
}
