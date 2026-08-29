package dev.goose.runtime

import kotlinx.coroutines.CompletableDeferred

/**
 * Routes [PopResult]s from a popping screen back to the caller that awaited it.
 *
 * Results are keyed by the target screen's class, SCOPED BY STACK (navigators append their stack
 * tag — e.g. the tab key — via [dev.goose.runtime.BaseNavigator.resultKeyFor]). Stack-scoping is
 * what keeps two in-flight requests for the same screen type in different tabs from cross-wiring,
 * while class-based keys stay stable across activity recreation (the restored back stack contains
 * new-but-equal screen instances, so identity-based keys would orphan pending awaiters). Within
 * one stack, nested requests for the same screen type resolve LIFO, matching stack discipline.
 *
 * One instance lives in the app graph and is shared by every navigator in the tree.
 */
class ResultRouter {
    private val pending = LinkedHashMap<String, ArrayDeque<CompletableDeferred<PopResult?>>>()

    fun resultKeyOf(screen: Screen): String = screen.javaClass.name

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
