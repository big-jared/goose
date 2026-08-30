package dev.goose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The single unit a feature contributes per screen: how to render it (and, inside, how to obtain
 * its presenter). Contributed into a map keyed by the screen's class via Metro's
 * `@ContributesIntoMap` + `@ClassKey`.
 */
fun interface ScreenEntry {
    @Composable
    fun Content(screen: Screen, modifier: Modifier)
}

/**
 * [ScreenEntry] with the downcast absorbed: the registry only ever routes an entry the screen
 * type its `@ClassKey` names, so the cast is the library's single point of trust and feature
 * code stays fully typed. Prefer this for screens that carry arguments:
 * ```
 * @ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
 * @ClassKey(ProfileScreen::class)
 * @Inject
 * class ProfileEntry : TypedScreenEntry<ProfileScreen>() {
 *   @Composable override fun ScreenContent(screen: ProfileScreen, modifier: Modifier) { ... }
 * }
 * ```
 * Note the explicit `binding` — Metro binds a contribution as its DIRECT supertype by default,
 * which for a typed entry would be `TypedScreenEntry<S>`, not the `ScreenEntry` the registry
 * collects.
 * The plain fun-interface form remains for argument-less `data object` screens.
 */
abstract class TypedScreenEntry<S : Screen> : ScreenEntry {
    @Composable
    final override fun Content(screen: Screen, modifier: Modifier) {
        @Suppress("UNCHECKED_CAST")
        ScreenContent(screen as S, modifier)
    }

    @Composable
    protected abstract fun ScreenContent(screen: S, modifier: Modifier)
}

/**
 * The navigator owning the stack this screen sits on. Set by hosts ([ScreenNavDisplay],
 * ScreenFragment); read by presenter helpers so screens never plumb navigators manually.
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided. Screens must be hosted by a Goose host (ScreenNavDisplay or ScreenFragment).")
}
