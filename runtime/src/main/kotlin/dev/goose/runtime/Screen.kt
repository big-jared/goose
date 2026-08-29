package dev.goose.runtime

import androidx.navigation3.runtime.NavKey

/**
 * A navigation destination. Screens are the only currency of navigation: they live in `:api`
 * modules so features can navigate to each other without depending on implementations.
 *
 * Concrete screens must be `@Serializable` (kotlinx) so back stacks can survive process death,
 * and must be registered polymorphically against [NavKey] via a contributed `SerializersModule`.
 */
interface Screen : NavKey

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
