package dev.goose.runtime

import androidx.compose.ui.window.DialogProperties

/**
 * A reusable presentation type: an app-defined object naming HOW a family of screens appears
 * (a bottom sheet, a full-screen modal, a help dialog), so each screen declares WHICH
 * presentation it uses instead of carrying the behavior itself. Screens point at one via
 * [PresentedScreen]:
 *
 * ```
 * // design-system module — defined once
 * object BottomSheet : Presentation, ScreenTransitions {
 *     override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
 * }
 *
 * // any :api module — screens just reference it
 * @Serializable data class HelpScreen(val topic: String) : PresentedScreen {
 *     override val presentation get() = BottomSheet
 * }
 * ```
 *
 * A presentation opts into behavior by implementing FACETS:
 * - [ScreenTransitions] — push/pop/predictive-back motion on Compose hosts.
 * - [Overlay] — render in a dialog over the previous entry, on both hosts.
 *
 * Facets are values, so Compose hosts consume them with no registration. Fragment hosts
 * (mid-migration) need FragmentManager mechanics and sometimes injection, so fragment behavior
 * binds once per presentation TYPE via `@GoosePresentationNavigation(BottomSheet::class)` in
 * `runtime-fragment` — one binding covers every screen using the presentation. [Overlay] is the
 * exception: the fragment host handles it built-in (a dialog host fragment), so a plain dialog
 * needs no binding anywhere.
 *
 * Precedence on both hosts: a facet the screen implements ITSELF beats the same facet on its
 * presentation, which beats the host default.
 */
interface Presentation

/**
 * A [Screen] that appears through a shared [Presentation]. Declare the presentation as a
 * getter (`override val presentation get() = BottomSheet`): like [ScreenTransitions], it is
 * behavior, not state — a body property WITH a backing field would be serialized, and
 * presentations are not serializable.
 */
interface PresentedScreen : Screen {
    val presentation: Presentation
}

/**
 * Facet: render in a dialog OVER the previous entry instead of replacing it. Implemented by a
 * screen directly (see [OverlayScreen]) or by its [Presentation]. Push and pop like any screen
 * (`Navigator.goToForResult` works too — a dialog is a natural answerer); tapping outside or
 * system back pops it with a null result, on the Compose host and the fragment host alike.
 *
 * The dialog window is configured by [dialogProperties]; the dialog's SIZE is whatever the
 * screen's composable measures (on Compose hosts, combine `usePlatformDefaultWidth = false`
 * with width modifiers for full control). On fragment hosts the properties map onto the
 * platform Dialog where they overlap: `dismissOnBackPress` rides the dialog's single
 * cancelable flag, so disabling it also disables outside-tap dismissal there.
 */
interface Overlay {
    /** Window-level dialog configuration: outside-tap/back dismissal, width policy, security. */
    fun dialogProperties(): DialogProperties = DialogProperties()
}

/**
 * The screen's effective [Overlay] facet: its own declaration, else its presentation's, else
 * null (a plain full-screen entry). Host-side resolution; apps don't call this.
 */
fun Screen.effectiveOverlay(): Overlay? =
    this as? Overlay ?: (this as? PresentedScreen)?.presentation as? Overlay

/**
 * The screen's effective [ScreenTransitions]: its own declaration, else its presentation's,
 * else [defaultTransitions]. Host-side resolution; apps don't call this.
 */
fun Screen.effectiveTransitions(defaultTransitions: ScreenTransitions? = null): ScreenTransitions? =
    this as? ScreenTransitions
        ?: (this as? PresentedScreen)?.presentation as? ScreenTransitions
        ?: defaultTransitions
