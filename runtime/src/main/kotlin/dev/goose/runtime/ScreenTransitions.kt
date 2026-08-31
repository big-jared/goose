package dev.goose.runtime

import androidx.compose.animation.ContentTransform

/**
 * Optional marker for a [Screen] that wants custom push/pop animations, the transition analogue
 * of [OverlayScreen]. Screens without it get the host's defaults (Nav3's slide).
 *
 * ```
 * @Serializable
 * data class ComposeMessageScreen(val to: String) : Screen, ScreenTransitions {
 *     override fun enterTransition() =
 *         slideInVertically { it } togetherWith fadeOut()
 *     override fun exitTransition() =
 *         fadeIn() togetherWith slideOutVertically { it }
 * }
 * ```
 *
 * The functions return [ContentTransform] (built with `togetherWith`), so a screen controls both
 * itself and the screen beneath it. They are behavior, not state: nothing here is serialized.
 * Ignored while the screen is hosted on a FragmentManager mid-migration (fragment transitions
 * apply there); a per-screen `FragmentScreenNavigation` override can supply custom fragment
 * animations.
 */
interface ScreenTransitions {
    /** Plays when this screen is pushed. Null keeps the host default. */
    fun enterTransition(): ContentTransform? = null

    /** Plays when this screen pops. Null keeps the host default. */
    fun exitTransition(): ContentTransform? = null

    /**
     * Plays while the user is DRAGGING the predictive back gesture on this screen. [swipeEdge]
     * is the edge the gesture started from (`BackEventCompat.EDGE_LEFT` / `EDGE_RIGHT`).
     * Defaults to [exitTransition], so a screen that customizes its pop automatically previews
     * the same motion under the gesture; returning null degrades to a plain crossfade during
     * the drag (the committed pop still uses [exitTransition]).
     */
    fun predictivePopTransition(swipeEdge: Int): ContentTransform? = exitTransition()
}
