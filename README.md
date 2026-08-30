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

**3. Replace the fragment + XML with a `ScreenUi`.** `screenViewModel` is `fragmentViewModel`
for this world: same instance across rotation, cleared when the screen pops, `@PersistState`
restored after process death:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class)
@Inject
class ProfileUi(private val vmFactory: ProfileViewModel.Factory) : ScreenUi<ProfileScreen>() {
    @Composable override fun Content(screen: ProfileScreen, modifier: Modifier) {
        val vm = screenViewModel<ProfileViewModel, ProfileState>(screen, vmFactory::create)
        val state by vm.collectAsState()
        Column(modifier) {
            Text(state.name)
            OutlinedButton(onClick = vm::toggleFollow) {
                Text(if (state.followed) "Following" else "Follow")
            }
            TextButton(onClick = vm::openFollowers) { Text("Followers") }
        }
    }
}
```

**4. Register the screen for back-stack persistence** (one block per feature module):

```kotlin
@ContributesTo(AppScope::class)
interface ProfileModule {
    companion object {
        @Provides @IntoSet fun serializers(): SerializersModule =
            screenSerializers { subclass(ProfileScreen::class) }
    }
}
```

**5. Delete the fragment and its XML, and update the call sites that opened it.** A legacy
fragment opens the new screen through the activity's navigator:

```kotlin
// in a legacy fragment that used to push ProfileFragment
binding.profileRow.setOnClickListener {
    (requireActivity() as FragmentNavigatorOwner)
        .gooseNavigator.goTo(ProfileScreen(user.id))
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

## Will this work in my app?

The questions a real codebase asks:

**Rotation and process death?** Migrated screens keep the Mavericks contract exactly: the VM
survives rotation, `@PersistState` fields come back after process death, and the back stack
itself is serialized and restored (that's what step 4 registers). The `m1` sample has tests for
all three.

**We share ViewModels with `activityViewModel()`.** Still works, including across the boundary.
In the `m3` sample the same activity-scoped `CounterViewModel`, one file, unchanged, drives a
legacy fragment and a migrated Compose screen at the same time.

**Our VMs get dependencies through companion `MavericksViewModelFactory`s.** That companion is
exactly what you swap in step 2. Dependencies move to the constructor, provided by the graph.
Unmigrated ViewModels are untouched; the two styles coexist per screen.

**We're on Dagger or Hilt, not Metro.** Goose's screen registry runs on Metro, which ships
Dagger interop for consuming an existing graph's bindings. This is the biggest integration
question for a Hilt app; if that's you, open an issue with your setup before committing.

**Dialogs and bottom sheets?** Mark the screen `OverlayScreen` and it renders as a dialog on
the same back stack, with the same result semantics.

**Deep links?** Build the stack you want to land on:
`rememberGooseBackStack(listOf(HomeScreen, ProfileScreen(id)))`.

**What if I wire something wrong?** Almost everything fails at build time with a message:

| Mistake | What happens |
|---|---|
| Wrong or missing constructor dependency | Feature module fails to compile, with a Metro dependency trace |
| Two screens contributed with the same key | App module fails to compile, duplicate map key |
| An `:impl` module depending on another feature's `:impl` | Build fails at configuration with an explanation |
| Forgot `binding = binding<ScreenEntry>()` | First navigation to that screen throws, and the message names this exact fix |
| Forgot the serializer registration (step 4) | First background of the app throws a `SerializationException` naming the class |
| Created a Goose VM outside `screenViewModel` | Throws immediately, with the message pointing at the right API |

**Can I revert a migrated screen?** Yes. Restore the fragment, delete the `ScreenUi`, and point
call sites back. Nothing else in the app referenced the change.

**How do I test migrated screens?** Compose UI tests, on device or on Robolectric. The three
sample apps' suites are the templates: navigation, typed results, recreation, and fragment
interop are all covered there.

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

Results are typed, survive rotation, and can't cross between tabs. Every way a user can dismiss
the screen resumes the caller with `null` instead of hanging it, including a legacy fragment
popping itself.

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
