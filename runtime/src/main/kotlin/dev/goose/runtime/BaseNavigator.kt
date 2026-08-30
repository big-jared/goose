package dev.goose.runtime

import android.os.Looper

/**
 * Shared result plumbing for concrete navigators. Subclasses implement stack mechanics and call
 * [deliverPopResult] whenever they remove a screen from their stack.
 *
 * Threading contract: concrete navigators mutate main-thread state (a snapshot-backed list, a
 * FragmentManager) and must be called on the main thread — subclasses enforce it with
 * [requireMainThread]. ViewModels never see this directly: they hold a [NavigatorHandle], which
 * dispatches to main from any thread.
 */
abstract class BaseNavigator(
    protected val resultRouter: ResultRouter,
) : Navigator {

    /**
     * The routing key for an awaited screen: the screen's class, scoped by the owning stack's
     * stable tag (appended by subclasses). Class + stack tag is deliberate: it stays stable
     * across activity recreation (a restored stack holds new-but-equal screen instances, so
     * identity-based keys would orphan pending awaiters), while the tag keeps same-class
     * requests in different stacks, tabs, or activities from cross-wiring.
     */
    protected open fun resultKeyFor(screen: Screen): String = resultRouter.resultKeyOf(screen)

    /** Fails fast on off-main mutation. No-ops on a plain JVM (no Android main looper). */
    protected fun requireMainThread() {
        val main = Looper.getMainLooper() ?: return
        check(Looper.myLooper() == main) {
            "Navigator methods must be called on the main thread. ViewModels are given a " +
                "main-dispatching NavigatorHandle; if you hold a concrete navigator, call it " +
                "from the main thread."
        }
    }

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? {
        val key = resultKeyFor(screen)
        val deferred = resultRouter.register(key)
        goTo(screen)
        return try {
            @Suppress("UNCHECKED_CAST")
            deferred.await() as R?
        } finally {
            resultRouter.unregister(key, deferred)
        }
    }

    /**
     * Routes [result] to whoever awaited [popped]. Also called with a null result on plain pops
     * so an awaiting caller resumes with "no answer" instead of hanging.
     */
    protected fun deliverPopResult(popped: Screen, result: PopResult?) {
        if (popped is ScreenWithResult<*>) {
            resultRouter.complete(resultKeyFor(popped), result)
        }
    }
}
