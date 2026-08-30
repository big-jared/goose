package dev.goose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The erased, registry-facing unit a feature contributes per screen — how to render it (and,
 * inside, how to obtain its presenter). Contributed into a map keyed by the screen's class via
 * Metro's `@ContributesIntoMap` + `@ClassKey`.
 *
 * Implement [ScreenUi] rather than this interface: it restores the screen's type. The lambda form
 * (`ScreenEntry { screen, modifier -> ... }`) suits tests and ad-hoc hosts. [UntypedContent] is
 * the library's dispatch surface; feature code overrides [ScreenUi.Content].
 */
fun interface ScreenEntry {
    @Composable
    fun UntypedContent(screen: Screen, modifier: Modifier)
}

/**
 * A typed [ScreenEntry] — the unit features actually write, Circuit-`Ui`-shaped. The registry
 * only ever routes an entry the screen type its `@ClassKey` names, so the downcast is the
 * library's single point of trust and feature code stays fully typed:
 * ```
 * @ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
 * @ClassKey(ProfileScreen::class)
 * @Inject
 * class ProfileUi(private val vmFactory: ProfileViewModel.Factory) : ScreenUi<ProfileScreen>() {
 *   @Composable override fun Content(screen: ProfileScreen, modifier: Modifier) {
 *     val vm = screenViewModel<ProfileViewModel, ProfileState>(screen) { state, navigator ->
 *       vmFactory.create(state, navigator)
 *     }
 *     ...
 *   }
 * }
 * ```
 * Note the explicit `binding` — Metro binds a contribution as its DIRECT supertype by default,
 * which here would be `ScreenUi<S>`, not the `ScreenEntry` the registry collects.
 */
abstract class ScreenUi<S : Screen> : ScreenEntry {
    @Composable
    final override fun UntypedContent(screen: Screen, modifier: Modifier) {
        @Suppress("UNCHECKED_CAST")
        Content(screen as S, modifier)
    }

    @Composable
    protected abstract fun Content(screen: S, modifier: Modifier)
}

/**
 * The navigator owning the stack this screen sits on. Set by hosts (NavigableGooseContent,
 * ScreenFragment); read by presenter helpers so screens never plumb navigators manually.
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided. Screens must be hosted by a Goose host (NavigableGooseContent or ScreenFragment).")
}

/**
 * Function form of a screen UI, for the @Provides registration style: the class, @Inject, and
 * binding parameter all disappear because the provider's return type IS the binding:
 * ```
 * @Provides @IntoMap @ClassKey(ProfileScreen::class)
 * fun profileUi(vmFactory: ProfileViewModel.Factory): ScreenEntry =
 *     screenUi<ProfileScreen> { screen, modifier -> ... }
 * ```
 */
inline fun <reified S : Screen> screenUi(
    crossinline content: @Composable (screen: S, modifier: Modifier) -> Unit,
): ScreenEntry = ScreenEntry { screen, modifier ->
    @Suppress("UNCHECKED_CAST")
    content(screen as S, modifier)
}
