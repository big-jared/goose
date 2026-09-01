package dev.goose.runtime

import androidx.compose.runtime.Composable

/**
 * Composition an app wants wrapped around every goose screen that goose itself roots — today
 * that means fragment-hosted screens (ScreenFragment), which create their own ComposeView and
 * would otherwise render OUTSIDE your app theme and CompositionLocal providers.
 *
 * Contribute one from the app module:
 * ```
 * @ContributesIntoSet(AppScope::class)
 * @Inject
 * class AppThemeDecoration(private val imageLoader: ImageLoader) : GooseDecoration {
 *     @Composable override fun Decorate(content: @Composable () -> Unit) {
 *         AppTheme {
 *             CompositionLocalProvider(LocalImageLoader provides imageLoader) { content() }
 *         }
 *     }
 * }
 * ```
 *
 * Compose hosts (NavigableGooseContent, TabbedGooseContent) deliberately do NOT apply
 * decorations: they render inside your shell's composition, which already carries the theme —
 * applying them there would double-wrap the moment a flow flips from fragments to Compose.
 *
 * Contributions form a Set, so the nesting order of MULTIPLE decorations is unspecified;
 * prefer one decoration that composes your theme and providers in the order you want.
 */
fun interface GooseDecoration {
    @Composable
    fun Decorate(content: @Composable () -> Unit)
}
