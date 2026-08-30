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
        // The navigation itself runs INSIDE the try: if it throws (a failing adapter, an
        // invalid transaction), the finally unregisters and no orphaned awaiter is left behind.
        return try {
            goToAwaited(screen, ResultAwaiter(resultRouter, key, deferred))
            @Suppress("UNCHECKED_CAST")
            deferred.await() as R?
        } finally {
            resultRouter.unregister(key, deferred)
        }
    }

    /**
     * Executes the navigation for an awaited screen, carrying THIS request's [awaiter]
     * explicitly. The default is a plain [goTo]: stack-hosted destinations deliver by key on
     * pop, which is LIFO-correct. Navigators with non-stack destinations (fragment adapters)
     * override this to hand the awaiter to the request, so answering is correlated exactly and
     * a plain same-class [goTo] issued while this caller waits can never steal its result.
     */
    protected open fun goToAwaited(screen: Screen, awaiter: ResultAwaiter) {
        goTo(screen)
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
