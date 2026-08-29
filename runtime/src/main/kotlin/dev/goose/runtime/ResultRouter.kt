package dev.goose.runtime

import kotlinx.coroutines.CompletableDeferred

/**
 * Routes [PopResult]s from a popping screen back to the caller that awaited it.
 *
 * Results are keyed by the target screen's class (the same shape as the Fragment Result API's
 * requestKey), so routing works identically across Nav3-owned and FragmentManager-owned stacks
 * and across the migration boundary. Nested requests for the same screen type resolve LIFO.
 *
 * One instance lives in the app graph and is shared by every navigator in the tree.
 */
class ResultRouter {
    private val pending = LinkedHashMap<String, ArrayDeque<CompletableDeferred<PopResult?>>>()

    fun resultKeyOf(screen: Screen): String = resultKeyOf(screen.javaClass)

    fun resultKeyOf(screenClass: Class<out Screen>): String = screenClass.name

    /** Registers an awaiting caller. Pair with [unregister] in a finally block. */
    fun register(key: String): CompletableDeferred<PopResult?> {
        val deferred = CompletableDeferred<PopResult?>()
        synchronized(pending) {
            pending.getOrPut(key) { ArrayDeque() }.addLast(deferred)
        }
        return deferred
    }

    fun unregister(key: String, deferred: CompletableDeferred<PopResult?>) {
        synchronized(pending) {
            val deque = pending[key] ?: return
            deque.remove(deferred)
            if (deque.isEmpty()) pending.remove(key)
        }
    }

    /**
     * Delivers [result] (possibly null, meaning "dismissed without answering") to the most recent
     * caller awaiting [key]. No-ops when nobody is waiting.
     */
    fun complete(key: String, result: PopResult?) {
        val deferred = synchronized(pending) {
            val deque = pending[key] ?: return
            val d = deque.removeLastOrNull()
            if (deque.isEmpty()) pending.remove(key)
            d
        }
        deferred?.complete(result)
    }
}
