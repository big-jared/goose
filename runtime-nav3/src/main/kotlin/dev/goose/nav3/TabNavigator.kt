package dev.goose.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.gooseGraph
import dev.goose.runtime.BaseNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.PopResult
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import dev.goose.runtime.StackKey
import dev.goose.runtime.TabNavigator

/** One tab in a [rememberTabNavigator] host. */
data class TabSpec(val key: StackKey, val root: Screen)

/**
 * A [TabNavigator] multiplexing one persisted Nav3 back stack per tab.
 *
 * The [displayStack] handed to NavDisplay is every tab's stack flattened with the current tab's
 * last. Keeping hidden tabs' entries in the display list is what preserves their ViewModels and
 * saveable state across tab switches — the ViewModelStore decorator only clears state for entries
 * that leave the list entirely (a real pop). One consequence: the display list always holds at
 * least one entry per tab, so the host NavDisplay intercepts system back even at the primary
 * tab's root — pass `onRootBack` to [TabbedGooseContent] (e.g. `{ finish() }`) so an unhandled
 * root pop still exits.
 *
 * Back semantics: pop the current stack; at a non-primary tab's root, fall back to the primary
 * tab; at the primary tab's root, bubble to [parent] or report unhandled.
 */
class GooseTabNavigator internal constructor(
    private val stacks: Map<StackKey, MutableList<NavKey>>,
    private val tabOrder: List<StackKey>,
    private val currentTabState: MutableState<StackKey>,
    resultRouter: ResultRouter,
    override val parent: Navigator? = null,
) : BaseNavigator(resultRouter), TabNavigator {

    override val currentTab: StackKey by currentTabState

    private val currentStack: MutableList<NavKey>
        get() = stacks.getValue(currentTab)

    /**
     * Scope result routing per tab: pushes and pops both happen on the current tab's stack, so
     * same-class awaits in different tabs get distinct, recreation-stable keys.
     */
    override fun resultKeyFor(screen: Screen): String =
        "${resultRouter.resultKeyOf(screen)}#${currentTab.value}"

    val displayStack: List<NavKey>
        get() = buildList {
            tabOrder.forEach { key -> if (key != currentTab) addAll(stacks.getValue(key)) }
            addAll(currentStack)
        }

    override val backStack: List<Screen>
        get() = currentStack.filterIsInstance<Screen>()

    /** True when [key] is the root entry of ANY tab's stack. */
    fun isStackRoot(key: NavKey): Boolean = stacks.values.any { it.firstOrNull() === key }

    override fun selectTab(key: StackKey) {
        require(key in stacks) { "Unknown tab $key" }
        if (key == currentTab) {
            // Re-select pops the tab to its root.
            val stack = currentStack
            while (stack.size > 1) {
                popTopOf(stack, result = null)
            }
        } else {
            currentTabState.value = key
        }
    }

    override fun goTo(screen: Screen) {
        currentStack.add(screen)
    }

    override fun pop(result: PopResult?): Boolean {
        val stack = currentStack
        if (stack.size > 1) {
            popTopOf(stack, result)
            return true
        }
        if (currentTab != tabOrder.first()) {
            currentTabState.value = tabOrder.first()
            return true
        }
        return parent?.pop(result) ?: false
    }

    override fun resetRoot(screen: Screen) {
        val stack = currentStack
        stack.asReversed().forEach { key -> (key as? Screen)?.let { deliverPopResult(it, null) } }
        stack.clear()
        stack.add(screen)
    }

    private fun popTopOf(stack: MutableList<NavKey>, result: PopResult?) {
        val popped = stack.removeAt(stack.lastIndex)
        (popped as? Screen)?.let { deliverPopResult(it, result) }
    }
}

/**
 * Creates the tab navigator with one persisted back stack per tab. The selected tab survives
 * process death via rememberSaveable; each stack persists through [rememberGooseBackStack].
 */
@Composable
fun rememberTabNavigator(
    tabs: List<TabSpec>,
    parent: Navigator? = null,
): GooseTabNavigator {
    require(tabs.isNotEmpty()) { "At least one tab required" }
    val resultRouter = gooseGraph<GooseRuntimeAccessors>().resultRouter
    val stacks = tabs.associate { spec -> spec.key to rememberGooseBackStack(spec.root) }
    val currentTabState = rememberSaveable(
        stateSaver = Saver({ it.value }, { StackKey(it) }),
    ) { mutableStateOf(tabs.first().key) }
    return remember(tabs, parent) {
        GooseTabNavigator(stacks, tabs.map { it.key }, currentTabState, resultRouter, parent)
    }
}

/**
 * Renders a [GooseTabNavigator]'s combined stack — the tabbed stack host. Pair with your own tab
 * bar UI. [onRootBack] fires when back is unhandled at the primary tab's root (typically
 * `finish()`).
 */
@Composable
fun TabbedGooseContent(
    tabNavigator: GooseTabNavigator,
    modifier: Modifier = Modifier,
    onRootBack: (() -> Unit)? = null,
) {
    GooseNavDisplay(
        displayStack = tabNavigator.displayStack,
        navigator = tabNavigator,
        isStackRoot = tabNavigator::isStackRoot,
        modifier = modifier,
        onRootBack = onRootBack,
    )
}
