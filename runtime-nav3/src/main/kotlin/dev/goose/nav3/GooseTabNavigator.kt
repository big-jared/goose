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
import dev.goose.runtime.StackHost
import dev.goose.runtime.StackKey

/** One tab in a [rememberTabNavigator] host. */
data class TabSpec(val key: StackKey, val root: Screen)

/**
 * A [StackHost] multiplexing one persisted Nav3 back stack per tab.
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
    private val stacksByKey: Map<StackKey, MutableList<NavKey>>,
    private val tabOrder: List<StackKey>,
    private val currentStackState: MutableState<StackKey>,
    resultRouter: ResultRouter,
    override val parent: Navigator? = null,
    private val hostTag: String,
) : BaseNavigator(resultRouter), StackHost {

    override val stacks: Set<StackKey> get() = stacksByKey.keys

    override val currentStack: StackKey by currentStackState

    private val currentEntries: MutableList<NavKey>
        get() = stacksByKey.getValue(currentStack)

    /**
     * Scope result routing per host instance AND per stack: pushes and pops both happen on the
     * current stack, so same-class awaits in different tabs (or another activity's host)
     * get distinct, recreation-stable keys.
     */
    override fun resultKeyFor(screen: Screen): String =
        "${resultRouter.resultKeyOf(screen)}#$hostTag#${currentStack.value}"

    val displayStack: List<NavKey>
        get() = buildList {
            tabOrder.forEach { key -> if (key != currentStack) addAll(stacksByKey.getValue(key)) }
            addAll(currentEntries)
        }

    override val backStack: List<Screen>
        get() = currentEntries.map { it.asScreen() }

    /** True when [key] is the root entry of ANY tab's stack. */
    fun isStackRoot(key: NavKey): Boolean = stacksByKey.values.any { it.firstOrNull() === key }

    override fun switchTo(key: StackKey): Navigator {
        requireMainThread()
        require(key in stacksByKey) { "Unknown stack $key (host owns $stacks)" }
        currentStackState.value = key
        return this
    }

    /**
     * Tab-bar button behavior, NOT a nav primitive: like [switchTo], except re-selecting the
     * current tab pops it to its root (the platform-conventional gesture). Feature code
     * navigating cross-stack should use [switchTo].
     */
    fun selectTab(key: StackKey) {
        requireMainThread()
        require(key in stacksByKey) { "Unknown stack $key (host owns $stacks)" }
        if (key == currentStack) {
            val stack = currentEntries
            while (stack.size > 1) {
                popTopOf(stack, result = null)
            }
        } else {
            currentStackState.value = key
        }
    }

    override fun goTo(screen: Screen) {
        requireMainThread()
        currentEntries.add(screen.pushed())
    }

    override fun pop(result: PopResult?): Boolean {
        requireMainThread()
        val stack = currentEntries
        if (stack.size > 1) {
            popTopOf(stack, result)
            return true
        }
        if (currentStack != tabOrder.first()) {
            currentStackState.value = tabOrder.first()
            return true
        }
        return parent?.pop(result) ?: false
    }

    override fun resetRoot(screen: Screen) {
        requireMainThread()
        val stack = currentEntries
        stack.asReversed().forEach { key -> deliverPopResult(key.asScreen(), null) }
        stack.clear()
        stack.add(screen.pushed())
    }

    private fun popTopOf(stack: MutableList<NavKey>, result: PopResult?) {
        val popped = stack.removeAt(stack.lastIndex)
        deliverPopResult(popped.asScreen(), result)
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
    require(tabs.distinctBy { it.key }.size == tabs.size) {
        "Tab keys must be unique: ${tabs.map { it.key }}"
    }
    val resultRouter = gooseGraph<GooseRuntimeAccessors>().resultRouter
    val stacks = tabs.associate { spec -> spec.key to rememberGooseBackStack(spec.root) }
    // A restored selection naming a tab this release no longer has falls back to the first tab.
    val currentTabState = rememberSaveable(
        stateSaver = Saver(
            { it.value },
            { raw -> StackKey(raw).takeIf { k -> tabs.any { it.key == k } } ?: tabs.first().key },
        ),
    ) { mutableStateOf(tabs.first().key) }
    val hostTag = rememberSaveable { java.util.UUID.randomUUID().toString() }
    return remember(tabs, parent) {
        GooseTabNavigator(stacks, tabs.map { it.key }, currentTabState, resultRouter, parent, hostTag)
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
