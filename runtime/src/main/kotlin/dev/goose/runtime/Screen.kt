package dev.goose.runtime

import androidx.navigation3.runtime.NavKey

/**
 * A navigation destination. Screens are the only currency of navigation: they live in `:api`
 * modules so features can navigate to each other without depending on implementations.
 *
 * Concrete screens must be `@Serializable` (kotlinx) so back stacks can survive process death.
 * Registration is reflective by default (the runtime resolves each screen's serializer by class
 * name); explicit registration via a contributed `screenSerializers { }` module is optional and
 * takes precedence, and is required only for custom `@SerialName`s.
 *
 * Screens are also [java.io.Serializable] because they double as Mavericks args
 * (ViewModelContext.args must be Parcelable or Serializable for saved-state persistence) —
 * screens are small data classes, so this costs nothing and keeps the MvRx contract intact.
 */
interface Screen : NavKey, java.io.Serializable

/**
 * A [Screen] rendered as a dialog OVER the previous entry instead of replacing it. Push and pop
 * it like any screen ([Navigator.goToForResult] works too — a dialog is a natural answerer);
 * tapping outside or system back pops it with a null result.
 *
 * The dialog window is configured by [dialogProperties]; the dialog's SIZE is whatever the
 * screen's composable measures (combine `usePlatformDefaultWidth = false` with width modifiers
 * for full control). Like [ScreenTransitions], the override is behavior, not serialized state.
 *
 * An overlay at the ROOT of a stack (deep link, resetRoot) has nothing to overlay and renders as
 * a plain full-screen entry instead.
 */
interface OverlayScreen : Screen {
    /** Window-level dialog configuration: outside-tap/back dismissal, width policy, security. */
    fun dialogProperties(): androidx.compose.ui.window.DialogProperties =
        androidx.compose.ui.window.DialogProperties()
}

/** A result a [ScreenWithResult] can answer with when popped. Concrete types are `@Serializable`. */
interface PopResult

/**
 * A [Screen] that answers with a typed result. Callers use [Navigator.goToForResult]; the screen's
 * own presenter pops with [Navigator.pop] passing an [R].
 */
interface ScreenWithResult<R : PopResult> : Screen

/** Identifies one back stack in a multi-stack (tabbed) host. */
@JvmInline
value class StackKey(val value: String)
