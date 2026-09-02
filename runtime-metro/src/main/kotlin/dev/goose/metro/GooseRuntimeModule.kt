package dev.goose.metro

import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass
import kotlinx.serialization.modules.SerializersModule

/**
 * What a graph exposes to goose hosts: the assembled [Goose]. Metro graphs implement it by
 * merging AppScope; hand-built holders (fragment environments, test doubles) implement the one
 * property directly.
 */
@ContributesTo(AppScope::class)
interface GooseRuntimeAccessors {
    val goose: Goose
}

/**
 * Metro-only multibinding declarations the feature contributions land in. Deliberately
 * separate from [GooseRuntimeAccessors] (so hand-built graphs never stub them) and from
 * [DefaultGooseModule] (so a replacement provider can still inject the collected sets).
 */
@ContributesTo(AppScope::class)
interface GooseMultibindings {
    @Multibinds(allowEmpty = true)
    val screenEntries: Map<KClass<*>, ScreenEntry>

    @Multibinds(allowEmpty = true)
    val serializersModules: Set<SerializersModule>
}

/**
 * The default [Goose] assembly: everything the features contributed, no app-level extras. An
 * app that wants to configure the builder replaces this with its own provider:
 * ```
 * @ContributesTo(AppScope::class, replaces = [DefaultGooseModule::class])
 * interface GooseModule {
 *     companion object {
 *         @Provides @SingleIn(AppScope::class)
 *         fun provideGoose(
 *             screenEntries: Map<KClass<*>, Provider<ScreenEntry>>,
 *             serializersModules: Set<SerializersModule>,
 *         ): Goose = Goose.Builder()
 *             .addScreens(screenEntries)
 *             .addSerializers(serializersModules)
 *             // app-level configuration goes here
 *             .build()
 *     }
 * }
 * ```
 * KEEP `@SingleIn(AppScope::class)` on the replacement. An unscoped provider builds a fresh
 * Goose per injection point, and split ResultRouters mean awaited results never resolve.
 */
@ContributesTo(AppScope::class)
interface DefaultGooseModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideGoose(
            screenEntries: Map<KClass<*>, Provider<ScreenEntry>>,
            serializersModules: Set<SerializersModule>,
        ): Goose = Goose.Builder()
            .addScreens(screenEntries)
            .addSerializers(serializersModules)
            .build()
    }
}
