# 🪿 goose

**Modern Compose navigation for apps that grew up on MvRx and fragments, without the rewrite.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![API docs](https://img.shields.io/badge/API%20docs-big--jared.github.io%2Fgoose-blue)](https://big-jared.github.io/goose/)

Goose moves a Mavericks (MvRx) + fragments app to Compose one screen at a time.

Your ViewModels don't change. `setState`, `execute`, `Async`, and `@PersistState` all keep
working. What changes is the stuff around them:

- **Navigation 3** owns the back stack. It's just a list.
- **[Metro](https://zacsweers.github.io/metro/)** connects your feature modules at compile time.
- ViewModels navigate through a small **`Navigator`** interface. They never see a
  FragmentManager or a NavController.

Because a ViewModel only knows the Navigator, it doesn't care whether its screen is a fragment
today or Compose tomorrow. So you migrate one screen per PR, ship it, and roll it back if you
have to.

## Setup (once per app)

Build requirement first, because it fails confusingly otherwise: Gradle must run on JDK 21 or
newer (Metro's compiler plugin requires it). On JDK 17 the build fails before configuration.

Goose is on Maven Central under `io.github.big-jared`:

```kotlin
val gooseVersion = "0.2.1"

dependencies {
    implementation("io.github.big-jared:goose-runtime:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-metro:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-mavericks:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-nav3:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-fragment:$gooseVersion") // during migration
    ksp("io.github.big-jared:goose-compiler:$gooseVersion")
}
```


Not on Maven yet; include the `runtime*` modules with Gradle's `includeBuild` or as a
submodule. Then two steps:

**1. Point goose at your app graph.** If you're already on Metro, this is your existing
`@DependencyGraph(AppScope::class)` graph — goose's own wiring (registry, result router,
serializers) arrives by contribution like any feature, so having the `runtime*` modules on that
graph's classpath is the whole integration. Just expose the graph you already build:

```kotlin
class MyApp : Application(), GooseGraphHolder {
    override val gooseGraph: Any get() = appGraph    // the graph you already create
    override fun onCreate() { super.onCreate(); Mavericks.initialize(this) }
}
```

Starting from scratch (or coming from Dagger — see [below](#already-on-dagger-or-hilt)), the
graph is one empty interface; features fill it by contribution:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph

class MyApp : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<AppGraph>() }
    override fun onCreate() { super.onCreate(); Mavericks.initialize(this) }
}
```

> The `GooseGraphHolder` part is only needed while you still have fragment hosts. Android
> creates fragments (and recreated activities) itself, with no constructor to hand the graph
> through — so they reach up to the one object every framework component can see, the
> Application. A pure-Compose app can skip the interface and pass the graph straight to
> `GooseCompositionLocals`.

One caveat for existing Metro apps: goose's contributions target Metro's standard
`dev.zacsweers.metro.AppScope`. If your root graph merges a custom scope marker instead, merge
both: `@DependencyGraph(scope = MyRootScope::class, additionalScopes = [AppScope::class])`.
Existing `@GraphExtension` child scopes need nothing — unless one should host goose screens,
covered under [session scopes](#session-scopes-child-graphs) later.

**2. Install a navigator in your existing activity.** One call:

```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)               // your existing layout
        installGooseNavigator(R.id.fragment_container)       // your existing container
    }
}
```

> This step is migration-only too: it exists to drive an existing FragmentManager stack. In a
> pure-Compose activity there is no fragment container to install into — `setContent` with
> `GooseCompositionLocals` + `NavigableGooseContent` is the whole setup.

Why is even this needed, if the nav API drives your FragmentManager anyway? Because three
things can't be guessed: which container in your layout to push into, a stable object for
rotation-surviving ViewModels to hold (the FragmentManager itself dies with the activity), and
routing back presses so awaited results resolve. This call wires all three; there is no new nav
stack here. During migration, your FragmentManager back stack IS the stack. Afterwards the
navigator is available anywhere as `activity.gooseNavigator`.

Have multiple activities? Call `installGooseNavigator` in each one that owns a fragment stack.
Each activity gets its own independent navigator; separate activities are separate navigation
roots, stacked by Android itself, same as today.

That's it. Nothing about your existing fragments changes yet.

## Migrating a screen

Here's a screen the way most Mavericks apps have it. The fragment renders state and also does
the navigating:

```kotlin
class ProfileFragment : Fragment(R.layout.fragment_profile), MavericksView {
    private val viewModel: ProfileViewModel by fragmentViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.followButton.setOnClickListener { viewModel.toggleFollow() }
        binding.followersRow.setOnClickListener {
            parentFragmentManager.commit {                   // navigation lives in the view layer
                replace(R.id.container, FollowersFragment.newInstance(userId))
                addToBackStack(null)
            }
        }
    }

    override fun invalidate() = withState(viewModel) { state ->
        binding.userName.text = state.name
        binding.followButton.isSelected = state.followed
    }
}

data class ProfileState(
    val userId: String = "",
    val name: String = "",
    val followed: Boolean = false,
) : MavericksState {
    constructor(args: ProfileArgs) : this(userId = args.userId)  // the fragment-args convention
}

class ProfileViewModel(initialState: ProfileState) :
    MavericksViewModel<ProfileState>(initialState) {
    fun toggleFollow() = setState { copy(followed = !followed) }
}
```

Five steps to migrate it.

**1. Define the screen.** This replaces the args Bundle. It's a data class in the feature's
`:api` module, so other features can navigate to it without depending on your implementation:

```kotlin
@Serializable data class ProfileScreen(val userId: String) : Screen
```

Initial state keeps the fragment-args convention you already use, with the screen as the args:

```kotlin
data class ProfileState(...) : MavericksState {
    constructor(screen: ProfileScreen) : this(userId = screen.userId)
}
```

**2. Move navigation into the ViewModel, and change how it's built. The state logic doesn't
change:**

```kotlin
@AssistedInject                                            // was: nothing
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,            // new
    private val repo: ProfileRepository,                   // real deps, from the graph
) : MavericksViewModel<ProfileState>(initialState) {

    fun toggleFollow() = setState { copy(followed = !followed) }   // unchanged

    fun openFollowers() = viewModelScope.launch {          // was: a fragment transaction
        navigator.goTo(FollowersScreen(awaitState().userId))  //    in the fragment's click listener
    }

    @AssistedFactory fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }
    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
        by gooseVmFactory(ProfileViewModel::class)         // was: your hand-rolled factory
}
```

**3. Replace the fragment + XML with an annotated composable.** One annotation is the entire
registration; the goose-compiler KSP processor generates everything else:

```kotlin
@GooseUi(ProfileScreen::class)
@Composable
fun ProfileUi(state: ProfileState, vm: ProfileViewModel, modifier: Modifier) {
    Column(modifier) {
        Text(state.name)
        OutlinedButton(onClick = vm::toggleFollow) {
            Text(if (state.followed) "Following" else "Follow")
        }
        TextButton(onClick = vm::openFollowers) { Text("Followers") }
    }
}
```

Parameters are wired by type. The `ProfileViewModel` parameter is the VM from step 2, scoped
the way `fragmentViewModel` scoped it: same instance across rotation, cleared when the screen
pops, `@PersistState` restored after process death. The `ProfileState` parameter is that VM's
state, observed, so the function recomposes on every state change. `Modifier` comes from the
host, a parameter typed as the screen receives the screen, and any other parameter is injected
from the app graph, checked at compile time. All of them are optional: a screen with no
ViewModel just asks for whatever it renders.

**4. Delete the fragment and its XML, and update the call sites that opened it.** A legacy
fragment opens the new screen through the activity's navigator:

```kotlin
// in a legacy fragment that used to push ProfileFragment
binding.profileRow.setOnClickListener {
    requireActivity().gooseNavigator.goTo(ProfileScreen(user.id))
}
```

Migrated ViewModels just call `navigator.goTo(ProfileScreen(user.id))`.

### What's on the stack now?

You deleted `ProfileFragment`, but the FragmentManager still owns navigation. So what happens
when something calls this?

```kotlin
navigator.goTo(ProfileScreen("ada"))
```

The `FragmentNavigator` looks for a fragment registered for that screen. There isn't one
anymore, so it wraps your Compose `ProfileUi` in an invisible host fragment and pushes that:

```kotlin
// inside FragmentNavigator.goTo, simplified
val fragment = binders[screen::class]?.createFragment(screen)   // legacy screens land here
    ?: ScreenFragment.newInstance(screen)                       // migrated screens land here
fragmentManager.commit {
    replace(containerId, fragment)
    addToBackStack(resultKey(screen))
}
```

Your back stack is now a mix, and everything on it pushes, pops, and rotates like a fragment:

```
FragmentManager back stack
├── HomeFragment                      // legacy
├── ScreenFragment(ProfileScreen)     // your new Compose screen, riding along
└── DetailFragment                    // legacy
```

### Your theme and providers, inside the fragment host

Each `ScreenFragment` roots its own ComposeView — outside whatever `AppTheme { ... }` your
Compose shell wraps. Contribute a `GooseDecoration` once and every fragment-hosted screen
renders inside it:

```kotlin
@ContributesIntoSet(AppScope::class)
@Inject
class AppThemeDecoration(private val imageLoader: ImageLoader) : GooseDecoration {
    @Composable override fun Decorate(content: @Composable () -> Unit) {
        AppTheme {
            CompositionLocalProvider(LocalImageLoader provides imageLoader) { content() }
        }
    }
}
```

Decorations are constructor-injected from the graph, so providers can carry real dependencies.
Compose hosts (`NavigableGooseContent`, tabs) deliberately don't apply them — they already
render inside your shell's composition, so flipping a flow to Compose never double-themes.

### Flipping a flow once it's fully converted

When every screen in a flow is Compose, delete the fragment host and let a plain list own that
stack instead:

```kotlin
// before: fragments own the stack
gooseNavigator = installGooseNavigator(R.id.fragment_container)

// after: a list owns the stack
setContent {
    GooseCompositionLocals(graph) {
        NavigableGooseContent(rememberGooseBackStack(HomeScreen))
    }
}
```

No screen code changes in the flip. The same `ProfileUi` that rode in a `ScreenFragment`
yesterday renders as a Nav3 entry today, driven by the same ViewModel.

### The reverse also works

A converted flow can still carry a fragment you haven't gotten to yet:

```kotlin
navigator.goTo(FragmentScreen.of<LegacyAboutFragment>())    // a fragment on a Compose stack
```

Your old fragment needs real arguments, though, and `FragmentScreen.of` can only pass strings.
So instead you give that fragment a normal typed screen. The common case is one annotation on
the fragment — goose-compiler generates the registration, with the Bundle built from the
screen's constructor properties by name:

```kotlin
@Serializable data class TermsScreen(val termsId: String, val revision: Int) : Screen

@GooseFragment(TermsScreen::class)
class TermsFragment : Fragment() {
    // requireArguments().getString("termsId"), getInt("revision")
}
```

The name convention is the whole contract: the fragment reads each argument under the screen
property's own name. When the keys differ, or the fragment needs Bundle entries beyond the
screen's fields, write the registration by hand instead — the general form the annotation
generates for you:

```kotlin
@Provides @IntoMap @ClassKey(TermsScreen::class)
fun termsEntry(): ScreenEntry = fragmentScreenEntry<TermsFragment, TermsScreen> { screen ->
    bundleOf(
        "terms_id" to screen.termsId,                // legacy key names, verbatim
        "revision" to screen.revision,
        "author" to Author(name = "Legal"),          // Parcelables are fine
    )
}
```

Either way, callers just do `goTo(TermsScreen("TOS-7", 3))`, the fragment gets the Bundle it
always got, and since only the little screen object is saved, rotation and process death
rebuild the same fragment automatically. When you eventually migrate the fragment, delete the
registration (or annotation) and register a composable for the SAME screen; no caller notices.
(Fragments are created through your FragmentManager's `FragmentFactory`, so custom factories
keep working too.)

## What @GooseUi generates

The annotation expands (via KSP, at compile time, no reflection) to plain code you could write
by hand:

```kotlin
@ContributesTo(AppScope::class)
interface ProfileUiGooseModule {
    companion object {
        @Provides @IntoMap @ClassKey(ProfileScreen::class)
        fun provideProfileUi(vmFactory: ProfileViewModel.Factory): ScreenEntry =
            ScreenEntry { screen, modifier ->
                val vm = screenViewModel(screen, ProfileViewModel::class.java,
                    ProfileState::class.java, vmFactory::create)
                ProfileUi(state = vm.collectAsState().value, vm = vm, modifier = modifier)
            }
    }
}
```

Reading it bottom to top: the VM parameter becomes a `screenViewModel` call, with the assisted
factory from step 2 injected from the graph. The state parameter observes that VM. The whole
thing lands in an app-wide map keyed by the screen class. That map is how the app renders
screens from modules it never imports, and what `goTo(ProfileScreen(...))` looks up.

Two notes. A flow-shared ViewModel is never a parameter; call `flowViewModel()` inside the
function (next section). And the hand-written forms remain supported if you prefer them: a
`@Provides` function with `screenUi { }`, or a `ScreenUi<S>` class.

## Typed results

Screens that are questions (pickers, confirmations, editors) declare their answer:

```kotlin
@Serializable data class PickShippingAddressScreen(val orderId: String) :
    ScreenWithResult<ShippingAddress>
@Serializable data class ShippingAddress(val line1: String, val city: String) : PopResult
```

```kotlin
// in the calling ViewModel
fun changeAddress() = viewModelScope.launch {
    val address = navigator.goToForResult(PickShippingAddressScreen(orderId))
        ?: return@launch                                  // null: user backed out
    setState { copy(shippingAddress = address) }
}

// in the picker's ViewModel
fun onAddressChosen(address: ShippingAddress) = navigator.pop(address)
```

Results are typed, survive rotation, and can't cross between stacks, tabs, or activities.
Every way a user can dismiss the screen resumes the caller with `null` instead of hanging it,
including a legacy fragment popping itself.

One contract to know. Suspended `goToForResult` callers do not survive process death: the
coroutine dies with the process, so treat a restart as "no answer", the same deal as a
coroutine-wrapped ActivityResult (stacks, tabs, and `@PersistState` fields all DO come back).
Pushing the same screen value twice is fine, by the way: every push has its own identity, so
equal screens stacked together get independent ViewModels and state.

## Nested flows and deep links

A flow (a checkout wizard, an onboarding sequence) is just a screen that hosts its own back
stack. Steps inside it navigate normally. Navigators form a tree: pass the enclosing navigator
as `parent`, and when the flow's own stack runs out of screens, back bubbles out to the parent
stack. `FlowViewModelScope` gives every step one shared `flowViewModel()` to accumulate answers
in, retained until the flow pops:

```kotlin
@GooseUi(CheckoutScreen::class)
@Composable
fun CheckoutUi(screen: CheckoutScreen, modifier: Modifier) {
    val parent = LocalNavigator.current
    FlowViewModelScope {
        val steps = rememberGooseBackStack(
            if (screen.startAtPayment) listOf(ShippingStepScreen, PaymentStepScreen)
            else listOf(ShippingStepScreen)
        )
        NavigableGooseContent(steps, modifier, parent = parent)
    }
}
```

Deep links are the same idea one level up. A back stack is a list of screens, so a deep link is
not a route table entry: it is you building the list the user should land on. From a
notification that should open the payment step, three screens deep inside the flow:

```kotlin
// in the activity
val stack = rememberGooseBackStack(
    if (isPaymentDeepLink(intent)) listOf(HomeScreen, CheckoutScreen(startAtPayment = true))
    else listOf(HomeScreen)
)
NavigableGooseContent(stack)
```

The `CheckoutUi` above reads `startAtPayment` and synthesizes its child stack with shipping
beneath payment. The user lands on payment; back walks down exactly what was built: payment to
shipping, shipping out of the flow to home. State restoration uses the same mechanism, so if
this survives process death (it does, the stacks serialize), a deep link does too.

A deep link arriving while the app is already running (`onNewIntent`) is just a navigator
mutation: parse the intent, then call `resetRoot` and `goTo` — or, to land in another tab,
`switchTo(key).goTo(screen)` (see [tabs](#tabs-and-cross-stack-navigation) below). The list
overload above is only the cold-start half.

One more restoration guarantee, because deep links and app updates meet here: if a saved stack
cannot be decoded on launch (a screen class was renamed or removed in an update), the stack
restarts at its roots instead of crash-looping. Losing navigation state is recoverable; a crash
loop is not.

## Tabs and cross-stack navigation

A tab host is a navigator that multiplexes several persisted back stacks. Declare the stacks,
render the combined host, and pair it with your own tab bar UI:

```kotlin
val tabs = rememberTabNavigator(
    tabs = listOf(
        TabSpec(GaggleTabs.Shop, CatalogScreen),
        TabSpec(GaggleTabs.Cart, CartScreen),
        TabSpec(GaggleTabs.Profile, ProfileScreen),
    ),
)
Column {
    TabbedGooseContent(tabs, Modifier.weight(1f), onRootBack = { finish() })
    MyTabBar(selected = tabs.currentStack, onSelect = tabs::selectTab)
}
```

Each tab's stack survives switching away, configuration changes, and process death. The keys
(`StackKey("shop")` etc.) live in a shared `:api` module so any feature can address a stack;
they're also the persistence identity for the selected tab, so keep them stable across releases.

**The routing contract is deliberately dumb.** Screens carry no tab affinity, and there is no
route table deciding where a screen "belongs":

- `goTo(screen)` **always pushes onto the stack you're standing in.** Any screen is pushable in
  any stack — showing a profile page inside the shop tab is a normal thing to do, and a feature
  module can push a screen it imported without registering anything.
- **Leaving your stack is a separate, explicit intent:** `switchTo(key)`. It selects that stack
  (keeping the state of the one you left) and returns the host's navigator, so a cross-stack
  push chains:

```kotlin
// "open my order history, in the Profile tab" — from anywhere:
navigator.switchTo(GaggleTabs.Profile).goTo(OrderHistoryScreen(orderId))

// just "go to the Profile tab":
navigator.switchTo(GaggleTabs.Profile)
```

`switchTo` is an extension on every `Navigator`: it walks up the navigator tree to the nearest
host owning that key. So the call above works unchanged from a screen sitting directly in a tab
stack, or three levels down inside a nested checkout flow — the flow's own stack is untouched,
and the push lands on the profile stack. Both mutations happen before the next frame renders,
so the switch-and-push is visually atomic. Addressing a key no ancestor hosts is a programming
error and throws.

`selectTab(key)` is the tab-bar button, not a nav primitive: it behaves like `switchTo` except
re-selecting the current tab pops it to its root, the platform-conventional gesture. Feature
code navigating somewhere should use `switchTo`; only your tab bar should call `selectTab`.

Back inside a tab host: pop the current stack; at a non-primary tab's root, back falls to the
primary tab; at the primary tab's root, `onRootBack` fires (typically `finish()`).

## Session scopes (child graphs)

Some dependencies should not live as long as the app: a checkout session, a signed-in user's
repositories, a workflow's scratch state. Goose supports Metro child graphs end to end. Declare
the scope and its graph once, in the owning feature:

```kotlin
abstract class CheckoutScope private constructor()

@SingleIn(CheckoutScope::class)
@Inject
class CheckoutSession { var giftNote: String = "" }

@GraphExtension(CheckoutScope::class)
interface CheckoutGraph : GooseScopeAccessors {
    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    interface Factory { fun createCheckoutGraph(): CheckoutGraph }
}
```

Register screens INTO the scope with the same annotation, one extra argument. Injected
parameters resolve from the child graph:

```kotlin
@GooseUi(GiftNoteScreen::class, scope = CheckoutScope::class)
@Composable
fun GiftNoteUi(modifier: Modifier, session: CheckoutSession) { ... }
```

The host that owns the flow creates the graph and activates it for its subtree:

```kotlin
val factory = gooseGraph<CheckoutGraph.Factory>()
val checkoutGraph = rememberRetainedGraph { factory.createCheckoutGraph() }
GooseScope(checkoutGraph) {
    NavigableGooseContent(childStack, parent = parentNavigator)
}
```

The rules, all tested in the [Gaggle sample](samples/gaggle):

- Screens registered to the scope resolve only inside `GooseScope`; their dependencies come
  from the child graph.
- App-scoped screens keep working inside the scope (registries chain to the parent).
- Leaving the flow drops the child registry and everything it cached; re-entering builds a
  fresh graph, so session-scoped objects never outlive their session.
- `rememberRetainedGraph` keeps the graph alive across rotation, together with the ViewModels
  it was injected into (one session per flow, however many times the device rotates); process
  death rebuilds it fresh. Durable state still belongs in ViewModels, exactly as before.
- Scoping is composition-based: it works in Nav3 entries and inside a fragment-hosted screen's
  own compose content. It does not cross a FragmentManager push, so during migration keep
  scoped screens inside compose-hosted flows.

## Screens without Mavericks

Mavericks is the presenter layer goose is built around, but it is not required per screen. A
`StateHolder` is the presenter-agnostic option: pure Kotlin plus coroutines, one `StateFlow` of
state, the same entry-scoped lifecycle as `screenViewModel` (retained across rotation, cleared
on pop, a navigator that is safe to hold):

```kotlin
class TeamStatsHolder(private val navigator: Navigator) :
    StateHolder<TeamStatsState>(TeamStatsState()) {
    fun spotGoose() = setState { copy(geeseSpotted = geeseSpotted + 1) }
    fun done() { navigator.pop() }
}

@GooseUi(TeamStatsScreen::class)
@Composable
fun StatsUi(modifier: Modifier) {
    val holder = rememberStateHolder { navigator -> TeamStatsHolder(navigator) }
    val state by holder.state.collectAsState()
    ...
}
```

What you give up is Mavericks' machinery: no `@PersistState` process-death restoration, no
`Async`. Both styles coexist per screen, so use holders where they fit and ViewModels where
persistence matters. Because the contract is free of Mavericks and Android ViewModel types,
it is also the seam a future multiplatform goose builds on.

## Already on Dagger or Hilt?

You don't migrate your graph to adopt goose. Metro consumes an existing Dagger component
through its public accessors:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Includes legacy: LegacyComponent): AppGraph
    }
}

// at startup
createGraphFactory<AppGraph.Factory>().create(DaggerLegacyComponent.create())
```

Everything the Dagger component exposes becomes an ordinary binding goose screens and
ViewModels inject; the Dagger side keeps compiling with Dagger's own processor, untouched. The
[dagger-interop sample](samples/dagger-interop) is exactly this shape (a plain Dagger component, a goose screen injecting its
repository) and is covered by a test. Hilt apps expose their bindings the same way through an
`@EntryPoint`-style accessor interface handed to `@Includes`; Metro also ships annotation
interop (`metro { interop { includeDagger() } }`) if you want Metro to compile classes that
still carry javax.inject annotations.

Your Mavericks ViewModels don't migrate their factories either. `@GooseUi` accepts a nested
`@AssistedFactory` from **either** DI world — Metro's or Dagger's — as long as it has the
`(initialState, navigator)` create shape:

```kotlin
class CoachViewModel @AssistedInject constructor(          // dagger.assisted, unchanged
    @Assisted initialState: CoachState,
    @Assisted private val navigator: Navigator,
    private val repo: CoachRepository,
) : MavericksViewModel<CoachState>(initialState) {
    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(initialState: CoachState, navigator: Navigator): CoachViewModel
    }
}
```

(The `navigator` parameter is the one addition — it's how the VM navigates through goose at
all.) And if a screen doesn't fit `@GooseUi`'s grammar, you never need the full hand-written
`ScreenUi` class with its multibinding annotations: the `@Provides` + `screenUi` function form
is the one-liner escape hatch:

```kotlin
@Provides @IntoMap @ClassKey(CoachScreen::class)
fun coachUi(factory: CoachViewModel.Factory): ScreenEntry =
    screenUi<CoachScreen> { screen, modifier -> /* compose content */ }
```

## Keeping your existing navigation APIs

Mature apps have their own navigation helpers that everything else uses: a
`startDialogForResult` extension, a router module, a framework someone built years ago.
Migrated screens do not need to bypass them. The compose side keeps talking to the goose
`Navigator`, so the ViewModel is portable and testable:

```kotlin
// the migrated VM neither knows nor cares how this screen gets shown
fun changePlan() = viewModelScope.launch {
    val plan = navigator.goToForResult(PickPlanScreen(currentPlanId)) ?: return@launch
    setState { copy(plan = plan) }
}
```

And you contribute one adapter per screen telling the fragment host how to actually execute it,
using whatever API your project already trusts:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<FragmentScreenNavigation>())
@ClassKey(PickPlanScreen::class)
@Inject
class PickPlanNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        val screen = request.screen as PickPlanScreen
        // your existing helper, unchanged
        request.fragmentManager.startDialogForResult(
            PlanPickerDialog.newInstance(screen.currentPlanId)
        ) { picked ->
            request.deliverResult(picked?.let { PlanResult(it) })
        }
    }
}
```

`deliverResult` answers the caller suspended in `goToForResult` (null means dismissed). If your
destination pushes onto the FragmentManager back stack instead of showing a dialog, use
`request.backStackEntryName` as the `addToBackStack` name; results then deliver automatically
when the entry pops, no matter what pops it. Screens without an adapter get the default
transaction, a plain `replace` + `addToBackStack`. Once the app is fully migrated, delete the
adapters and nothing else changes.

The host is configurable too, not just individual screens. `installGooseNavigator` takes an
optional FragmentManager (pass a fragment's `childFragmentManager` for nested stack ownership)
and an optional host-wide policy that sees every screen without a per-screen adapter:

```kotlin
installGooseNavigator(
    containerId = R.id.container,
    defaultNavigation = { request ->
        if (request.screen is FullScreenDialogScreen) {
            MyDialogHost.show(request.fragmentManager, request.createFragment())
            // deliver via request.deliverResult(...) from your dismiss callback
        } else {
            request.performDefaultTransaction()   // everything else stays standard
        }
    },
)
```

`request.createFragment()` hands you the fragment goose would have shown (the bound legacy
fragment, or the compose host created through your FragmentManager's own `FragmentFactory`), so
custom transactions change HOW a screen appears without changing WHAT appears.

## Embedding without navigating

Not every child fragment is navigation. A parent attaches a chat panel, a map, a payment sheet
into one of its containers — no back stack entry, no navigator call. Goose never owns your
FragmentManager, so those transactions keep working untouched, including when the thing you
embed is a migrated goose screen:

```kotlin
val fragment = ScreenFragment.newInstance(childFragmentManager, ChatPanelScreen(ticketId))
childFragmentManager.commit { add(R.id.panel_container, fragment) }
```

Prefer this `newInstance(fragmentManager, screen)` overload: it creates the fragment through
that FragmentManager's own `FragmentFactory`, so a host with a custom factory sees goose's
fragments go through the same path as its own.

The embedded fragment wires itself up when its view is created; you configure it by being the
right kind of parent, not by passing anything in:

- **Navigator.** It walks up the parent-fragment chain for the nearest `FragmentNavigatorOwner`
  and falls back to the activity's navigator. So when the embedded screen's ViewModel calls
  `navigator.goTo(...)`, that pushes onto the nearest enclosing stack. If its navigations
  should land in one of YOUR containers instead, implement `FragmentNavigatorOwner` on the
  parent fragment with a `FragmentNavigator` over your `childFragmentManager` — the shape
  `SupportFlowFragment` in the [Gaggle sample](samples/gaggle) demonstrates.
- **Scoped dependencies.** Same walk, for the nearest `GooseScopeOwner`: embedded inside a
  scoped flow, the screen resolves the flow's child graph.
- **ViewModel lifetime.** The embedded fragment is the screen's `ViewModelStoreOwner`, so the
  ViewModel is retained across rotation and cleared when the fragment is removed — it lives
  exactly as long as the embedding, which is what embedding means.

One semantic difference from navigation: nobody is awaiting an embedded screen. Typed results
(`goToForResult`) ride the navigator and back stack, so a screen designed to answer a caller
should be pushed, not embedded.

The compose side mirrors both directions. A goose screen (or any composable) embeds a fragment
without a stack entry via `AndroidFragment` from `androidx.fragment.compose` — that is all
`fragmentScreenEntry` does internally. And non-goose compose UI embeds a single goose screen
with `GooseContent(screen, navigator)`, no stack host required.

## Animations

Three tools, all optional.

**Per-screen transitions.** A screen declares how it enters and leaves by implementing
`ScreenTransitions`, the same pattern as `OverlayScreen`:

```kotlin
@Serializable
data class CheckoutScreen(val itemId: String? = null) :
    ScreenWithResult<CheckoutResult>, ScreenTransitions {

    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}
```

That checkout now slides up over the cart and slides back down when it pops, from every call
site, because the screen owns its presentation. Screens without the interface get the host's
default (Nav3's standard transition). Nothing here is serialized; the functions are behavior,
not state.

**Shared elements.** Tag an element on both screens with the same key and it animates between
them during the transition:

```kotlin
// on the list screen
ItemImage(item, Modifier.sharedScreenElement(ItemImageKey(item.id)))
// on the detail screen
ItemImage(state.item, Modifier.sharedScreenElement(ItemImageKey(screen.itemId)))
```

Declare the key type in an `:api` module so both features can use it without depending on each
other. The modifier no-ops when no transition scope exists (for example, while the screen is
hosted inside a fragment mid-migration), so it is safe to add before the app is fully
converted.

**Dialogs.** Mark a screen `OverlayScreen` and it renders as a dialog above the previous
screen, on the same back stack, with the same result semantics: push it with `goTo` or
`goToForResult`, and tapping outside or pressing back pops it (a `null` result for anyone
awaiting one). The window is configured on the screen; the size is whatever your composable
measures:

```kotlin
@Serializable
data class ConfirmDeleteScreen(val itemId: String) :
    OverlayScreen, ScreenWithResult<ConfirmDeleteResult> {

    override fun dialogProperties() = DialogProperties(
        dismissOnClickOutside = false,        // force a real answer
        usePlatformDefaultWidth = false,      // my content decides its own width
    )
}
```

```kotlin
@GooseUi(ConfirmDeleteScreen::class)
@Composable
fun ConfirmDeleteUi(screen: ConfirmDeleteScreen, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Card(Modifier.fillMaxWidth(0.92f)) {      // the dialog is exactly this size
        Column(Modifier.padding(24.dp)) {
            Text("Delete ${screen.itemId}?")
            Row {
                TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
                Button(onClick = { navigator.pop(ConfirmDeleteResult(confirmed = true)) }) {
                    Text("Delete")
                }
            }
        }
    }
}
```

There is no custom dialog machinery here. `OverlayScreen` forwards to Nav3's
`DialogSceneStrategy`, which renders a regular Compose `Dialog`; `DialogProperties` is passed
straight through, not wrapped. Goose only moves the "I am a dialog" declaration onto the screen
class, so a feature can say it in its `:api` module and every host renders it right.

Why not skip `OverlayScreen` and call `Dialog()` inside a normal screen? Because a normal
screen replaces the one before it: your dialog would float over an empty background.
`OverlayScreen` is what keeps the previous screen visible underneath.

Two things dialogs cannot do:

- **Animate in.** Android shows dialog windows instantly; `ScreenTransitions` cannot change
  that, because it animates screens inside your app's window and a dialog is its own window.
  A screen that should slide up is not really a dialog: make it a normal screen with
  `ScreenTransitions` (like the checkout above) and draw it shaped like a sheet.
- **Render as a dialog on the fragment side.** If this screen gets pushed while navigation is
  still running on fragments, it shows through a normal fragment transaction. To get a dialog
  there during migration, contribute a `FragmentScreenNavigation` adapter (previous section)
  that shows a `DialogFragment`.

## Design decisions

The sharp edges are decided, not accidental: result-request identity, the exact `@GooseUi`
grammar, thread contracts, saved-state compatibility across releases, R8, tabs, and scoping are
all written down with their reasoning in [DESIGN.md](DESIGN.md). One in-depth sample app,
[Gaggle](samples/gaggle), exercises everything above as a mid-migration shop, with a
claim-by-claim map to its tests in [samples/README.md](samples/README.md); the tiny
[dagger-interop](samples/dagger-interop) sample proves adoption next to an existing Dagger
graph. The screen-scoped ViewModel
lifecycle (identity, retention, clearing, restoration, result cancellation) is one documented
contract with the tests that pin it on both hosts:
[docs/VIEWMODEL_CONTRACT.md](docs/VIEWMODEL_CONTRACT.md).

## License

[Apache 2.0](LICENSE)
