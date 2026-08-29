package dev.goose.runtime

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The [SharedTransitionScope] wrapping the host's NavDisplay, when shared elements are enabled. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The entry-level animation scope, bridged from the host so features stay host-agnostic. */
val LocalScreenAnimatedContentScope = staticCompositionLocalOf<AnimatedContentScope?> { null }

/**
 * Marks this element as a shared element across screen transitions, keyed by [key].
 *
 * Keys should be declared in `:api` modules so two features can share an element without an
 * impl→impl dependency. No-ops gracefully when the host provides no shared transition scope
 * (e.g. a screen hosted inside a fragment during migration).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedScreenElement(key: Any): Modifier {
    val sts = LocalSharedTransitionScope.current ?: return this
    val anim = LocalScreenAnimatedContentScope.current ?: return this
    return with(sts) {
        this@sharedScreenElement.sharedElement(rememberSharedContentState(key), anim)
    }
}
