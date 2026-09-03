package dev.goose.runtime

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Optional marker for a [Screen] that wants custom push/pop animations, the transition analogue
 * of [OverlayScreen]. Screens without it get the host's defaults (Nav3's slide). Also a
 * [Presentation] facet: implement it on a shared presentation object instead and every screen
 * pointing at that presentation moves the same way (the screen's own declaration still wins).
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
 *
 * Not consulted for stack ROOTS. A root changing at the top of the display is a stack change (a
 * tab switch, or resetRoot), not a push or pop within one stack, so when the entry arriving at
 * the top is a stack root the host renders a short crossfade regardless of what the root screen
 * declares here — a root's transitions would only ever describe motion against a sibling stack
 * it knows nothing about. A stack change landing on a stack with screens PUSHED above its root
 * animates with the top screen's transitions (the display consults the entry actually entering).
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

/**
 * The platform-conventional horizontal stack motion in LEFT-TO-RIGHT layouts: pushes slide in
 * from the right (the outgoing screen drifting left beneath), pops reverse it, and the
 * predictive back gesture previews the pop. Pass as a host's `defaultTransitions` so every
 * screen without its own [ScreenTransitions] moves this way; individual screens still override
 * by implementing the interface (a modal wizard sliding up, say).
 *
 * This object is fixed LTR because transition functions run outside composition and cannot read
 * the ambient layout direction. Apps that ship RTL locales should pass
 * [rememberSlideScreenTransitions] instead, which picks the mirrored motion under RTL.
 */
object SlideScreenTransitions : ScreenTransitions {
    override fun enterTransition(): ContentTransform = slidePush(LayoutDirection.Ltr)

    override fun exitTransition(): ContentTransform = slidePop(LayoutDirection.Ltr)
}

/** [SlideScreenTransitions] mirrored: RTL convention pushes in from the left. */
internal object RtlSlideScreenTransitions : ScreenTransitions {
    override fun enterTransition(): ContentTransform = slidePush(LayoutDirection.Rtl)

    override fun exitTransition(): ContentTransform = slidePop(LayoutDirection.Rtl)
}

/**
 * [SlideScreenTransitions] matched to the composition's [LocalLayoutDirection]: the
 * conventional slide under LTR, its mirror under RTL. Prefer this over the raw object when
 * passing a host's `defaultTransitions`:
 *
 * ```
 * TabbedGooseContent(tabs, defaultTransitions = rememberSlideScreenTransitions())
 * ```
 */
@Composable
fun rememberSlideScreenTransitions(): ScreenTransitions =
    slideScreenTransitionsFor(LocalLayoutDirection.current)

/** The non-composable core of [rememberSlideScreenTransitions], split out so tests can pin it. */
internal fun slideScreenTransitionsFor(layoutDirection: LayoutDirection): ScreenTransitions =
    if (layoutDirection == LayoutDirection.Rtl) RtlSlideScreenTransitions else SlideScreenTransitions

private const val SLIDE_DURATION_MS = 300

/** The entering screen's full-width start offset: on-screen width away, toward the push origin. */
internal fun slideEnterOffset(layoutDirection: LayoutDirection, width: Int): Int =
    when (layoutDirection) {
        LayoutDirection.Ltr -> width
        LayoutDirection.Rtl -> -width
    }

/** The underlying screen's parallax drift: a third of the width, opposite the push origin. */
internal fun slideDriftOffset(layoutDirection: LayoutDirection, width: Int): Int =
    -slideEnterOffset(layoutDirection, width) / 3

private fun slidePush(direction: LayoutDirection): ContentTransform =
    (slideInHorizontally(tween(SLIDE_DURATION_MS)) { slideEnterOffset(direction, it) } +
        fadeIn(tween(SLIDE_DURATION_MS))) togetherWith
        (slideOutHorizontally(tween(SLIDE_DURATION_MS)) { slideDriftOffset(direction, it) } +
            fadeOut(tween(SLIDE_DURATION_MS)))

private fun slidePop(direction: LayoutDirection): ContentTransform =
    (slideInHorizontally(tween(SLIDE_DURATION_MS)) { slideDriftOffset(direction, it) } +
        fadeIn(tween(SLIDE_DURATION_MS))) togetherWith
        (slideOutHorizontally(tween(SLIDE_DURATION_MS)) { slideEnterOffset(direction, it) } +
            fadeOut(tween(SLIDE_DURATION_MS)))
