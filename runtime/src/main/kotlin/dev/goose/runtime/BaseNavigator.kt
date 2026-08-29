package dev.goose.runtime

/**
 * Shared result plumbing for concrete navigators. Subclasses implement stack mechanics and call
 * [deliverPopResult] whenever they remove a screen from their stack.
 */
abstract class BaseNavigator(
    protected val resultRouter: ResultRouter,
) : Navigator {

    /**
     * The routing key for an awaited screen. Navigators that multiplex several stacks (tabs)
     * override this to append a stable per-stack tag, so same-class requests in different stacks
     * never cross-wire. Must be stable across activity recreation — derive it from the screen's
     * class and stable stack identity, never from instance identity.
     */
    protected open fun resultKeyFor(screen: Screen): String = resultRouter.resultKeyOf(screen)

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
