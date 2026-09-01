package dev.goose.gaggle

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.goose.runtime.GooseDecoration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/**
 * Demonstrates: an app-contributed [GooseDecoration]. Fragment-hosted goose screens (the
 * support chat riding SupportFlowFragment's child FragmentManager) root their OWN ComposeView,
 * outside MainActivity's `MaterialTheme { ... }` — this decoration is how they get the app
 * theme (and any CompositionLocal providers) anyway. Compose-hosted screens never see it;
 * they inherit the shell's composition directly.
 */
@ContributesIntoSet(AppScope::class)
@Inject
class GaggleThemeDecoration : GooseDecoration {
    @Composable
    override fun Decorate(content: @Composable () -> Unit) {
        MaterialTheme { content() }
    }
}
