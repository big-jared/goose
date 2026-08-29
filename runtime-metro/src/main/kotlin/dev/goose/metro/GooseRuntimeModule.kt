package dev.goose.metro

import dev.goose.runtime.ResultRouter
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass
import kotlinx.serialization.modules.SerializersModule

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
         * The combined serializers module for back-stack persistence. Feature modules contribute
         * their screens with `@Provides @IntoSet fun serializers(): SerializersModule =
         * SerializersModule { polymorphic(NavKey::class) { subclass(MyScreen::class) } }`.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideNavSerializersModule(modules: Set<SerializersModule>): SerializersModule =
            SerializersModule { modules.forEach { include(it) } }
    }
}
