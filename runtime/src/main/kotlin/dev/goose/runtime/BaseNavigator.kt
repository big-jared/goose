package dev.goose.runtime

/**
 * Shared result plumbing for concrete navigators. Subclasses implement stack mechanics and call
 * [deliverPopResult] whenever they remove a screen from their stack.
 */
abstract class BaseNavigator(
    protected val resultRouter: ResultRouter,
) : Navigator {

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? {
        val key = resultRouter.resultKeyOf(screen)
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
            resultRouter.complete(resultRouter.resultKeyOf(popped), result)
        }
    }
}
