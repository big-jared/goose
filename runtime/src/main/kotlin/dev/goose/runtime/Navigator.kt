package dev.goose.runtime

/**
 * The seam between presentation and whatever owns the back stack (a Nav3 list, a FragmentManager,
 * or a tabbed host). ViewModels are handed a [Navigator] and never learn which world they live in,
 * which is what makes fragment→compose migration per-screen.
 *
 * Navigators form a tree: an unhandled [pop] at a stack's root bubbles to [parent].
 */
interface Navigator {
    /** The navigator owning the enclosing stack, or null at the root of the tree. */
    val parent: Navigator?

    /** Read-only view of this navigator's own stack, bottom-first. */
    val backStack: List<Screen>

    fun goTo(screen: Screen)

    /**
     * Pops the top screen. If [result] is non-null and the popped screen is a [ScreenWithResult],
     * the result is delivered to the caller awaiting it. Returns false if this navigator was
     * already at its root and no parent handled the pop.
     */
    fun pop(result: PopResult? = null): Boolean

    /** Clears this stack and starts over at [screen]. */
    fun resetRoot(screen: Screen)

    /**
     * Pushes [screen] and suspends until it pops. Returns the typed result, or null when the
     * screen was dismissed without answering (system back, plain [pop]).
     *
     * Survives configuration changes (the caller is expected to be a retained presenter). Does NOT
     * survive process death — the suspension dies with the process; treat a null-less restart as
     * "no answer", the same contract as a coroutine-wrapped ActivityResult.
     */
    suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R?
}

/**
 * A [Navigator] that multiplexes several saved back stacks — a bottom-nav tab host is the common
 * case, but the nav contract knows nothing about tabs, only stacks.
 *
 * [Navigator.goTo] on a host (as on every navigator) pushes onto the CURRENT stack; a screen
 * carries no stack affinity, so the same screen is pushable in any stack. Changing stacks is a
 * separate, explicit intent: [switchTo].
 */
interface StackHost : Navigator {
    /** The keys of every stack this host owns. */
    val stacks: Set<StackKey>

    /** The key of the stack currently displayed. */
    val currentStack: StackKey

    /**
     * Makes [key] the current stack, preserving the state of the stack being left (already
     * current is a no-op). Returns this host as a [Navigator] — now addressing the new current
     * stack — so a cross-stack push chains:
     * `navigator.switchTo(Orders).goTo(OrderScreen(id))`. Both mutations land before the next
     * frame, so the switch-and-push renders atomically.
     *
     * Throws on a key this host doesn't own; from a nested stack, use the tree-walking
     * [Navigator.switchTo] extension instead of calling the host directly.
     */
    fun switchTo(key: StackKey): Navigator
}

/**
 * Switches to the stack [key], from anywhere in the navigator tree: walks [Navigator.parent]
 * (starting at this navigator) to the nearest [StackHost] owning [key] and delegates to
 * [StackHost.switchTo]. This is how a screen deep inside a nested flow routes to a sibling
 * stack without knowing where its host lives:
 * `navigator.switchTo(GaggleStacks.Profile).goTo(OrderHistoryScreen(id))`.
 *
 * Throws when no ancestor host owns [key] — addressing a stack that isn't hosted is a
 * programming error (wrong key, or the host isn't in this navigator's ancestry).
 */
fun Navigator.switchTo(key: StackKey): Navigator {
    var nav: Navigator? = this
    while (nav != null) {
        if (nav is StackHost && key in nav.stacks) return nav.switchTo(key)
        nav = nav.parent
    }
    error(
        "No StackHost owning $key in the navigator tree above $this. " +
            "Check the key, and that the host is an ancestor of this navigator."
    )
}
