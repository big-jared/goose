# 🪿 goose

**Modern Compose navigation for apps that grew up on MvRx and fragments, without the rewrite.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

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

Not on Maven yet; include the `runtime*` modules with Gradle's `includeBuild` or as a
submodule. Then two steps:

**1. Give your Application a graph.** The graph interface stays empty; features fill it by
contribution:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph

class MyApp : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<AppGraph>() }
    override fun onCreate() { super.onCreate(); Mavericks.initialize(this) }
}
```

**2. Install a navigator in your existing activity.** One call. It builds a navigator over the
FragmentManager and fragment container you already have, and routes back presses through it.
There is no new nav stack here: during migration, your FragmentManager back stack IS the stack.

```kotlin
class MainActivity : FragmentActivity(), FragmentNavigatorOwner {
    override lateinit var gooseNavigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)                        // your existing layout
        gooseNavigator = installGooseNavigator(R.id.fragment_container)  // your existing container
    }
}
```

Have multiple activities? Call `installGooseNavigator` in each one that owns a fragment stack.
Each activity gets its own independent navigator; separate activities are separate navigation
roots, stacked by Android itself, same as today.

That's it. Nothing about your existing fragments changes yet.

## Migrating a screen

**1. Define the screen** in the feature's `:api` module, so other features can navigate to it
without depending on your implementation:

```kotlin
@Serializable data class ProfileScreen(val userId: String) : Screen
```

**2. Change the ViewModel's front door. Nothing else about it:**

```kotlin
@AssistedInject                                            // was: nothing
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,            // replaces fragment-side navigation
    private val repo: ProfileRepository,                   // real deps, from the graph
) : MavericksViewModel<ProfileState>(initialState) {

    fun toggleFollow() = setState { copy(followed = !followed) }   // unchanged

    @AssistedFactory fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }
    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
        by gooseVmFactory(ProfileViewModel::class)
}
```

**3. Replace the fragment + XML with a `ScreenUi`.** `screenViewModel` gives the same retention
as `fragmentViewModel`: survives rotation, cleared on pop, `@PersistState` restored:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class)
@Inject
class ProfileUi(private val vmFactory: ProfileViewModel.Factory) : ScreenUi<ProfileScreen>() {
    @Composable override fun Content(screen: ProfileScreen, modifier: Modifier) {
        val vm = screenViewModel<ProfileViewModel, ProfileState>(screen, vmFactory::create)
        val state by vm.collectAsState()
        // compose what the XML rendered
    }
}
```

**4. Register it for back-stack persistence** (one block per feature):

```kotlin
@Provides @IntoSet fun serializers(): SerializersModule =
    screenSerializers { subclass(ProfileScreen::class) }
```

**5. Delete** the fragment, the XML, and the nav-graph entry. Fragment transactions at call
sites become `navigator.goTo(ProfileScreen(userId))`.

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

## The annotations, decoded

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class)
@Inject
```

- `@Inject`: the graph builds this class. The constructor is where a screen gets its
  dependencies. A screen with no ViewModel just injects whatever it renders.
- `@ContributesIntoMap(AppScope::class)`: adds this class to an app-wide map at compile time.
  That's how the app shows screens from modules it never imports.
- `@ClassKey(ProfileScreen::class)`: the map key. `goTo(ProfileScreen(...))` looks this up.
- `binding = binding<ScreenEntry>()`: store it as `ScreenEntry`, the type the registry reads.
  If you forget this one, the error at first navigation tells you.

Everything else fails at build time: a wrong constructor dependency, a duplicate screen key, or
an `:impl` module depending on another feature's `:impl`.

## Typed results

Screens that are questions (pickers, confirmations, editors) declare their answer:

```kotlin
@Serializable data class PickShippingAddressScreen(val orderId: String) :
    ScreenWithResult<ShippingAddress>
```

```kotlin
// caller VM                                          // picker VM
val address = navigator.goToForResult(                navigator.pop(address)
    PickShippingAddressScreen(orderId)) ?: return@launch   // null = backed out
```

Results are typed, survive rotation, and can't cross between tabs. If the user backs out in any
way at all, the caller gets `null` instead of hanging.

## Also in the box

- Tabs with one saved stack each: `TabbedGooseContent`
- Nested flows (wizards) that share a `flowViewModel()`
- Dialog screens on the same stack: `OverlayScreen`
- Shared-element transitions with keys declared in `:api` modules
- Deep links: `rememberGooseBackStack(List<Screen>)`

Three sample apps in [`samples/`](samples) show all of it: the happy path (`m1`), multi-module
tabs plus a wizard (`m2`), and a half-migrated fragment app (`m3`). Each is covered by tests on
a real emulator and on Robolectric.

<p align="center">
  <img src="docs/screenshots/m2_catalog.png" width="200" alt="M2 catalog tab" />
  <img src="docs/screenshots/m2_item_detail.png" width="200" alt="M2 item detail" />
  <img src="docs/screenshots/m2_cart.png" width="200" alt="M2 cart tab" />
</p>

## License

[Apache 2.0](LICENSE)
