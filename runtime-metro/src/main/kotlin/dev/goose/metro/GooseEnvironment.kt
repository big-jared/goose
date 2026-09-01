package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.goose.runtime.GooseDecoration
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenUi
import dev.goose.runtime.screenUi
import dev.zacsweers.metro.providerOf
import kotlin.reflect.KClass
import kotlinx.serialization.modules.SerializersModule

/**
 * Hand-assembled goose wiring — the Circuit-`Builder`-style alternative to a Metro graph.
 *
 * On Metro, this whole object is assembled BY CONTRIBUTION: features contribute entries and
 * the app graph satisfies [GooseRuntimeAccessors] automatically. Apps on other DI (Dagger,
 * Hilt, kotlin-inject) or none build one explicitly and use it wherever a goose graph goes:
 *
 * ```
 * val environment = GooseEnvironment.Builder()
 *     .addUi<ProfileScreen> { screen, modifier -> ProfileUi(screen, modifier) }
 *     .addUi<FollowersScreen> { screen, modifier -> FollowersUi(screen, modifier) }
 *     .addDecoration { content -> AppTheme { content() } }
 *     .build()
 *
 * // pure Compose:
 * GooseCompositionLocals(environment) { NavigableGooseContent(stack) }
 *
 * // or as the Application-held graph while fragments still host screens:
 * class MyApp : Application(), GooseGraphHolder {
 *     override val gooseGraph: Any = environment
 * }
 * ```
 *
 * Entry lambdas close over whatever your own DI provides (a Dagger component's factories, a
 * service locator, plain constructors) — goose doesn't care how a screen's dependencies are
 * made, only how the screen renders. `@GooseUi` codegen and session child scopes (GooseScope
 * graph extensions) remain Metro features; everything else, fragment hosting included, works
 * against a built environment.
 */
class GooseEnvironment private constructor(
    override val screenEntries: Map<KClass<*>, ScreenEntry>,
    override val serializersModules: Set<SerializersModule>,
    override val gooseDecorations: Set<GooseDecoration>,
) : GooseRuntimeAccessors {

    override val resultRouter: ResultRouter = ResultRouter()

    override val screenRegistry: ScreenRegistry = ScreenRegistry(
        screenEntries.mapValues { (_, entry) -> providerOf(entry) },
    )

    /** Screens still serialize reflectively by default; added modules take precedence. */
    override val navSerializersModule: SerializersModule =
        GooseRuntimeAccessors.provideNavSerializersModule(serializersModules)

    class Builder {
        private val entries = mutableMapOf<KClass<*>, ScreenEntry>()
        private val serializers = mutableSetOf<SerializersModule>()
        private val decorations = mutableSetOf<GooseDecoration>()

        /** Registers [entry] as the renderer for [screen]. Last registration per class wins. */
        fun addEntry(screen: KClass<out Screen>, entry: ScreenEntry): Builder = apply {
            entries[screen] = entry
        }

        /** Typed sugar over [addEntry] — the builder analogue of a `screenUi` registration. */
        inline fun <reified S : Screen> addUi(
            crossinline content: @Composable (screen: S, modifier: Modifier) -> Unit,
        ): Builder = addEntry(S::class, screenUi<S> { screen, modifier -> content(screen, modifier) })

        /** Typed sugar for a hand-written [ScreenUi] instance. */
        fun <S : Screen> addUi(screen: KClass<S>, ui: ScreenUi<S>): Builder = addEntry(screen, ui)

        /** Explicit serializers for screens whose reflective lookup won't do (custom @SerialName, heavy minification). */
        fun addSerializers(module: SerializersModule): Builder = apply { serializers += module }

        /** App theme / CompositionLocal wrapper applied by goose-rooted hosts (fragment-hosted screens). */
        fun addDecoration(decoration: GooseDecoration): Builder = apply { decorations += decoration }

        fun build(): GooseEnvironment =
            GooseEnvironment(entries.toMap(), serializers.toSet(), decorations.toSet())
    }
}
