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

/** A [Navigator] that multiplexes several saved back stacks, bottom-nav style. */
interface TabNavigator : Navigator {
    val currentTab: StackKey

    /**
     * Makes [key] the active stack, preserving the state of the one being left.
     * Re-selecting the current tab pops it to its root.
     */
    fun selectTab(key: StackKey)
}
