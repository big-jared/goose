package dev.goose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A presenter that is NOT a Mavericks ViewModel — the presenter-agnostic option. Exposes one
 * [StateFlow] of [S]; the UI collects it with `collectAsState()`. Deliberately pure Kotlin plus
 * coroutines: no Mavericks, no Android ViewModel in the contract, which is the seam a future
 * multiplatform goose needs (only the retention mechanism in [rememberStateHolder] is
 * Android-specific).
 *
 * ```
 * class CounterStateHolder(private val navigator: Navigator) : StateHolder<CounterState>(CounterState()) {
 *     fun increment() = setState { copy(count = count + 1) }
 *     fun done() { navigator.pop() }
 * }
 * ```
 *
 * Lifecycle matches the screen-scoped ViewModel contract (docs/VIEWMODEL_CONTRACT.md): retained
 * across recomposition and configuration changes, cleared when the entry pops ([holderScope] is
 * cancelled, then [onCleared] runs). What it does NOT give you, by design: Mavericks'
 * `@PersistState` process-death restoration and `Async` machinery. State that must survive
 * process death belongs in a Mavericks ViewModel or in the screen's own saveable state; both
 * styles coexist per screen.
 */
abstract class StateHolder<S>(initial: S) {

    private val mutableState = MutableStateFlow(initial)

    /** The single state stream; collect with `collectAsState()` in the UI. */
    val state: StateFlow<S> = mutableState.asStateFlow()

    /** Cancelled when the owning entry pops. Main-immediate, like a presenter should be. */
    protected val holderScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected fun setState(reducer: S.() -> S) = mutableState.update(reducer)

    /** Runs after [holderScope] is cancelled, when the owning entry pops. */
    protected open fun onCleared() {}

    internal fun clear() {
        holderScope.cancel()
        onCleared()
    }
}

/** Retains one [StateHolder] (and its navigator handle) in the owning entry's ViewModelStore. */
internal class StateHolderRetainer : ViewModel() {
    val handle = NavigatorHandle()
    var holder: StateHolder<*>? = null

    override fun onCleared() {
        holder?.clear()
    }
}

/**
 * Obtains (or creates) the [StateHolder] for this screen, scoped to the current nav entry
 * exactly like `screenViewModel`: same instance across recomposition and rotation, cleared when
 * the entry pops. [create] runs once and receives a [Navigator] that is safe to hold forever
 * (a [NavigatorHandle], rebound to the live host on every composition, dispatching to main).
 *
 * ```
 * val holder = rememberStateHolder { navigator -> CounterStateHolder(navigator) }
 * val state by holder.state.collectAsState()
 * ```
 * A screen using several holders distinguishes them with [key].
 */
@Composable
fun <T : StateHolder<*>> rememberStateHolder(
    key: String = "default",
    create: (navigator: Navigator) -> T,
): T {
    val navigator = LocalNavigator.current
    val retainer = viewModel<StateHolderRetainer>(key = "goose:stateHolder:$key")
    DisposableEffect(navigator) {
        retainer.handle.bind(navigator)
        onDispose { retainer.handle.unbind(navigator) }
    }
    @Suppress("UNCHECKED_CAST")
    return retainer.holder as? T
        ?: create(retainer.handle).also { retainer.holder = it }
}
