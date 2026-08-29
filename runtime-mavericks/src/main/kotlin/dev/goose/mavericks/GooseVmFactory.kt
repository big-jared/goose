package dev.goose.mavericks

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import kotlin.reflect.KClass

/**
 * Composition-time context for VM creation. MavericksViewModelProvider resolves factories through
 * the VM's companion object, which only receives (viewModelContext, state) — there is no seam to
 * pass the navigator or the creator map through. Creation is synchronous on the main thread inside
 * [screenViewModel], so a scoped holder set around the `get()` call is safe and contained.
 */
internal object GooseVmLocator {
    internal class Scope(
        val screen: Screen,
        val navigator: Navigator,
        val creators: Map<KClass<*>, MavericksVmCreator>,
    )

    private val local = ThreadLocal<Scope?>()

    internal val current: Scope? get() = local.get()

    internal fun <T> withScope(scope: Scope, block: () -> T): T {
        local.set(scope)
        return try {
            block()
        } finally {
            // Creation never nests (it runs synchronously inside one remember block), so clear
            // the slot outright rather than implying re-entrancy support.
            local.remove()
        }
    }
}

/**
 * The factory each Goose-hosted ViewModel delegates its companion to:
 * ```
 * companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
 *   by gooseVmFactory(ProfileViewModel::class)
 * ```
 * Initial state still comes from Mavericks' own conventions (a secondary `State(screen)`
 * constructor, since the screen rides in as ViewModelContext.args) — byte-for-byte the fragment
 * behavior existing MvRx apps rely on.
 */
fun <VM : MavericksViewModel<S>, S : MavericksState> gooseVmFactory(
    vmClass: KClass<VM>,
): MavericksViewModelFactory<VM, S> = object : MavericksViewModelFactory<VM, S> {
    override fun create(viewModelContext: ViewModelContext, state: S): VM {
        val scope = checkNotNull(GooseVmLocator.current) {
            "${vmClass.simpleName} was created outside a Goose host. Goose-hosted ViewModels must " +
                "be obtained via screenViewModel() inside a ScreenEntry (or a Mavericks fragment " +
                "using its own factory during migration)."
        }
        val creator = scope.creators[vmClass]
            ?: error(
                "No MavericksVmCreator bound for ${vmClass.qualifiedName}. Add a " +
                    "@Provides @IntoMap @ClassKey(${vmClass.simpleName}::class) binding in the feature module."
            )
        @Suppress("UNCHECKED_CAST")
        return creator.create(state, scope.screen, scope.navigator) as VM
    }
}
