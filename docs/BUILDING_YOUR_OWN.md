# Mavericks navigation, as a PR stack

For apps that want something like goose but not goose exactly. This is the library
deconstructed into a PR stack an app can build in its own module, written for the common
starting point: a multimodule Mavericks + fragments app on Dagger and Metro, migrating to
Compose one screen at a time.

Phase 1 (PRs 1 through 8) runs entirely on your existing FragmentManager back stack. At
the end of it, screens migrate one per PR. Phase 2 (PRs 9 through 14) adds the
Compose-owned stack, and lands later, when your first whole flow has migrated. Nothing in
phase 1 depends on Navigation 3.

Each PR compiles green on its own and every later PR builds only on reviewed surface.
Names use package `yourapp.nav`. Rename freely. Goose's own files are linked per PR as
the reference implementation.

Ground rules the whole stack obeys:

- Screens are small serializable data classes or objects living in feature `:api` modules.
- ViewModels see only the `Navigator` interface, never a back stack implementation.
- Entry identity is per push, never per screen value. (FragmentManager gives this for
  free, every transaction is its own entry. Phase 2 must rebuild it by hand.)
- Result routing keys are class + stack tag, never instance identity.
- Every dismissal answers an awaiting caller, with null meaning "dismissed without answering."

Dependencies: phase 1 needs only Compose UI (for the migrated screens' content) and
coroutines. Phase 2 adds `androidx.navigation3:navigation3-runtime`,
`navigation3-ui`, and `kotlinx-serialization-core`.

## If the module already exists in your app, start here

The PR stack is for the team building the module. For a feature team adopting it, this
is the entire surface, with the PR that explains each line.

Once, in `:app`:

1. One `@SingleIn(AppScope::class)` provider that calls `NavRuntime.Builder()` with the
   multibound maps (PR 3).
2. One host fragment, a subclass of your base:
   `class AppScreenFragment : AppBaseFragment() { override fun onCreateView(...) = screenView { AppTheme { it() } } }` (PR 4).
3. In the activity, `@Inject lateinit var navRuntime: NavRuntime` and one line in
   `onCreate`: `installNavigator(R.id.container, AppScreenFragment::class, navRuntime)` (PR 4).

Per screen you migrate:

4. `data class ProfileScreen(val userId: String) : Screen` in the feature's `:api` module (PR 1).
5. On the ViewModel: `@Assisted private val navigator: Navigator`, the matching parameter
   on its `@AssistedFactory`, and
   `companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState> by screenVmFactory(ProfileViewModel::class)` (PR 5).
6. One provider in the feature module:
   `@Provides @IntoMap @ClassKey(ProfileScreen::class) fun profileUi(factory: ProfileViewModel.Factory): ScreenEntry = screenUi<ProfileScreen> { screen, modifier -> ... }`
   whose body calls `screenViewModel(screen, factory::create)` (PRs 3 and 5).
7. Delete the fragment. For every destination it navigates to that is still a fragment,
   one `ScreenFragmentBinder` provider under that screen's class (PR 4).

Legacy call sites navigate with `requireActivity().appNavigator.goTo(ProfileScreen(id))`.
ViewModel tests hand in a `FakeNavigator` (PR 1). That's all of it.

---

# Phase 1: migrate screens on the fragment stack

## PR 1: A screen is a value, a navigator is an interface

Contracts only. No Android, no Compose. Reference:
[Screen.kt](../runtime/src/main/kotlin/dev/goose/runtime/Screen.kt),
[Navigator.kt](../runtime/src/main/kotlin/dev/goose/runtime/Navigator.kt).

`nav/src/main/kotlin/yourapp/nav/Screen.kt`

```kotlin
package yourapp.nav

/**
 * A destination. Concrete screens are data classes or objects in :api modules.
 * java.io.Serializable so a screen rides a fragment arguments Bundle as-is.
 * (`object` screens need `private fun readResolve(): Any = TheObject`.)
 */
interface Screen : java.io.Serializable

/** A typed answer a screen can pop with. */
interface PopResult

/** A screen that answers with R. Callers use Navigator.goToForResult. */
interface ScreenWithResult<R : PopResult> : Screen
```

`nav/src/main/kotlin/yourapp/nav/Navigator.kt`

```kotlin
package yourapp.nav

interface Navigator {
    /** Navigator owning the enclosing stack, null at the root. Pops bubble up through it. */
    val parent: Navigator?

    /** This navigator's own stack, bottom-first, read-only. */
    val backStack: List<Screen>

    fun goTo(screen: Screen)

    /**
     * Pops the top screen, delivering [result] to an awaiting caller if the popped screen
     * is a ScreenWithResult. Returns false if already at the root and no parent handled it.
     */
    fun pop(result: PopResult? = null): Boolean

    /** Clears this stack and starts over at [screen]. */
    fun resetRoot(screen: Screen)

    /**
     * Pushes [screen], suspends until it pops. Null means dismissed without answering
     * (system back, plain pop). Survives recreation. Does NOT survive process death.
     */
    suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R?
}
```

**Many stacks.** A tab host is a row of drawers, one open at a time, and every screen's
`navigator` points at the drawer that screen lives in. That pointer never changes meaning,
which is why the naive cross-tab push goes wrong:

```kotlin
navigator.switchTo(Tabs.Profile)          // Profile drawer is now open, but...
navigator.goTo(OrderHistoryScreen(id))    // WRONG: my navigator still means MY stack,
                                          // so this pushes behind the visible tab
```

The fix is `switchTo`'s return value: it opens the drawer AND hands you a navigator for
that drawer, like `cd` returning the directory you moved into:

```kotlin
navigator.switchTo(Tabs.Profile).goTo(OrderHistoryScreen(id))   // pushes onto Profile
```

Two rules keep this composable: `goTo` always pushes onto the current stack (screens
carry no tab affinity, the same screen is pushable in any tab), and leaving your stack
is a separate explicit call that works from anywhere in the navigator tree. The contract
is host-free, so it lands here. Fragment tabs implement it in PR 7, Nav3 tabs in PR 12.

`nav/src/main/kotlin/yourapp/nav/StackHost.kt`

```kotlin
package yourapp.nav

@JvmInline value class StackKey(val value: String)

/** A navigator that owns several stacks and shows one at a time. */
interface StackHost : Navigator {
    val stacks: Set<StackKey>
    val currentStack: StackKey
    /** Shows [key]'s stack, preserving the one being left, and returns this host addressing it. */
    fun switchTo(key: StackKey): Navigator
}

/** From anywhere in the tree: walk parents to the nearest host owning [key]. */
fun Navigator.switchTo(key: StackKey): Navigator {
    var nav: Navigator? = this
    while (nav != null) {
        if (nav is StackHost && key in nav.stacks) return nav.switchTo(key)
        nav = nav.parent
    }
    error("No StackHost owning $key above $this. Check the key, and that the host is an ancestor.")
}
```

**The fake every feature team will use.** A ViewModel takes a `Navigator`, so its tests
take a fake. This is the most-used surface in the whole module and it ships as a test
fixture from day one. The important part is scripting answers, so a test can drive a
`goToForResult` call through its picker with no UI.

`nav/src/testFixtures/kotlin/yourapp/nav/FakeNavigator.kt`

```kotlin
package yourapp.nav

class FakeNavigator(override val parent: Navigator? = null) : Navigator {
    override val backStack = mutableListOf<Screen>()
    val calls = mutableListOf<String>()
    private val answers = mutableMapOf<KClass<out Screen>, ArrayDeque<PopResult?>>()

    /** The next goToForResult for a screen of this class resumes with [result]. Queue several for several calls. */
    fun <R : PopResult> answer(screen: KClass<out ScreenWithResult<R>>, result: R?) {
        answers.getOrPut(screen) { ArrayDeque() }.addLast(result)
    }

    override fun goTo(screen: Screen) { backStack += screen; calls += "goTo($screen)" }

    override fun pop(result: PopResult?): Boolean {
        calls += "pop($result)"
        if (backStack.size <= 1) return parent?.pop(result) ?: false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    override fun resetRoot(screen: Screen) {
        backStack.clear(); backStack += screen; calls += "resetRoot($screen)"
    }

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? {
        goTo(screen)
        val answer = answers[screen::class]?.removeFirstOrNull()
            ?: error("No answer scripted for ${screen::class.simpleName}. Call answer() first.")
        backStack.remove(screen)
        @Suppress("UNCHECKED_CAST") return answer as R?
    }
}
```

A ViewModel test then reads like the feature it tests:

```kotlin
@Test fun `changing address updates state`() = runTest {
    val nav = FakeNavigator().apply { answer(PickAddressScreen::class, Address("1 Main St")) }
    val vm = CheckoutViewModel(CheckoutState(orderId = "o1"), nav, FakeOrderRepo())

    vm.changeAddress()

    assertEquals("1 Main St", vm.awaitState().address?.line1)
    assertEquals(listOf("goTo(PickAddressScreen(orderId=o1))"), nav.calls)
}
```

`FakeStackHost` is one `FakeNavigator` per key with a recording `switchTo`.

Tests, `NavigatorContractTest`:

- `goToAppendsToBackStack`
- `popAtRootReturnsFalseWithNoParent`
- `popAtRootDelegatesToParent`
- `resetRootLeavesExactlyOneScreen`
- `switchToWalksParentsToTheOwningHost`
- `switchToOnUnownedKeyThrows`

---

## PR 2: Ask a screen a question, get an answer or null

The result engine and the navigator base classes. Host-free: the same router will
serve the fragment navigator now and the Compose one in phase 2, which is what lets a
result cross hosts mid-migration. Reference:
[ResultRouter.kt](../runtime/src/main/kotlin/dev/goose/runtime/ResultRouter.kt),
[BaseNavigator.kt](../runtime/src/main/kotlin/dev/goose/runtime/BaseNavigator.kt).

`nav/src/main/kotlin/yourapp/nav/ResultRouter.kt`

```kotlin
package yourapp.nav

import kotlinx.coroutines.CompletableDeferred

/** One instance per app, shared by every navigator in the tree. */
class ResultRouter {
    private val pending = LinkedHashMap<String, ArrayDeque<CompletableDeferred<PopResult?>>>()

    fun resultKeyOf(screen: Screen): String = screen.javaClass.name

    fun register(key: String): CompletableDeferred<PopResult?> {
        val d = CompletableDeferred<PopResult?>()
        synchronized(pending) { pending.getOrPut(key) { ArrayDeque() }.addLast(d) }
        return d
    }

    fun unregister(key: String, d: CompletableDeferred<PopResult?>) {
        synchronized(pending) {
            val q = pending[key] ?: return
            q.remove(d)
            if (q.isEmpty()) pending.remove(key)
        }
    }

    /** Delivers to the MOST RECENT awaiter of [key]. LIFO matches stack discipline. */
    fun complete(key: String, result: PopResult?) {
        val d = synchronized(pending) {
            val q = pending[key] ?: return
            val d = q.removeLastOrNull()
            if (q.isEmpty()) pending.remove(key)
            d
        }
        d?.complete(result)
    }

    /** Delivers to ONE specific awaiter, for destinations that bypass stack order. */
    internal fun completeExact(key: String, d: CompletableDeferred<PopResult?>, result: PopResult?) {
        val found = synchronized(pending) {
            val q = pending[key] ?: return
            val removed = q.remove(d)
            if (q.isEmpty()) pending.remove(key)
            removed
        }
        if (found) d.complete(result)
    }
}

/** One-shot handle to one awaiting caller. First complete wins, later calls no-op. */
class ResultAwaiter internal constructor(
    private val router: ResultRouter,
    private val key: String,
    private val deferred: CompletableDeferred<PopResult?>,
) {
    fun complete(result: PopResult?) = router.completeExact(key, deferred, result)
}
```

`nav/src/main/kotlin/yourapp/nav/BaseNavigator.kt`

`BaseNavigator` owns everything every navigator has in common, and makes it final. An
implementation writes three stack operations and inherits the rest: main-thread checks,
a root pop bubbling to the parent, result keys scoped by stack, and the result plumbing.
None of it can be forgotten, because none of it is overridable. (One Android import,
`Looper`, for the main-thread check. It no-ops on a plain JVM so these tests stay fast.)

```kotlin
package yourapp.nav

import android.os.Looper

abstract class BaseNavigator(
    protected val router: ResultRouter,
    final override val parent: Navigator?,
    /** Stable across recreation, unique per stack. Hosts persist one. */
    private val stackTag: String,
) : Navigator {

    // ---- what an implementation writes ----

    /** Push [screen] onto this stack. */
    protected abstract fun push(screen: Screen)

    /** Pop this stack's own top entry. Return false at the root, having popped nothing. */
    protected abstract fun popOwn(result: PopResult?): Boolean

    /** Clear this stack and start over at [screen]. Every removed screen must deliver null. */
    protected abstract fun reset(screen: Screen)

    /** True when [popOwn] would return false. Hosts read it to decide whether to bubble. */
    abstract val isAtRoot: Boolean

    /** Override only for non-stack destinations that must hand the awaiter through (PR 6). */
    protected open fun goToAwaited(screen: Screen, awaiter: ResultAwaiter) = goTo(screen)

    // ---- what every implementation inherits ----

    final override fun goTo(screen: Screen) {
        requireMainThread()
        push(screen)
    }

    final override fun pop(result: PopResult?): Boolean {
        requireMainThread()
        return popOwn(result) || (parent?.pop(result) ?: false)   // at the root, bubble up
    }

    final override fun resetRoot(screen: Screen) {
        requireMainThread()
        reset(screen)
    }

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? {
        requireMainThread()
        val key = resultKeyFor(screen)
        val deferred = router.register(key)
        // Navigation runs INSIDE the try: if it throws, finally leaves no orphaned awaiter.
        return try {
            goToAwaited(screen, ResultAwaiter(router, key, deferred))
            @Suppress("UNCHECKED_CAST")
            deferred.await() as R?
        } finally {
            router.unregister(key, deferred)
        }
    }

    /** Class + stack, never instance identity. */
    protected fun resultKeyFor(screen: Screen): String = "${router.resultKeyOf(screen)}#$stackTag"

    /** Call on EVERY removal, with null for plain pops, so callers never hang. */
    protected fun deliverPopResult(popped: Screen, result: PopResult?) {
        if (popped is ScreenWithResult<*>) router.complete(resultKeyFor(popped), result)
    }

    protected fun requireMainThread() {
        val main = Looper.getMainLooper() ?: return
        check(Looper.myLooper() == main) { "Navigator methods must be called on the main thread." }
    }
}
```

The one obligation the base class cannot take over: **every removal delivers.** Only the
implementation knows when its stack dropped an entry, including removals it didn't
initiate, so it must call `deliverPopResult` from wherever it learns that.

(`isAtRoot` looks unused in this PR. PR 7's multi-stack host base class needs it to
decide whether to bubble, and it belongs on the contract rather than being bolted on
later.)

**The navigator a ViewModel actually holds.** ViewModels outlive activities, but every
concrete navigator wraps something that dies with one (a FragmentManager, a remembered
list). So a ViewModel is never handed a concrete navigator. It gets a `NavigatorHandle`:
a stable `Navigator` the host rebinds to the live one on every (re)creation, which also
moves every call to the main thread, so a ViewModel can navigate from any dispatcher.
Reference: [NavigatorHandle.kt](../runtime/src/main/kotlin/dev/goose/runtime/NavigatorHandle.kt).

`nav/src/main/kotlin/yourapp/nav/NavigatorHandle.kt`

```kotlin
package yourapp.nav

class NavigatorHandle : Navigator {
    private val lock = Any()
    private var delegateField: Navigator? = null
    private val queued = ArrayDeque<(Navigator) -> Unit>()        // calls made while unbound
    private val delegateFlow = MutableStateFlow<Navigator?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The live navigator, or null between an unbind and the next bind. */
    val delegate: Navigator? get() = delegateFlow.value

    fun bind(navigator: Navigator) {
        val replay: List<(Navigator) -> Unit>
        synchronized(lock) {
            delegateField = navigator
            replay = queued.toList()
            queued.clear()
        }
        delegateFlow.value = navigator
        if (replay.isNotEmpty()) runOnMain { replay.forEach { it(navigator) } }
    }

    fun unbind(navigator: Navigator) {
        synchronized(lock) { if (delegateField === navigator) delegateField = null }
        delegateFlow.compareAndSet(navigator, null)
    }

    override val parent: Navigator? get() = delegate?.parent
    override val backStack: List<Screen> get() = delegate?.backStack ?: emptyList()

    override fun goTo(screen: Screen) = submit { it.goTo(screen) }
    override fun resetRoot(screen: Screen) = submit { it.resetRoot(screen) }

    /** Real answer only on the main thread while bound. Posted or queued pops report "accepted". */
    override fun pop(result: PopResult?): Boolean {
        synchronized(lock) { delegateField }?.let { d ->
            if (Looper.myLooper() == Looper.getMainLooper()) return d.pop(result)
        }
        submit { it.pop(result) }
        return true
    }

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? =
        withContext(Dispatchers.Main.immediate) {
            delegateFlow.filterNotNull().first().goToForResult(screen)   // waits out a recreation gap
        }

    private fun submit(op: (Navigator) -> Unit) {
        val d = synchronized(lock) { delegateField ?: run { queued.addLast(op); return } }
        runOnMain { op(d) }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
```

One consequence for PR 1's tree walk: a handle bound to a `StackHost` isn't itself a
`StackHost`, so `switchTo` must look through it before walking parents.

```diff
 fun Navigator.switchTo(key: StackKey): Navigator {
-    var nav: Navigator? = this
+    var nav: Navigator? = (this as? NavigatorHandle)?.delegate ?: this
     while (nav != null) {
```

Tests, `ResultCorrelationTest`, `BaseNavigatorTest`, and `NavigatorHandleTest` (reference:
[ResultCorrelationTest.kt](../runtime/src/test/kotlin/dev/goose/runtime/ResultCorrelationTest.kt)):

- `poppedResultResumesCaller`
- `plainPopResumesCallerWithNull`
- `nestedSameClassRequestsResolveLifo`
- `differentStackTagsNeverCrossDeliver`
- `cancelledCallerIsUnregisteredAndLaterAnswerResumesNobody`
- `throwingNavigationLeavesNoOrphanedAwaiter`
- `completeExactResolvesRightCallerOutOfOrder`
- `awaiterCompleteIsOneShot`
- `popAtRootBubblesToParent`
- `popAtRootWithNoParentReturnsFalse`
- `resultKeyIsClassPlusStackTag`
- `handleQueuesCallsWhileUnboundAndReplaysThemOnBind`
- `handlePostsOffMainCallsToTheMainThread`
- `handleGoToForResultWaitsForABindBeforeNavigating`
- `switchToThroughAHandleReachesTheHostItIsBoundTo`

---

## PR 3: A map from screen to composable

When the app is handed a `ProfileScreen`, something has to know that means "draw
`ProfileUi`." That something is a plain map: screen class in, composable out. This PR is
that map, plus the object that holds it. First Compose dependency (compose.runtime only).
Reference: [ScreenEntry.kt](../runtime/src/main/kotlin/dev/goose/runtime/ScreenEntry.kt),
[Goose.kt](../runtime-metro/src/main/kotlin/dev/goose/metro/Goose.kt).

**The value in the map.** It's a function that draws a screen:

`nav/src/main/kotlin/yourapp/nav/ScreenUi.kt`

```kotlin
package yourapp.nav

/** "Draw this screen." One of these per screen class, stored in the map. */
fun interface ScreenEntry {
    @Composable fun UntypedContent(screen: Screen, modifier: Modifier)
}
```

That takes a plain `Screen`, because the map holds every screen type together and can
only hand back the common type. Feature code shouldn't have to cast, so the library does
it once, in one helper, and feature code writes against the real type:

```kotlin
/** Typed on the outside, cast on the inside. Features write this, never ScreenEntry directly. */
inline fun <reified S : Screen> screenUi(
    crossinline content: @Composable (S, Modifier) -> Unit,
): ScreenEntry = ScreenEntry { s, m ->
    @Suppress("UNCHECKED_CAST") content(s as S, m)
}
```

The cast is safe because the map is keyed by class: an entry registered under
`ProfileScreen::class` is only ever handed a `ProfileScreen`. This lambda is the one way
to write an entry. (Goose also has a `ScreenUi<S>` abstract class for the same job, and
having two forms is a review-time argument in every feature PR. If a team wants
constructor injection on the entry for Metro's class-form contribution, add the class
then, as a thin wrapper over this lambda.)

Also in this file, how a composable finds its navigator without passing it through every
parameter list. The host sets it, screens read it:

```kotlin
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided. Host screens inside your screen host fragment or NavContent.")
}
```

**The map itself,** built once at startup and never changed:

`nav/src/main/kotlin/yourapp/nav/NavRuntime.kt`

```kotlin
package yourapp.nav

// Maps are keyed by KClass<*>, not KClass<out Screen>, to match what @ClassKey multibindings produce.
class NavRuntime private constructor(
    val entries: Map<KClass<*>, ScreenEntry>,
    val resultRouter: ResultRouter,        // PR 2's router lives here, one per app
) {
    class Builder {
        private val entries = mutableMapOf<KClass<*>, ScreenEntry>()
        private val bulk = mutableMapOf<KClass<*>, ScreenEntry>()

        /** Register one screen: addUi<ProfileScreen> { screen, modifier -> ProfileUi(...) } */
        inline fun <reified S : Screen> addUi(
            crossinline content: @Composable (S, Modifier) -> Unit,
        ): Builder = apply { addScreen(S::class, screenUi<S>(content)) }

        fun addScreen(screen: KClass<out Screen>, entry: ScreenEntry) =
            apply { entries[screen] = entry }

        /** Register many at once, for a map your DI collected. */
        fun addScreens(map: Map<KClass<*>, ScreenEntry>) = apply { bulk += map }

        // Explicit entries win over bulk ones, so an app can override a feature's screen.
        fun build() = NavRuntime(bulk + entries, ResultRouter())
    }
}
```

**DI bindings.** Each feature module registers its own screens through a multibinding:
every feature contributes one map entry keyed by screen class, DI collects them into one
map, and one provider hands that map to the builder. Three pieces, in three places.

The declaration, in the `nav` module, so the map exists even when no feature has
contributed yet:

```kotlin
package yourapp.nav

@ContributesTo(AppScope::class)                       // Metro. Dagger: a @Module with @Multibinds
interface NavMultibindings {
    @Multibinds(allowEmpty = true)
    val screenEntries: Map<KClass<*>, ScreenEntry>
}
```

The contribution, in each feature module:

```kotlin
// in feature :profile
@Provides @IntoMap @ClassKey(ProfileScreen::class)
fun profileUi(factory: ProfileViewModel.Factory): ScreenEntry =
    screenUi<ProfileScreen> { screen, modifier -> ProfileUi(screen, modifier, factory) }
```

(Metro's class form, `@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class) @Inject class ProfileUi(...) : ScreenEntry`, works too if
you add the class wrapper mentioned above. The explicit `binding` matters: Metro binds a
contribution as its direct supertype by default.)

The assembly, in `:app`, once:

```kotlin
@ContributesTo(AppScope::class)
interface NavRuntimeModule {
    companion object {
        @Provides @SingleIn(AppScope::class)          // Dagger: @Singleton
        fun navRuntime(entries: Map<KClass<*>, ScreenEntry>): NavRuntime =
            NavRuntime.Builder().addScreens(entries).build()
    }
}
```

The scope annotation is not optional. An unscoped provider builds a fresh `NavRuntime`
per injection point, and two `ResultRouter`s mean a result registered through one is
never delivered through the other.

Without DI, the same thing is a chain of `addUi` calls in `Application.onCreate`.

**Your existing annotations stay.** Every binding in this doc uses an annotation Dagger
already has under the same name, so a feature module written against Dagger contributes
without rewriting. The only names that differ are the ones that describe the graph itself:

| In this doc | Dagger | Metro |
|---|---|---|
| `@Provides @IntoMap @ClassKey(X::class)` | same, `dagger.multibindings` | same, `dev.zacsweers.metro` |
| `@Multibinds(allowEmpty = true)` | `@Multibinds` in a `@Module` | same, on a `@ContributesTo` interface |
| `@AssistedInject`, `@Assisted`, `@AssistedFactory` | same, `dagger.assisted` | same |
| `@ContributesTo(AppScope::class)` | a `@Module` listed on the component | same |
| `@SingleIn(AppScope::class)` | `@Singleton` | same |
| map type | `Map<Class<*>, @JvmSuppressWildcards T>` | `Map<KClass<*>, T>` |

Two things to know about the last row. Dagger's `@ClassKey` produces `Class` keys, so a
Dagger-provided map needs `.mapKeys { it.key.kotlin }` before `addScreens`, and Dagger
needs `@JvmSuppressWildcards` on the injected map or the binding won't match. Metro on
its Dagger interop setting (`enableDaggerRuntimeInterop`) reads Dagger's annotations
directly, which is how a module can stay on Dagger while the graph is Metro.

If the app graph is still Dagger and only the nav pieces are Metro, the Metro graph
`@Includes` the Dagger component, and every accessor on it becomes injectable into
ViewModels and entries. Nothing moves until you want it to:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @DependencyGraph.Factory
    fun interface Factory { fun create(@Includes legacy: LegacyComponent): AppGraph }
}
```

**Getting the built object to the activity.** The activity injects it through whatever
member injection the app already does (`@Inject lateinit var navRuntime: NavRuntime`, or
a graph accessor), and hands it to `installNavigator` in PR 4. Nothing is added to the
Application, and fragments never look it up themselves.

Tests, `NavRuntimeBuilderTest`:

- `lastExplicitRegistrationPerClassWins`
- `explicitRegistrationBeatsBulkRegardlessOfOrder`
- `unregisteredScreenFailsWithClassNameInMessage`

---

## PR 4: The old stack speaks the new interface

The centerpiece of phase 1: a `Navigator` over FragmentManager, and the invisible host
fragment that renders migrated screens on the legacy back stack. After this PR, a
ViewModel calling `navigator.goTo(screen)` neither knows nor cares whether the
destination is a fragment or a composable. Reference: the `runtime-fragment` module,
entry point
[InstallGooseNavigator.kt](../runtime-fragment/src/main/kotlin/dev/goose/fragment/InstallGooseNavigator.kt),
[FragmentNavigator.kt](../runtime-fragment/src/main/kotlin/dev/goose/fragment/FragmentNavigator.kt),
[ScreenFragment.kt](../runtime-fragment/src/main/kotlin/dev/goose/fragment/ScreenFragment.kt).

`nav/src/main/kotlin/yourapp/nav/ScreenFragmentBinder.kt`

```kotlin
package yourapp.nav

/** Legacy destinations: how to build the old fragment for a screen still unmigrated. */
fun interface ScreenFragmentBinder {
    fun createFragment(screen: Screen): Fragment
}
```

The one idea that makes this navigator short: **the back stack entry's name is the
result key.** Every push is `addToBackStack(resultKeyFor(screen))`. Then a single
`OnBackStackChangedListener` compares the entry names before and after each change, and
any name that disappeared gets its result delivered. That fires no matter who popped
(our `pop`, system back, or a legacy fragment calling `popBackStack()` on itself), so an
awaiting caller always resumes, and `pop` and `resetRoot` don't deliver anything
themselves.

`nav/src/main/kotlin/yourapp/nav/FragmentNavigator.kt`

```kotlin
package yourapp.nav

class FragmentNavigator(
    private val fragmentManager: FragmentManager,
    private val containerId: Int,
    private val binders: Map<KClass<*>, ScreenFragmentBinder>,
    private val screenHost: KClass<out Fragment>,      // your fragment, see below
    router: ResultRouter,
    parent: Navigator? = null,
    stackTag: String,
) : BaseNavigator(router, parent, stackTag) {

    /**
     * Intentionally empty. A restored FragmentManager holds entry names, not Screen
     * instances, so this host can't rebuild the list. Nothing mid-migration should
     * introspect the legacy stack anyway. The Compose host (PR 9) implements it for real.
     */
    override val backStack: List<Screen> get() = emptyList()

    override val isAtRoot: Boolean get() { flush(); return fragmentManager.backStackEntryCount == 0 }

    private var knownEntryNames: List<String?> = currentEntryNames()

    /** A result from pop(result), held until the listener sees that entry go away. */
    private var pendingResult: Pair<String, PopResult?>? = null

    init {
        fragmentManager.addOnBackStackChangedListener {
            val current = currentEntryNames()
            if (current.size < knownEntryNames.size) {
                // Something was popped. Deliver for every removed entry, top-down.
                // router.complete() no-ops when nobody is awaiting that key.
                for (i in knownEntryNames.size - 1 downTo current.size) {
                    val name = knownEntryNames[i] ?: continue
                    val pending = pendingResult
                    if (pending?.first == name) {
                        pendingResult = null
                        router.complete(name, pending.second)
                    } else {
                        router.complete(name, null)     // dismissed without answering
                    }
                }
            }
            knownEntryNames = current
        }
    }

    override fun push(screen: Screen) {
        val fragment = binders[screen::class]?.createFragment(screen)  // legacy screens
            ?: fragmentManager.instantiateHost(screenHost, screen)      // migrated screens
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment)
            addToBackStack(resultKeyFor(screen))       // the name IS the result key
        }
    }

    override fun popOwn(result: PopResult?): Boolean {
        flush()
        val count = fragmentManager.backStackEntryCount
        if (count == 0) return false                 // at the root: the base class bubbles up
        val name = fragmentManager.getBackStackEntryAt(count - 1).name
        if (name != null && result != null) pendingResult = name to result
        fragmentManager.popBackStack()               // the listener delivers pendingResult
        return true
    }

    override fun reset(screen: Screen) {
        // The listener delivers null to every awaited entry this clears.
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        push(screen)
    }

    /**
     * A push from this same main-loop turn is still queued. Flush it so "goTo(A); pop()"
     * pops A instead of seeing the stale pre-commit stack. Skip when the FM can't run
     * transactions (state saved) or we're already inside one (the queue stays ordered).
     */
    private fun flush() {
        if (fragmentManager.isStateSaved) return
        try { fragmentManager.executePendingTransactions() } catch (_: IllegalStateException) {}
    }

    private fun currentEntryNames(): List<String?> =
        (0 until fragmentManager.backStackEntryCount)
            .map { fragmentManager.getBackStackEntryAt(it).name }
}
```

Why `popOwn` doesn't call `deliverPopResult` directly: `popBackStack()` is asynchronous,
so delivering before the entry is actually gone would resume the caller while the popped
screen is still on screen. Parking the result in `pendingResult` and letting the listener
deliver it keeps "resumed" and "gone" in the same moment.

`goToAwaited` is not overridden in this PR. Stack-hosted destinations deliver by entry
name on pop, which is correct because the stack removes its newest same-class entry
first. PR 6 adds the override for destinations that bypass the stack.

**The host fragment is yours.** Every fragment app has a base fragment (lifecycle hooks,
analytics, a theme), and a migrated screen should ride it like any other. So there is no
built-in host: you write one small subclass of your base, and the library gives you one
helper that does all the wiring. That helper is the entire contract between the two.

`nav/src/main/kotlin/yourapp/nav/ScreenHost.kt`

```kotlin
package yourapp.nav

/**
 * The whole view for a migrated screen: reads the Screen from arguments, finds the
 * navigator and the registry, renders the entry. Call it from your host's onCreateView.
 * [wrap] composes your theme and chrome around the screen content.
 */
fun Fragment.screenView(
    wrap: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
): View {
    val screen = requireArguments().getSerializable(KEY_SCREEN) as Screen   // PR 1's contract
    val runtime = requireActivity().navRuntime      // stashed by installNavigator, see below
    val navigator = findNavigator()
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            wrap {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    val entry = runtime.entries[screen::class]
                        ?: error("No ScreenEntry for ${screen::class.qualifiedName}")
                    entry.UntypedContent(screen, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Nearest wins: a parent fragment owning a nested stack, else the activity's navigator. */
private fun Fragment.findNavigator(): Navigator {
    var parent = parentFragment
    while (parent != null) {
        if (parent is NavigatorOwner) return parent.navigator
        parent = parent.parentFragment
    }
    return requireActivity().appNavigator
}

interface NavigatorOwner { val navigator: Navigator }

/**
 * Creates the host through the FragmentManager's own FragmentFactory, the same path it
 * uses to recreate the fragment after rotation or process death, so a custom factory
 * (constructor injection, test doubles) sees hosts on push and on restore alike.
 */
internal fun FragmentManager.instantiateHost(host: KClass<out Fragment>, screen: Screen): Fragment =
    fragmentFactory.instantiate(host.java.classLoader!!, host.java.name)
        .apply { arguments = bundleOf(KEY_SCREEN to screen) }
```

In the app, once:

```kotlin
class AppScreenFragment : AppBaseFragment() {          // your base, your analytics
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        screenView { content -> AppTheme { content() } }
}
```

That class is the `screenHost` the navigator above takes in its constructor.

`nav/src/main/kotlin/yourapp/nav/InstallNavigator.kt`

```kotlin
/**
 * One call in the activity, with the NavRuntime the activity injected. Builds a
 * FragmentNavigator over this activity's FragmentManager, stashes both in the activity's
 * retained holder, and exposes them afterwards as activity.appNavigator and
 * activity.navRuntime.
 */
fun FragmentActivity.installNavigator(
    containerId: Int,
    screenHost: KClass<out Fragment>,                                  // required
    runtime: NavRuntime,
): Navigator {
    val holder = ViewModelProvider(this)[ActivityNavHolder::class.java]   // survives rotation
    holder.runtime = runtime
    val navigator = FragmentNavigator(
        fragmentManager = supportFragmentManager,
        containerId = containerId,
        binders = runtime.fragmentBinders,
        screenHost = screenHost,
        router = runtime.resultRouter,
        stackTag = holder.stackTag,
    )
    holder.handle.bind(navigator)              // the NavigatorHandle ViewModels hold, see PR 5
    onBackPressedDispatcher.addCallback(this) { if (!navigator.pop()) finish() }
    return navigator
}

/** Per activity, in its ViewModelStore: outlives the navigator, dies with the activity. */
internal class ActivityNavHolder : ViewModel() {
    val stackTag: String = UUID.randomUUID().toString()   // result keys stay stable across rotation
    val handle = NavigatorHandle()
    lateinit var runtime: NavRuntime
}

val FragmentActivity.appNavigator: Navigator get() = ViewModelProvider(this)[ActivityNavHolder::class.java].handle
val FragmentActivity.navRuntime: NavRuntime get() = ViewModelProvider(this)[ActivityNavHolder::class.java].runtime
```

In the activity, that's all of it:

```kotlin
class MainActivity : AppBaseActivity() {
    @Inject lateinit var navRuntime: NavRuntime          // your existing member injection
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)            // your existing layout
        installNavigator(R.id.fragment_container, AppScreenFragment::class, navRuntime)
    }
}
```

**Saved state and R8.** The screen in the arguments Bundle is Java-serialized, and that
Bundle is what FragmentManager restores after process death, including after an app
update. Java serialization resolves by class and field *name*. If R8 renames a screen or
one of its fields between two releases, restoring a Bundle saved by the old release throws
on the main thread on every launch until the user clears data. That's the crash loop PR 11
prevents on the Compose side, and phase 1 needs the equivalent from day one. Two rules in
the module's consumer proguard:

```
-keepnames class * implements yourapp.nav.Screen
-keepclassmembernames class * implements yourapp.nav.Screen { <fields>; }
```

And a fail-soft in `screenView`, so a screen that still fails to decode pops instead of
crash-looping:

```diff
-    val screen = requireArguments().getSerializable(KEY_SCREEN) as Screen   // PR 1's contract
+    val screen = runCatching { requireArguments().getSerializable(KEY_SCREEN) as Screen }
+        .getOrElse {
+            requireActivity().appNavigator.pop()      // a stale entry after an update
+            return View(requireContext())
+        }
```

**Back presses.** `installNavigator` registers its `OnBackPressedCallback` in `onCreate`.
A callback an existing fragment registers later with its own lifecycle owner takes
priority while that fragment is resumed, which is the usual intent, so legacy fragments
that intercept back keep working. Predictive back animates fragment transitions once
`android:enableOnBackInvokedCallback="true"` is set. Nothing in this module changes for it.

**Warm deep links** are a navigator call from `onNewIntent`:
`appNavigator.goTo(OrderScreen(id))`, or `appNavigator.switchTo(Tabs.Orders).goTo(...)`
once PR 7 lands. Cold-start deep links on the fragment stack stay whatever the app does
today, since the root is still a fragment.

**DI bindings.** Binders are the second multibinding, same three pieces as PR 3. The
builder and runtime grow one map:

```diff
 class NavRuntime private constructor(
     val entries: Map<KClass<*>, ScreenEntry>,
+    val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder>,
     val resultRouter: ResultRouter,
 ) {
     class Builder {
+        private val binders = mutableMapOf<KClass<*>, ScreenFragmentBinder>()
+        fun addFragmentBinder(screen: KClass<out Screen>, binder: ScreenFragmentBinder) =
+            apply { binders[screen] = binder }
+        fun addFragmentBinders(map: Map<KClass<*>, ScreenFragmentBinder>) =
+            apply { binders += map }
```

The declaration joins `NavMultibindings`:

```kotlin
    @Multibinds(allowEmpty = true)
    val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder>
```

A feature contributes a binder for each of its screens that is still a fragment:

```kotlin
// in feature :followers, unmigrated
@Provides @IntoMap @ClassKey(FollowersScreen::class)
fun followersBinder(): ScreenFragmentBinder = ScreenFragmentBinder { screen ->
    FollowersFragment.newInstance((screen as FollowersScreen).userId)
}
```

And the `:app` provider passes the collected map through:

```diff
         fun navRuntime(
             entries: Map<KClass<*>, ScreenEntry>,
+            binders: Map<KClass<*>, ScreenFragmentBinder>,
         ): NavRuntime = NavRuntime.Builder()
             .addScreens(entries)
+            .addFragmentBinders(binders)
             .build()
```

When a destination migrates, its feature deletes the binder provider and adds a
`ScreenEntry` provider for the same screen class. No caller changes.

(Goose itself ships a default `ScreenFragment` and makes `screenHost` optional. That's a
convenience for the sample and the docs, not a design requirement, and one required
parameter is the simpler API.)

Tests, `FragmentNavigatorTest` (Robolectric):

- `goToMigratedScreenCommitsTheRegisteredScreenHost`
- `screenHostIsCreatedThroughTheFragmentFactory`
- `goToLegacyScreenUsesItsBinder`
- `systemBackPopsAndDeliversNull`
- `legacyPopBackStackResumesAwaitingCallerWithNull`
- `goToThenPopInOneTurnPopsTheJustPushedScreen`
- `goToForResultRoundTripsAcrossTheFragmentBoundary`
- `resultKeysSurviveActivityRecreation`
- `screenViewFindsTheNearestNavigatorOwnerBeforeTheActivity`
- `undecodableScreenArgumentsPopInsteadOfCrashing`
- `legacyFragmentBackCallbackStillWins`

---

## PR 5: A screen's ViewModel lives exactly as long as its entry

One helper, `screenViewModel`, that a migrated composable calls to get its Mavericks
ViewModel. The rules it guarantees, which phase 2 must reproduce bit for bit (reference:
[VIEWMODEL_CONTRACT.md](VIEWMODEL_CONTRACT.md)):

| Event | ViewModel |
|---|---|
| Recomposition | same instance |
| Recreation | same instance, retained |
| Entry popped, any way | cleared, saved-state hooks unregistered |
| Process death | new instance, `@PersistState` fields restored |

Your ViewModels don't change. The screen doubles as the args (that's why PR 1 made
`Screen` java-Serializable), so the `constructor(screen: ProfileScreen)` state convention
and `@PersistState` ride Mavericks' own machinery. What's new is two assisted parameters
and how the navigator gets in. Reference:
[ScreenViewModel.kt](../runtime-mavericks/src/main/kotlin/dev/goose/mavericks/ScreenViewModel.kt),
[GooseVmFactory.kt](../runtime-mavericks/src/main/kotlin/dev/goose/mavericks/GooseVmFactory.kt).

What a feature writes:

```kotlin
@AssistedInject
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,
    private val repo: ProfileRepository,
) : MavericksViewModel<ProfileState>(initialState) {
    @AssistedFactory fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }
    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
        by screenVmFactory(ProfileViewModel::class)
}

// in the ScreenEntry registered in PR 3
screenUi<ProfileScreen> { screen, modifier ->
    val vm = screenViewModel<ProfileViewModel, ProfileState>(screen, factory::create)
    val state by vm.collectAsState()
    ProfileUi(state, vm, modifier)
}
```

**The problem the helper solves.** Mavericks creates ViewModels through the companion
`MavericksViewModelFactory.create(viewModelContext, state)`. That signature is fixed, and
it has no slot for a navigator or for your assisted factory. So the helper parks both in a
ThreadLocal for the duration of one synchronous, main-thread `MavericksViewModelProvider.get`
call, and the companion reads them back out. It's a handoff, not a design pattern, and it's
fully contained in these two files.

`nav/src/main/kotlin/yourapp/nav/ScreenVmFactory.kt`

```kotlin
package yourapp.nav

/** What screenViewModel parks for the companion to read. Alive only during one get() call. */
internal object VmCreationScope {
    class Scope(val navigator: Navigator, val create: (MavericksState, Navigator) -> MavericksViewModel<*>)
    private val local = ThreadLocal<Scope?>()
    val current: Scope? get() = local.get()

    fun <T> with(scope: Scope, block: () -> T): T {
        check(local.get() == null) { "Nested screen ViewModel creation is not supported." }
        local.set(scope)
        return try { block() } finally { local.remove() }
    }
}

/** The companion every screen ViewModel delegates to. */
fun <VM : MavericksViewModel<S>, S : MavericksState> screenVmFactory(
    vmClass: KClass<VM>,
): MavericksViewModelFactory<VM, S> = object : MavericksViewModelFactory<VM, S> {
    override fun create(viewModelContext: ViewModelContext, state: S): VM {
        val scope = checkNotNull(VmCreationScope.current) {
            "${vmClass.simpleName} was created outside screenViewModel()."
        }
        @Suppress("UNCHECKED_CAST")
        return scope.create(state, scope.navigator) as VM
    }
}
```

`nav/src/main/kotlin/yourapp/nav/ScreenViewModel.kt`

```kotlin
package yourapp.nav

/** Retained next to the VM. Forwards to whichever navigator is live right now. */
internal class NavigatorHandleHolder : ViewModel() { val handle = NavigatorHandle() }

/** Runs when the entry's store clears, so a popped screen's saved-state hook doesn't leak. */
internal class CleanupHolder : ViewModel() {
    var onCleared: (() -> Unit)? = null
    override fun onCleared() { onCleared?.invoke() }
}

@Composable
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> screenViewModel(
    screen: Screen,
    noinline create: (initialState: S, navigator: Navigator) -> VM,
): VM {
    val navigator = LocalNavigator.current
    val activity = LocalContext.current.findComponentActivity()!!
    val storeOwner = LocalViewModelStoreOwner.current!!     // the host fragment, for now
    val registryOwner = LocalSavedStateRegistryOwner.current

    // Stable per-entry id. rememberSaveable is scoped to the entry, so it survives
    // recreation and process death without depending on instance identity.
    val entryId = rememberSaveable { UUID.randomUUID().toString() }
    val key = "${VM::class.java.name}:$entryId"

    // The VM outlives any one navigator instance (recreation builds a new FragmentNavigator),
    // so it holds a handle that is rebound to the live navigator on every composition.
    val handleHolder = viewModel<NavigatorHandleHolder>(storeOwner, key = "nav:handle")
    DisposableEffect(navigator) {
        handleHolder.handle.bind(navigator)
        onDispose { handleHolder.handle.unbind(navigator) }
    }

    val cleanup = viewModel<CleanupHolder>(storeOwner, key = "nav:cleanup:$key")
    SideEffect {
        val registry = WeakReference(registryOwner.savedStateRegistry)
        cleanup.onCleared = { registry.get()?.unregisterSavedStateProvider(key) }
    }

    return remember(screen, entryId) {
        @Suppress("UNCHECKED_CAST")
        val erased = create as (MavericksState, Navigator) -> MavericksViewModel<*>
        VmCreationScope.with(VmCreationScope.Scope(handleHolder.handle, erased)) {
            MavericksViewModelProvider.get(
                viewModelClass = VM::class.java,
                stateClass = S::class.java,
                viewModelContext = ActivityViewModelContext(
                    activity = activity,
                    args = screen,                          // the screen IS the args
                    owner = storeOwner,
                    savedStateRegistry = registryOwner.savedStateRegistry,
                ),
                key = key,
            )
        }
    }
}
```

The per-entry `NavigatorHandle` is PR 2's class doing its job one level down: the
activity's handle from PR 4 survives rotation, but `LocalNavigator` inside a nested
owner (a tab, a flow) may be a fresh object after recreation, so each entry rebinds its
own. `findComponentActivity()` is the usual walk up `ContextWrapper.baseContext` until a
`ComponentActivity` turns up.

On the fragment stack the lifetime table is free: the host fragment is the
`ViewModelStoreOwner`, retained across rotation and cleared on pop, and FragmentManager
persists the screen in the arguments Bundle. Phase 2's Nav3 host has to provide an
equivalent store per entry, which is one line there.

**If your ViewModels already use the factory-map pattern.** Many Mavericks + Dagger apps
resolve factories through a multibound map instead of injecting each one: every ViewModel
has an `AssistedViewModelFactory` contributed under `@ViewModelKey(X::class)`, and a shared
companion looks its own factory up in `Map<Class<out MavericksViewModel<*>>, AssistedViewModelFactory<*, *>>`.
You don't have to unwind that to adopt this. Two changes:

The existing factory interface gains the navigator parameter, and the ViewModel's
companion becomes the `screenVmFactory` line. That replaces the old shared companion
rather than sitting next to it:

```diff
 interface AssistedViewModelFactory<VM : MavericksViewModel<S>, S : MavericksState> {
-    fun create(initialState: S): VM
+    fun create(initialState: S, navigator: Navigator): VM
 }

 @AssistedInject
 class ProfileViewModel(
     @Assisted initialState: ProfileState,
+    @Assisted private val navigator: Navigator,
     private val repo: ProfileRepository,
 ) : MavericksViewModel<ProfileState>(initialState) {
     @AssistedFactory
     interface Factory : AssistedViewModelFactory<ProfileViewModel, ProfileState> {
-        override fun create(initialState: ProfileState): ProfileViewModel
+        override fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
     }
-    companion object : MvRxViewModelFactory<ProfileViewModel, ProfileState> by daggerMavericksViewModelFactory()
+    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
+        by screenVmFactory(ProfileViewModel::class)
 }
```

The entry provider injects the map and looks the factory up, instead of injecting the
factory directly:

```kotlin
@Provides @IntoMap @ClassKey(ProfileScreen::class)
fun profileUi(
    factories: Map<Class<out MavericksViewModel<*>>, @JvmSuppressWildcards AssistedViewModelFactory<*, *>>,
): ScreenEntry = screenUi<ProfileScreen> { screen, modifier ->
    val factory = factories.getValue(ProfileViewModel::class.java) as ProfileViewModel.Factory
    val vm = screenViewModel<ProfileViewModel, ProfileState>(screen, factory::create)
    ...
}
```

Unmigrated ViewModels keep the old shared companion and are still created by
`fragmentViewModel()`. That companion is the one place that has to learn about the
navigator, since the widened `create` now needs one:

```diff
 // the existing shared companion, used by every unmigrated ViewModel
 fun <VM, S> daggerMavericksViewModelFactory() = object : MavericksViewModelFactory<VM, S> {
     override fun create(viewModelContext: ViewModelContext, state: S): VM {
         val factory = viewModelContext.activity.component.viewModelFactories.getValue(vmClass)
-        return factory.create(state) as VM
+        return factory.create(state, viewModelContext.activity.appNavigator) as VM   // PR 4's install
     }
 }
```

That's a side benefit rather than a cost: an unmigrated ViewModel can start navigating
by screen (`navigator.goTo(ProfileScreen(id))`) before its own UI moves, so legacy call
sites migrate independently of legacy screens. The remaining cost is the extended
`create` signature on every existing factory, which is a mechanical find-and-replace.
The direct-injection form shown above is smaller per screen and is the better end state,
so new ViewModels should use it, and the map can shrink as screens migrate.

Tests, `ScreenViewModelTest` (Robolectric, recreate via `ActivityScenario.recreate()`):

- `sameInstanceAcrossRecomposition`
- `sameInstanceAcrossRecreation`
- `clearedOnPop`
- `clearedOnSystemBack`
- `savedStateProviderUnregisteredOnClear`
- `navigatorHandleReboundAfterRecreation`
- `awaitingGoToForResultSurvivesRecreation`
- `persistStateRestoredAfterProcessDeath`
- `creatingOutsideScreenViewModelFailsWithActionableMessage`

**Milestone: migration starts here.** One screen per PR from now on: add the screen class
to `:api`, register its `ScreenEntry`, delete the fragment, register binders for any
unmigrated destinations it navigates to.

---

## PR 6: One screen, your transaction

PR 4's navigator knows exactly one transaction: replace the container and push. Real
migrations have screens that don't fit it: a dialog, a bottom sheet, a screen that hands
off to your existing router, an activity. The navigator does not grow cases for these.
Instead it gains one hook, keyed by screen class, that hands the whole navigation to you.
Reference: `FragmentScreenNavigation` in
[FragmentNavigator.kt](../runtime-fragment/src/main/kotlin/dev/goose/fragment/FragmentNavigator.kt).

```kotlin
/** Per-screen override of how a navigation executes. Registered by screen class. */
fun interface FragmentScreenNavigation {
    fun navigate(request: FragmentNavigationRequest)
}

class FragmentNavigationRequest internal constructor(
    val screen: Screen,
    val fragmentManager: FragmentManager,
    val containerId: Int,
    /** If you DO push onto the back stack, use this name, so the PR 4 listener delivers on pop. */
    val backStackEntryName: String,
    private val awaiter: ResultAwaiter?,        // null for a plain goTo: nobody is waiting
) {
    /** If you DON'T push (a callback dialog, an activity), answer the caller directly. */
    fun deliverResult(result: PopResult?) { awaiter?.complete(result) }
}
```

There are two ways an override can answer a `goToForResult` caller, and the request
carries both:

- **Push onto the back stack under `backStackEntryName`.** Then the listener from PR 4
  delivers on pop, same as any screen. This is the dialog case:
  `DialogFragment.show(transaction, tag)` records the back stack entry itself, so tapping
  outside, system back, and `navigator.pop` all pop the named entry.
- **Skip the back stack and call `deliverResult`.** The listener never sees these pop, so
  they answer directly. `deliverResult` resumes exactly this request's caller, one-shot,
  so two same-class dialogs answering out of order each resolve their own caller.

A dialog screen in the app, using the first way and the same `screenView` helper as PR 4:

```kotlin
class AppScreenDialogFragment : AppBaseDialogFragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        screenView { content -> AppTheme { content() } }
}

// registered under PickPlanScreen::class
class PickPlanNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        val dialog = request.fragmentManager.instantiateHost(AppScreenDialogFragment::class, request.screen)
        val tx = request.fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(request.backStackEntryName)
        (dialog as DialogFragment).show(tx, request.backStackEntryName)
    }
}
```

`FragmentNavigator` diff: a `navigationOverrides: Map<KClass<*>, FragmentScreenNavigation>`
constructor parameter, and `goToAwaited` gets its override so the awaiter reaches the
request. A plain `goTo` passes null: that request has no caller, even if an older
same-class request is still waiting somewhere.

```diff
-    override fun push(screen: Screen) {
+    override fun push(screen: Screen) = navigate(screen, awaiter = null)
+
+    override fun goToAwaited(screen: Screen, awaiter: ResultAwaiter) = navigate(screen, awaiter)
+
+    private fun navigate(screen: Screen, awaiter: ResultAwaiter?) {
+        navigationOverrides[screen::class]?.let { override ->
+            override.navigate(FragmentNavigationRequest(
+                screen, fragmentManager, containerId, resultKeyFor(screen), awaiter,
+            ))
+            return
+        }
         val fragment = binders[screen::class]?.createFragment(screen)
```

The rest of the old `push` body moves into `navigate` unchanged. `instantiateHost` from
PR 4 becomes public so overrides can create hosts through the FragmentFactory too.

**DI bindings.** One more map, same three pieces as PR 3.

```kotlin
// in NavMultibindings
    @Multibinds(allowEmpty = true)
    val navigationOverrides: Map<KClass<*>, FragmentScreenNavigation>

// a one-off screen, in its feature module
@Provides @IntoMap @ClassKey(PickPlanScreen::class)
fun pickPlanNavigation(): FragmentScreenNavigation = PickPlanNavigation()
```

The builder gains `addNavigationOverrides(map)`, the `:app` provider passes it through
exactly as PR 4 did for binders, and `installNavigator` reads it off the runtime.

(Goose ships an `OverlayScreen` marker and a built-in dialog transaction for it. On the
fragment side that's convenience over exactly this hook, and one hook is the simpler API.
The Compose host gets its own dialog story in PR 13, where Nav3 needs a marker to pick
the dialog scene.)

Tests, `NavigationOverrideTest` and `FragmentRequestCorrelationTest`:

- `overrideWinsOverBinderAndDefaultTransaction`
- `overridePushingUnderBackStackEntryNameDeliversOnPop`
- `dialogDismissDeliversNullToAwaitingCaller`
- `deliverResultResumesExactlyThisCaller`
- `twoSameClassDialogsAnsweringOutOfOrderEachResolveTheirOwnCaller`
- `deliverResultIsOneShot`
- `plainGoToThroughOverrideAnswersNobody`

---

## PR 7: Many stacks, one host, on fragments

On the fragment host there is no list object: the `FragmentManager` is the stack. So
"several stacks" means several FragmentManagers, and a fragment app with bottom nav
already has them, usually one host fragment per tab, each with its own
`childFragmentManager`, shown and hidden in the activity's container. This PR wraps that
pattern in PR 1's `StackHost`, so a ViewModel can
`navigator.switchTo(Tabs.Profile).goTo(...)` today, before any Compose stack exists, and
the same call keeps working after the flip. (Goose has no fragment tab host, this PR is
new work.)

First the base class, host-free, that any multi-stack host extends. An implementation
answers two questions, which navigator owns a key and how to make a key visible, and
inherits the delegation of every call to the current stack plus `switchTo`'s contract.

`nav/src/main/kotlin/yourapp/nav/BaseStackHost.kt`

```kotlin
package yourapp.nav

abstract class BaseStackHost(
    router: ResultRouter,
    parent: Navigator?,
) : BaseNavigator(router, parent, stackTag = "host"), StackHost {

    /** The navigator owning [key]'s stack. Create on first use. Its parent must be this host. */
    protected abstract fun navigatorFor(key: StackKey): BaseNavigator

    /** Make [key]'s stack the visible one. Must NOT push a back stack entry anywhere. */
    protected abstract fun show(key: StackKey)

    abstract override var currentStack: StackKey
        protected set

    private val current: BaseNavigator get() = navigatorFor(currentStack)

    final override fun switchTo(key: StackKey): Navigator {
        requireMainThread()
        require(key in stacks) { "$key is not a stack of this host" }
        if (key != currentStack) {
            show(key)
            currentStack = key
        }
        return this
    }

    // Every Navigator call is the current stack's. A host owns no entries of its own.
    final override val backStack: List<Screen> get() = current.backStack
    final override val isAtRoot: Boolean get() = current.isAtRoot
    final override fun push(screen: Screen) = current.goTo(screen)
    final override fun reset(screen: Screen) = current.resetRoot(screen)
    final override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? =
        current.goToForResult(screen)

    /** Pop the current stack. At ITS root, return false so the base bubbles to this host's parent. */
    final override fun popOwn(result: PopResult?): Boolean =
        if (current.isAtRoot) false else current.pop(result)
}
```

Why `popOwn` checks `isAtRoot` instead of just calling `current.pop`: a stack at its root
bubbles to its parent, which is this host, whose `pop` would pop the current stack, which
is at its root. The check breaks that loop.

Then the fragment implementation:

`nav/src/main/kotlin/yourapp/nav/FragmentTabNavigator.kt`

```kotlin
package yourapp.nav

data class TabSpec(val key: StackKey, val root: Screen)

/** A plain container per tab. Implements NavigatorOwner so screenView finds its tab's navigator. */
class TabContainerFragment : Fragment(), NavigatorOwner {
    override lateinit var navigator: Navigator
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        FragmentContainerView(requireContext()).apply { id = R.id.tab_container }
}

class FragmentTabNavigator(
    private val activityFragmentManager: FragmentManager,
    private val containerId: Int,
    private val tabs: List<TabSpec>,
    private val binders: Map<KClass<*>, ScreenFragmentBinder>,
    private val overrides: Map<KClass<*>, FragmentScreenNavigation>,
    private val screenHost: KClass<out Fragment>,
    router: ResultRouter,
    parent: Navigator? = null,
    initialStack: StackKey,           // the retained current tab on recreation, see below
) : BaseStackHost(router, parent) {

    override val stacks: Set<StackKey> = tabs.map { it.key }.toSet()
    override var currentStack: StackKey = initialStack
        protected set

    private val perTab = HashMap<StackKey, FragmentNavigator>()

    private fun tabFragment(key: StackKey): TabContainerFragment =
        activityFragmentManager.findFragmentByTag(key.value) as? TabContainerFragment
            ?: TabContainerFragment().also { fragment ->
                activityFragmentManager.commitNow { add(containerId, fragment, key.value); hide(fragment) }
            }

    /** One FragmentNavigator per tab, over that tab's childFragmentManager, parented to this. */
    override fun navigatorFor(key: StackKey): BaseNavigator = perTab.getOrPut(key) {
        val fragment = tabFragment(key)
        FragmentNavigator(
            fragmentManager = fragment.childFragmentManager,
            containerId = R.id.tab_container,
            binders = binders,
            screenHost = screenHost,
            navigationOverrides = overrides,
            router = router,
            parent = this,                      // a pop at the tab's root bubbles here
            stackTag = "tab:${key.value}",      // results never cross tabs
        ).also { nav ->
            fragment.navigator = nav
            if (fragment.childFragmentManager.fragments.isEmpty()) {
                // Root is a plain replace, NOT addToBackStack: at the root the child stack
                // is empty, so isAtRoot is true and a pop bubbles up here instead of
                // emptying the tab.
                val root = tabs.first { it.key == key }.root
                fragment.childFragmentManager.commitNow {
                    replace(R.id.tab_container, fragment.childFragmentManager.instantiateHost(screenHost, root))
                }
            }
        }
    }

    /** No back stack entry: switching is not a push, so system back never undoes it. */
    override fun show(key: StackKey) {
        navigatorFor(key)                                 // ensure its root is committed
        activityFragmentManager.commitNow {
            hide(tabFragment(currentStack))
            show(tabFragment(key))
        }
    }

    init { show(initialStack) }   // first install: commit and show the initial tab's root
}
```

That's the whole class: which navigator owns a key, and how to show a key. Delegation to
the current tab, `switchTo`, and the root-pop bubble are inherited from `BaseStackHost`.

**Bring your own stacks.** If the app already has a tab manager, a nested-stack framework,
or Nav2 with `saveState`/`restoreState`, don't use this class. Subclass `BaseStackHost`
yourself, answer the same two questions over your machinery, and every rule above holds
by inheritance. The contract tests in PRs 2 and 7 should run against your subclass. For
a single legacy flow that manages its own child back stack, the smaller move is a fragment
implementing `NavigatorOwner` with its own `FragmentNavigator`, so every migrated screen
beneath it uses that one.

**Stacks within stacks.** A tab is a nested stack that never pops, and the same shape is
a nested flow: a fragment that owns a `childFragmentManager`, builds its own
`FragmentNavigator` over it with `parent = findNavigator()`, and implements
`NavigatorOwner` so `screenView` beneath it finds the inner one. Steps push onto the inner
stack. A pop at the inner root bubbles out through `parent` and takes the flow with it.
If the flow screen is a `ScreenWithResult`, the last step's `pop(summary)` at the inner
root travels the same path and answers the outer caller, which never knew there were
steps. `switchTo` from a step walks up through both levels. Nothing in the contract
distinguishes a tab from a flow, and the inner stack tag keeps results inside the flow
from colliding with results outside it.

`InstallNavigator.kt` gains the tabbed form:

```kotlin
fun FragmentActivity.installTabNavigator(
    containerId: Int,
    screenHost: KClass<out Fragment>,
    runtime: NavRuntime,
    tabs: List<TabSpec>,
    initialStack: StackKey = tabs.first().key,
): StackHost
```

Your bottom bar stays your own view. It reads `currentStack` and calls `switchTo`.
`installTabNavigator` keeps the current key in `ActivityNavHolder` (updated by a listener
on the host) and passes it back as `initialStack` on recreation, and the tab fragments
themselves are restored by the FragmentManager, so recreation lands on the same tab with
every stack intact.

Tests, `FragmentTabNavigatorTest` (Robolectric):

- `goToPushesOntoCurrentTabOnly`
- `switchToPreservesTheTabBeingLeft`
- `switchToAlreadyCurrentIsNoOp`
- `switchToThenGoToLandsOnTheTargetTab`
- `switchToFromScreenInsideATabWalksToTheHost`
- `popAtTabRootBubblesToHostAndReturnsFalse`
- `systemBackNeverUndoesATabSwitch`
- `sameScreenClassAwaitedInTwoTabsDeliversToTheRightTab`
- `screenViewInsideATabFindsThatTabsNavigator`
- `allTabsAndCurrentTabSurviveRecreation`
- `stackHostDelegatesGoToPopAndResultsToCurrentStack`
- `stackHostPopBubblesToItsParentOnlyWhenCurrentStackIsAtRoot`
- `stackHostSwitchToReturnsItselfAndSkipsShowWhenAlreadyCurrent`
- `nestedFlowPopAtInnerRootAnswersTheOuterCaller`

---

## PR 8: Presentations (optional, add when you have a family of screens that share a look)

Ten bottom sheets should not need ten copies of `PickPlanNavigation`. A screen can
instead say *which* shared presentation it uses, and the PR 6 override binds once per
presentation type. Skip this PR until you have the ten bottom sheets. Reference:
[Presentation.kt](../runtime/src/main/kotlin/dev/goose/runtime/Presentation.kt).

```kotlin
/** A named way of appearing (bottom sheet, full-screen modal). An app-defined object. */
interface Presentation

/**
 * A screen that appears through a shared Presentation. Declare it as a getter: it is
 * behavior, not state, and a backing field would be serialized with the screen.
 */
interface PresentedScreen : Screen {
    val presentation: Presentation
}
```

```kotlin
// design-system module, defined once
object BottomSheet : Presentation

// any :api module
data class HelpScreen(val topic: String) : PresentedScreen {
    override val presentation get() = BottomSheet
}

// registered once under BottomSheet::class, covers every screen pointing at it
class BottomSheetNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        val sheet = request.fragmentManager.instantiateHost(AppBottomSheetFragment::class, request.screen)
        (sheet as BottomSheetDialogFragment).show(
            request.fragmentManager.beginTransaction().addToBackStack(request.backStackEntryName),
            request.backStackEntryName,
        )
    }
}
```

Precedence in `FragmentNavigator.navigate`, most specific first: an override for the
screen's own class, then one for its presentation's class, then the default transaction.
A screen can always take back control from its presentation by registering its own
override.

```diff
     private fun navigate(screen: Screen, awaiter: ResultAwaiter?) {
-        navigationOverrides[screen::class]?.let { override ->
+        val override = navigationOverrides[screen::class]
+            ?: (screen as? PresentedScreen)?.presentation?.let { presentationNavigations[it::class] }
+        if (override != null) {
             override.navigate(FragmentNavigationRequest(
                 screen, fragmentManager, containerId, resultKeyFor(screen), awaiter,
             ))
             return
         }
```

`presentationNavigations: Map<KClass<*>, FragmentScreenNavigation>` is a new constructor
parameter on `FragmentNavigator`, threaded through `FragmentTabNavigator` and
`installNavigator` the same way the overrides map is.

A presentation is a plain object, so a data-class presentation can carry knobs
(`BottomSheet(peekHeight = 200.dp)`), and the override reads them off
`(request.screen as PresentedScreen).presentation`. The binding still keys on the class.

**DI bindings.** The screen-keyed and presentation-keyed maps have the same type,
`Map<KClass<*>, FragmentScreenNavigation>`, and differ only in what the key means, so this
one is qualified.

```kotlin
@Qualifier @Retention(AnnotationRetention.RUNTIME)
annotation class PresentationNavigations

// in NavMultibindings
    @Multibinds(allowEmpty = true) @PresentationNavigations
    val presentationNavigations: Map<KClass<*>, FragmentScreenNavigation>

// in the design-system module
@Provides @IntoMap @ClassKey(BottomSheet::class) @PresentationNavigations
fun bottomSheetNavigation(): FragmentScreenNavigation = BottomSheetNavigation()
```

The builder gains `addPresentationNavigations(map)` and the `:app` provider passes it
through. In phase 2, PR 13 gives the same `BottomSheet` object its Compose behavior, so
one definition covers both hosts.

Tests, `PresentationTest`:

- `presentationNavigationCoversEveryScreenPointingAtIt`
- `screenOverrideBeatsPresentationNavigation`
- `presentationInstanceKnobsReachTheRequest`

---

# Phase 2: the Compose-owned stack

Land these when the first whole flow is migrated. The payoff claim for the phase: swap a
container's `installNavigator` for `NavContent(rememberNavBackStack(HomeScreen))` and no
screen code changes, because everything below implements interfaces reviewed in phase 1.

## PR 9: The back stack is a list

A navigator over a Nav3 back stack. Add the navigation3 dependencies. `Screen` gains
`: NavKey`, and concrete screens gain kotlinx `@Serializable` (used by PR 11). Reference:
[Nav3Navigator.kt](../runtime-nav3/src/main/kotlin/dev/goose/nav3/Nav3Navigator.kt).

`nav/src/main/kotlin/yourapp/nav/StackNavigator.kt`

```kotlin
package yourapp.nav

/** Mutating the list IS the navigation. [stackTag] must be stable across recreation. */
class StackNavigator(
    private val stack: MutableList<NavKey>,          // a NavBackStack from rememberNavBackStack
    router: ResultRouter,
    parent: Navigator? = null,
    stackTag: String,
) : BaseNavigator(router, parent, stackTag) {

    override val backStack: List<Screen> get() = stack.map { it as Screen }
    override val isAtRoot: Boolean get() = stack.size <= 1

    override fun push(screen: Screen) {
        stack.add(screen)
    }

    override fun popOwn(result: PopResult?): Boolean {
        if (isAtRoot) return false               // the base class bubbles to whoever pushed this stack
        deliverPopResult(stack.removeAt(stack.lastIndex) as Screen, result)
        return true
    }

    override fun reset(screen: Screen) {
        stack.asReversed().forEach { deliverPopResult(it as Screen, null) }  // nobody hangs
        stack.clear()
        stack.add(screen)
    }
}
```

`nav/src/main/kotlin/yourapp/nav/NavContent.kt`

```kotlin
package yourapp.nav

/** Set once at the root. A pure-Compose activity provides it in setContent; screenView provides it too. */
val LocalNavRuntime = staticCompositionLocalOf<NavRuntime> { error("No NavRuntime provided.") }

@Composable
fun NavContent(
    backStack: NavBackStack<NavKey>,                 // rememberNavBackStack(rootScreen)
    modifier: Modifier = Modifier,
    parent: Navigator? = null,
) {
    val runtime = LocalNavRuntime.current
    val stackTag = rememberSaveable { UUID.randomUUID().toString() }
    val navigator = remember(backStack) {
        StackNavigator(backStack, runtime.resultRouter, parent, stackTag)
    }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),   // makes PR 5's table hold here
            ),
            entryProvider = { key ->
                val screen = key as Screen
                NavEntry(key) {
                    val entry = runtime.entries[screen::class]
                        ?: error("No ScreenEntry for ${screen::class.qualifiedName}")
                    entry.UntypedContent(screen, Modifier)
                }
            },
            modifier = modifier,
        )
    }
}
```

`screenView` (PR 4) diff: wrap its content in
`CompositionLocalProvider(LocalNavRuntime provides runtime)`, so a migrated screen still
riding the fragment stack can host a nested `NavContent` flow. A pure-Compose activity
provides the same local in `setContent` from its injected runtime.

Tests, `StackNavigatorTest` (Robolectric), plus re-run `ScreenViewModelTest` against
this host, since the PR 5 contract must hold identically:

- `goToRendersNewTopEntry`
- `popRendersPreviousEntry`
- `popAtRootBubblesToParentNavigator`
- `resetRootAnswersEveryRemovedAwaiterWithNull`
- `offMainCallThrows`
- `goToForResultRoundTripsThroughRealPop`
- `resultCrossesHosts_composeCallerFragmentAnswerer`

---

## PR 10: Pushing twice is two entries

The most bug-preventing PR in phase 2. FragmentManager gave per-push identity for free;
a list does not: two pushes of equal screens would share entry identity, saved state, and
ViewModel. Wrap every push in a record with a fresh id and key everything off the record.
Reference: [PushRecords.kt](../runtime-nav3/src/main/kotlin/dev/goose/nav3/PushRecords.kt).

`nav/src/main/kotlin/yourapp/nav/PushRecord.kt` (new)

```kotlin
package yourapp.nav

/** Serializes with the stack, so identity survives recreation and process death. */
@Serializable
data class PushRecord(val screen: Screen, val id: String) : NavKey

fun Screen.pushed(): NavKey = PushRecord(this, UUID.randomUUID().toString())
fun NavKey.asScreen(): Screen = if (this is PushRecord) screen else this as Screen
```

`StackNavigator.kt` (diff)

```diff
-    override val backStack: List<Screen> get() = stack.map { it as Screen }
+    override val backStack: List<Screen> get() = stack.map { it.asScreen() }

-        stack.add(screen)
+        stack.add(screen.pushed())

-            deliverPopResult(stack.removeAt(stack.lastIndex) as Screen, result)
+            deliverPopResult(stack.removeAt(stack.lastIndex).asScreen(), result)
```

`NavContent.kt` (diff): `NavEntry(key)` keeps the record as its key, `key.asScreen()`
feeds the registry lookup and content. Same substitution in `reset`.

Tests, `PushRecordsTest` (reference:
[PushRecordsTest.kt](../runtime-nav3/src/test/kotlin/dev/goose/nav3/PushRecordsTest.kt)):

- `equalScreensPushedTwiceAreDistinctEntries`
- `equalScreensPushedTwiceHaveIndependentSaveableState`
- `equalScreensPushedTwiceGetIndependentViewModels`
- `poppingTopOfTwoEqualScreensLeavesBottomUntouched`
- `recordIdSurvivesRecreation`
- `screenEqualityAndSerializedPayloadUnchangedByWrapping`

---

## PR 11: Kill the process, keep the stack

FragmentManager persisted the old stack for you. The list is yours to persist: serialize
by class name plus kotlinx payload, restore reflectively, and fail soft, meaning an
undecodable stack restarts at its roots instead of crash-looping (a renamed screen after
an app update would otherwise re-save the poisoned stack on every death). Reference:
`buildNavSerializersModule` in [Goose.kt](../runtime-metro/src/main/kotlin/dev/goose/metro/Goose.kt).

`nav/src/main/kotlin/yourapp/nav/StackPersistence.kt`

```kotlin
package yourapp.nav

fun buildNavSerializersModule(explicit: Set<SerializersModule>): SerializersModule =
    SerializersModule {
        explicit.forEach { include(it) }                 // explicit wins: custom @SerialName, R8
        polymorphicDefaultSerializer(Screen::class) { value ->
            @Suppress("UNCHECKED_CAST")
            value::class.serializerOrNull() as SerializationStrategy<Screen>?
        }
        polymorphicDefaultDeserializer(Screen::class) { className ->
            className?.let {
                @Suppress("UNCHECKED_CAST")
                classForSerialName(it)?.kotlin?.serializerOrNull()
                    as DeserializationStrategy<Screen>?
            }
        }
        // repeat both blocks for NavKey::class so PushRecord round-trips
    }

/** Kotlinx default serial names are dot-separated. Nested classes need $ substitution. */
private fun classForSerialName(name: String): Class<*>? {
    var candidate = name
    while (true) {
        try { return Class.forName(candidate) }
        catch (_: ClassNotFoundException) {} catch (_: LinkageError) {}
        val lastDot = candidate.lastIndexOf('.')
        if (lastDot < 0) return null
        candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1)
    }
}
```

`NavRuntime` gains `addSerializers(module)` on the builder and exposes the built module.
`NavContent` backs its stack with saved state through it, wrapping restore so any decode
failure yields the root list.

Tests, `BackStackRestoreTest` (reference:
[BackStackRestoreTest.kt](../runtime-nav3/src/test/kotlin/dev/goose/nav3/BackStackRestoreTest.kt)):

- `stackOfDataClassesAndObjectsRoundTrips`
- `pushRecordIdsRoundTrip`
- `unknownClassNameOnRestoreYieldsRootStack`
- `unloadableClassOnRestoreYieldsRootStack`
- `nestedClassDefaultSerialNameResolves`
- `explicitSerializersModuleBeatsReflectiveLookup`

Add one R8 rule to the module's consumer proguard: keep `Companion.serializer()` for
`Screen` implementors, or ship explicit modules.

---

## PR 12: Many stacks, one host, on Compose

The Nav3 implementation of PR 1's `StackHost`, replacing PR 7's fragment tab host when
the tabs flip. Where PR 7 held one `FragmentNavigator` per tab over a child
FragmentManager, this holds one `NavBackStack` per tab and keeps every tab live in a
single `NavDisplay`, so hidden tabs' ViewModels stay alive. Reference:
[GooseTabNavigator.kt](../runtime-nav3/src/main/kotlin/dev/goose/nav3/GooseTabNavigator.kt).

```kotlin
package yourapp.nav

class TabNavigator(
    private val stacksByKey: Map<StackKey, NavBackStack<NavKey>>,   // one rememberNavBackStack per TabSpec
    router: ResultRouter,
    parent: Navigator? = null,
    initialStack: StackKey,
) : BaseStackHost(router, parent) {

    override val stacks: Set<StackKey> get() = stacksByKey.keys
    override var currentStack: StackKey by mutableStateOf(initialStack)   // saved by the host
        protected set

    private val perTab = stacksByKey.mapValues { (key, stack) ->
        StackNavigator(stack, router, parent = this, stackTag = "tab:${key.value}")
    }

    override fun navigatorFor(key: StackKey): BaseNavigator = perTab.getValue(key)

    /** Nothing to do: currentStack is snapshot state and NavDisplay reads it. */
    override fun show(key: StackKey) {}
}
```

Plus `rememberTabNavigator(tabs: List<TabSpec>)`, which creates the per-tab
`rememberNavBackStack`s and saves `currentStack` with `rememberSaveable`, and
`TabbedNavContent(tabs, onRootBack)`, a `NavDisplay` over the current tab's stack. Your
tab bar stays your own composable reading `currentStack` and calling `switchTo`. Because
`switchTo` and `goTo` are both snapshot writes, the user never sees the target tab flash
its old top screen before the pushed one appears.

**Stacks within stacks and deep links.** A nested flow on the Compose host is one
parameter: the flow screen's entry renders
`NavContent(rememberNavBackStack(FirstStep), parent = LocalNavigator.current)`. The inner
navigator mints its own stack tag, so results inside the flow never collide with results
outside it, and a pop at the inner root bubbles through `parent` exactly as on fragments.
A cold-start deep link is the initial list: `rememberNavBackStack(HomeScreen, OrderScreen(id))`.
Deep-linking *into* a flow is the outer screen's job, `CheckoutScreen(startAt = Payment)`,
and the flow reads `startAt` for its own root. The outer stack never reaches into the
inner one. Warm links stay `appNavigator.goTo(...)`.

Tests, `TabNavigatorTest`:

- `goToPushesOntoCurrentStackOnly`
- `switchToPreservesTheStackBeingLeft`
- `switchToAlreadyCurrentIsNoOp`
- `switchToThenGoToRendersInOneFrame`
- `switchToFromNestedFlowWalksParentTree`
- `switchToUnownedKeyThrows`
- `sameScreenClassAwaitedInTwoTabsDeliversToTheRightTab`
- `allStacksSurviveRecreation`

---

## PR 13: Overlays and transitions on the Compose host

On the fragment stack (PR 6) a dialog was an override the app wrote. On the Compose
stack there is no transaction to override: Nav3 renders a dialog when an entry's
metadata says so. So this PR adds markers on the screen value that the host reads into
metadata, for dialogs and for motion. Behavior, not serialized state. Reference:
[Screen.kt](../runtime/src/main/kotlin/dev/goose/runtime/Screen.kt),
[ScreenTransitions.kt](../runtime/src/main/kotlin/dev/goose/runtime/ScreenTransitions.kt),
[Presentation.kt](../runtime/src/main/kotlin/dev/goose/runtime/Presentation.kt).

The markers are called facets. A screen implements one directly, or its `Presentation`
from PR 8 implements it, so the `BottomSheet` object that already carries fragment
behavior now carries Compose behavior too, and every screen pointing at it gets both.

```kotlin
/** Facet: render in a dialog OVER the previous entry. Same stack, same push/pop/result rules. */
interface Overlay {
    fun dialogProperties(): DialogProperties = DialogProperties()
}

/** Sugar: a screen that is its own overlay. */
interface OverlayScreen : Screen, Overlay

/** Facet: motion. Implemented on the screen class or on its Presentation. */
interface ScreenTransitions {
    fun enterTransition(): ContentTransform
    fun exitTransition(): ContentTransform
}
```

```kotlin
// the same design-system object as PR 8, now with Compose behavior
object BottomSheet : Presentation, ScreenTransitions, Overlay {
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}
```

Resolution is the same precedence as PR 8, written as two host-side helpers. Apps never
call these.

```kotlin
/** The screen's own facet, else its presentation's, else null. */
fun Screen.effectiveOverlay(): Overlay? =
    this as? Overlay ?: (this as? PresentedScreen)?.presentation as? Overlay

/** The screen's own facet, else its presentation's, else the host default. */
fun Screen.effectiveTransitions(default: ScreenTransitions? = null): ScreenTransitions? =
    this as? ScreenTransitions
        ?: (this as? PresentedScreen)?.presentation as? ScreenTransitions
        ?: default
```

Host diff: `NavContent` adds `DialogSceneStrategy` to `NavDisplay`, and its
`entryProvider` puts `screen.effectiveOverlay()` and `screen.effectiveTransitions(hostDefault)`
into `NavEntry` metadata. Root entries ignore overlay and crossfade, a root dialog has
nothing to overlay.

A screen marked `OverlayScreen` that still has callers on the fragment stack keeps its
PR 6 override there, which can read the same marker to decide it's a dialog. Nothing
about the screen or its callers changes when the flow flips.

Tests, `EntryMetadataTest` and `OverlayTest`:

- `overlayScreenRendersAsDialogOverPreviousEntry`
- `tapOutsidePopsWithNullResult`
- `overlayAtRootRendersFullScreen`
- `screenFacetBeatsPresentationFacetBeatsHostDefault`
- `transitionsComeFromScreenOverride`

---

## PR 14: Delete the boilerplate (optional, last, always last)

Codegen must only emit code reviewers already read by hand in earlier PRs, so its review
reduces to diffing generated output against the hand-written pattern. In goose: a KSP
processor turning `@GooseUi(ProfileScreen::class)` on a composable into the PR 3
registration, and a compiler plugin writing `readResolve` on object screens and the
Mavericks companion. Skip the compiler plugin unless you accept a hard Kotlin version pin.
KSP covers the registration boilerplate without one.

Tests are golden-output: `processorGeneratesRegistrationMatchingHandWrittenPattern`,
`missingScreenParameterFailsCompilationWithActionableMessage`.

---

## Order rationale, in three lines

Contracts and results land before any host, so the expensive arguments happen where
change is cheap, and the router's edge cases get reviewed undistracted by UI. The
fragment host comes before any Compose stack because that's where a migrating app ships
value: screens start moving at PR 5, with Nav3 nowhere in the build. Phase 2 rebuilds by
hand what FragmentManager gave for free (per-push identity, persistence), which is why
those are separate, loudly-named PRs instead of details inside the host.

A sample app gets no slot. It grows one screen per PR from 4 onward, as each claim gains
the demo that makes it concrete.
