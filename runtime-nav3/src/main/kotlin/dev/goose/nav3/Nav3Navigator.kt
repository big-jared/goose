package dev.goose.nav3

import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.BaseNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.PopResult
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen

/**
 * A [Navigator] over a Nav3 back stack (any snapshot-backed MutableList of NavKeys, typically a
 * `NavBackStack` from `rememberNavBackStack`). Mutating the list is the navigation.
 *
 * [stackTag] scopes result routing to THIS stack instance and is mandatory: it must be stable
 * across recreation (hosts persist one with rememberSaveable, as NavigableGooseContent does),
 * so same-class result requests in two stacks — or two activities — never cross-deliver.
 */
class Nav3Navigator(
    private val stack: MutableList<NavKey>,
    resultRouter: ResultRouter,
    override val parent: Navigator? = null,
    private val stackTag: String,
) : BaseNavigator(resultRouter) {

    override fun resultKeyFor(screen: Screen): String =
        "${resultRouter.resultKeyOf(screen)}#$stackTag"

    override val backStack: List<Screen>
        get() = stack.map { it.asScreen() }

    override fun goTo(screen: Screen) {
        requireMainThread()
        // Wrapped in a per-push record: equal screen values pushed twice are distinct entries.
        stack.add(screen.pushed())
    }

    override fun pop(result: PopResult?): Boolean {
        requireMainThread()
        if (stack.size > 1) {
            val popped = stack.removeAt(stack.lastIndex)
            deliverPopResult(popped.asScreen(), result)
            return true
        }
        // At our root: this stack's screens aren't ours to remove — bubble to the parent, which
        // pops the entry hosting this stack (delivering [result] if that entry awaits one).
        return parent?.pop(result) ?: false
    }

    override fun resetRoot(screen: Screen) {
        requireMainThread()
        // Every removed screen is "dismissed without answering" — resume any awaiting callers
        // with null instead of leaving their goToForResult suspended forever.
        stack.asReversed().forEach { key -> deliverPopResult(key.asScreen(), null) }
        stack.clear()
        stack.add(screen.pushed())
    }
}
