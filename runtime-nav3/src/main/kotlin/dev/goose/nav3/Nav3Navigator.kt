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
 */
class Nav3Navigator(
    private val stack: MutableList<NavKey>,
    resultRouter: ResultRouter,
    override val parent: Navigator? = null,
) : BaseNavigator(resultRouter) {

    override val backStack: List<Screen>
        get() = stack.filterIsInstance<Screen>()

    override fun goTo(screen: Screen) {
        stack.add(screen)
    }

    override fun pop(result: PopResult?): Boolean {
        if (stack.size > 1) {
            val popped = stack.removeAt(stack.lastIndex)
            (popped as? Screen)?.let { deliverPopResult(it, result) }
            return true
        }
        // At our root: this stack's screens aren't ours to remove — bubble to the parent, which
        // pops the entry hosting this stack (delivering [result] if that entry awaits one).
        return parent?.pop(result) ?: false
    }

    override fun resetRoot(screen: Screen) {
        // Every removed screen is "dismissed without answering" — resume any awaiting callers
        // with null instead of leaving their goToForResult suspended forever.
        stack.asReversed().forEach { key -> (key as? Screen)?.let { deliverPopResult(it, null) } }
        stack.clear()
        stack.add(screen)
    }
}
