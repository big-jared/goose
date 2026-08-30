# 🪿 goose

**Modern Compose navigation for apps that grew up on MvRx and fragments, without the rewrite.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## Why this exists

A lot of very good Android apps are built on Mavericks (MvRx) ViewModels, fragments, and a
FragmentManager back stack. The modern stack (Compose, Navigation 3, compile-time DI) is better,
but every path to it seems to start with "first, rewrite your ViewModels." That's where
migrations go to die: the risky part of a mature app isn't its views, it's the state machines
behind them, and those are usually the best-tested code in the building.

Goose starts from a different premise: **your Mavericks ViewModels are fine. Keep them.**
`setState`, `execute`, `Async`, `@PersistState`, the initial-state-from-args convention: all of
it works unchanged. Goose replaces everything around the ViewModel with three pieces:

- **Navigation 3** owns the back stack. It's a plain list you can print and reason about.
- **[Metro](https://zacsweers.github.io/metro/)** wires features together at compile time.
- A **`Navigator` interface** is the only thing your ViewModels ever see. Because they never
  touch a FragmentManager or a NavController, the same ViewModel works whether its screen is
  hosted by a fragment (today) or a Compose back stack (after migration). That makes migration a
  per-screen, reversible operation you can ship every week.

## Migrating your first screen

This is the whole point of the library, so it goes first. Say you have a classic MvRx screen:

```kotlin
// The screen you have today
class ProfileFragment : Fragment(R.layout.fragment_profile), MavericksView {
    private val viewModel: ProfileViewModel by fragmentViewModel()

    override fun invalidate() = withState(viewModel) { state ->
        binding.userName.text = state.name
        binding.followButton.isSelected = state.followed
    }
}

class ProfileViewModel(initialState: ProfileState) :
    MavericksViewModel<ProfileState>(initialState) {
    fun toggleFollow() = setState { copy(followed = !followed) }
}
```

### One-time setup (once per app, not per screen)

Your Application exposes a Metro graph, and the activity that hosts your fragments builds a
`FragmentNavigator` over its existing FragmentManager:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph                      // empty; features fill it by contribution

class MyApp : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<AppGraph>() }
    override fun onCreate() { super.onCreate(); Mavericks.initialize(this) }
}

class MainActivity : FragmentActivity(), FragmentNavigatorOwner {
    override val gooseNavigator: Navigator get() = /* FragmentNavigator over supportFragmentManager */
}
```

(The [`samples/m3`](samples/m3) app is exactly this shape and is the copy-paste reference.)

### Then, per screen

**Step 1. Define the screen.** A small serializable data class, placed in the feature's `:api`
module so other features can navigate to it without depending on your implementation:

```kotlin
@Serializable data class ProfileScreen(val userId: String) : Screen
```

**Step 2. Change the ViewModel's front door. Nothing else about it.** The body is untouched;
only how it's constructed changes:

```kotlin
@AssistedInject                                            // was: nothing
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,            // new: replaces fragment-side navigation
    private val repo: ProfileRepository,                   // real dependencies, from the graph
) : MavericksViewModel<ProfileState>(initialState) {

    fun toggleFollow() = setState { copy(followed = !followed) }   // unchanged

    @AssistedFactory fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }
    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
        by gooseVmFactory(ProfileViewModel::class)         // was: your hand-rolled factory
}
```

**Step 3. Write the UI.** Compose replaces the XML; `screenViewModel` replaces
`fragmentViewModel` (same retention: survives rotation, cleared on pop, `@PersistState`
restored):

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
        }
    }
}
```

**Step 4. Register the screen for back-stack persistence** (one small block per feature module):

```kotlin
@ContributesTo(AppScope::class)
interface ProfileModule {
    companion object {
        @Provides @IntoSet fun serializers(): SerializersModule =
            screenSerializers { subclass(ProfileScreen::class) }
    }
}
```

**Step 5. Delete.** `ProfileFragment`, `fragment_profile.xml`, and its nav-graph entry all go.
Call sites that did fragment transactions become `navigator.goTo(ProfileScreen(userId))`.

That's the whole migration. The part that makes it shippable: **you did not convert the flow,
only the screen.** The FragmentManager still owns the back stack, and Goose hosts your new
Compose screen inside an invisible `ScreenFragment` automatically, because no fragment is
registered for `ProfileScreen`. Push it, pop it, rotate on it: it behaves like any other
fragment in the stack. When every screen in a flow is migrated, you flip that flow's host to
`NavigableGooseContent` (a ~5 line change in the activity) and the fragment layer disappears.
Until then, "half migrated" is a stable place to live, and each screen migration is a small,
revertable PR.

The reverse direction works too: a Nav3-owned flow can carry not-yet-migrated fragments along
as `FragmentScreen.of<LegacyAboutFragment>()`.

## What those annotations actually do

The contribution block is the densest part of the API, so here it is line by line:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class)
@Inject
class ProfileUi(...) : ScreenUi<ProfileScreen>()
```

| Line | Meaning |
|---|---|
| `@Inject` | Metro may construct this class; constructor parameters come from the app graph. |
| `@ContributesIntoMap(AppScope::class, ...)` | At compile time, add this class to an app-wide map that any `@DependencyGraph(AppScope::class)` assembles. This is how the app module renders screens from feature modules it has never imported. |
| `binding = binding<ScreenEntry>()` | Store it in the map AS a `ScreenEntry` (the interface the screen registry collects). Metro's default would be the direct supertype `ScreenUi<ProfileScreen>`, which nothing collects. This parameter is required; forgetting it produces a first-navigation error that names this exact fix. |
| `@ClassKey(ProfileScreen::class)` | The map key. Navigation to a `ProfileScreen` looks up this class. |

Quality-of-binding guarantees, since that's what a mature app actually cares about:

- **Wiring is compile time.** A missing or wrong constructor dependency is a Metro compiler
  error in the feature module, with a dependency trace. There is no reflection and no runtime
  container to misconfigure.
- **Duplicate screens are compile time.** Two entries contributed for the same `@ClassKey` fail
  the app module's build as a duplicate map key.
- **The one runtime trap is fenced.** Forgetting the `binding` parameter (or forgetting to put
  the `:impl` module on the app's classpath) surfaces on first navigation with an error message
  listing both possible causes.
- **Module boundaries are build enforced.** A feature `:impl` (or `:api`) module that depends on
  another feature's `:impl` fails at configuration time with an explanation. Only app modules
  may see impls, and only so Metro can aggregate their contributions.

## DI into screens, including screens with no ViewModel at all

The `ScreenUi` constructor is the injection point. That's the entire DI story for a screen:
whatever the class asks for, the graph provides, checked at compile time. A ViewModel factory is
just one thing you might ask for. A pure Compose screen skips ViewModels entirely:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(AboutScreen::class)
@Inject
class AboutUi(
    private val buildInfo: BuildInfo,        // any binding from the graph
) : ScreenUi<AboutScreen>() {
    @Composable override fun Content(screen: AboutScreen, modifier: Modifier) {
        val navigator = LocalNavigator.current
        Column(modifier.padding(16.dp)) {
            Text("Version ${buildInfo.versionName}")
            TextButton(onClick = { navigator.goTo(LicensesScreen) }) { Text("Open licenses") }
        }
    }
}
```

For a one-off dependency deep inside composable code where constructor injection is awkward,
declare a `@ContributesTo(AppScope::class)` accessor interface anywhere and read it with
`gooseGraph<PricingAccessor>().pricingService`. It's a cast to an interface the graph provably
implements, so it stays compile safe; there is no string-keyed service locator anywhere.

## Typed results

Some screens are questions: pickers, confirmations, editors. Declare what they answer with, and
callers get a suspend call instead of a result-code dance:

```kotlin
@Serializable data class PickShippingAddressScreen(val orderId: String) :
    ScreenWithResult<ShippingAddress>
@Serializable data class ShippingAddress(val line1: String, val city: String) : PopResult
```

```kotlin
// In the calling ViewModel
fun changeAddress() = viewModelScope.launch {
    val address = navigator.goToForResult(PickShippingAddressScreen(orderId))
        ?: return@launch                       // null: user backed out without answering
    setState { copy(shippingAddress = address) }
}

// In the picker's ViewModel
fun onAddressChosen(address: ShippingAddress) = navigator.pop(address)
```

Results are typed end to end, survive rotation, are routed per stack so the same picker open in
two tabs can't cross-wire, and every path that dismisses the screen (back gesture, `resetRoot`,
a legacy fragment calling `popBackStack()` directly) resumes the caller with `null` rather than
leaving it suspended.

## What's actually happening

No magic budget is spent on navigation itself. `navigator.goTo(screen)` appends a data class to
a snapshot-backed list; Navigation 3's `NavDisplay` observes that list and renders the top
entry by looking its class up in the contributed map. Back removes the last element. You can
log the back stack, assert on it in tests, and serialize it (that's also how process-death
restoration works, via each feature's contributed serializers).

State lives in three well-defined places:

| Scope | API | Lifetime |
|---|---|---|
| One screen | `screenViewModel(screen, factory::create)` | the nav entry: rotation-proof, cleared on pop |
| One flow (wizard, checkout) | `FlowViewModelScope` + `flowViewModel()` | the flow's host entry |
| Activity (migration parity) | `mavericksViewModel(scope = activity)` | shared with legacy `activityViewModel()` fragments |

Tabs (`TabbedGooseContent`) keep one persisted stack per tab with state preserved across
switches. Nested flows host their own stack and bubble unhandled back to the parent navigator.
A screen marked `OverlayScreen` renders as a dialog on the same stack with the same result
semantics. Shared-element transitions use keys declared in `:api` modules so two features never
need to couple to animate together.

## Samples

Three apps in [`samples/`](samples), each verified by instrumented tests on an emulator and the
same suites on Robolectric. The samples are the spec: every behavior claimed above has a test in
one of them.

| Sample | What it proves |
|---|---|
| [`m1`](samples/m1) | The happy path: two features, typed result round trip, `@PersistState`, recreation survival |
| [`m2`](samples/m2) | Multi-module tabs, nested checkout wizard with a shared flow VM, cross-module results, shared elements, a dialog screen |
| [`m3`](samples/m3) | A 50% migrated app: legacy MvRx fragments and migrated compose screens on one stack, results across the boundary both ways, one ViewModel shared verbatim between a fragment and a compose screen |

<p align="center">
  <img src="docs/screenshots/m2_catalog.png" width="200" alt="M2 catalog tab" />
  <img src="docs/screenshots/m2_item_detail.png" width="200" alt="M2 item detail (shared-element end state, graph-injected pricing)" />
  <img src="docs/screenshots/m2_cart.png" width="200" alt="M2 cart tab" />
</p>

## Modules

```
runtime            Screen, Navigator, results, flow scopes. No DI, no Nav3 UI.
runtime-metro      ScreenRegistry, GooseCompositionLocals, GooseContent, graph access.
runtime-mavericks  screenViewModel / flowViewModel / gooseVmFactory: the MvRx bridge.
runtime-nav3       NavigableGooseContent, TabbedGooseContent, back-stack persistence.
runtime-fragment   FragmentNavigator, ScreenFragment, FragmentScreen: the interop layer.
```

Toolchain: Kotlin 2.4, AGP 9, Navigation 3 (stable line), Metro 1.4, Mavericks 3. Convention
plugins in [`build-logic/`](build-logic) keep module build files to a few lines.

## Known limitations

- Two equal screen values on one stack share a Nav3 entry scope, and therefore a ViewModel
  (Nav3's own contentKey semantics). Give screens a distinguishing field when a destination can
  appear twice concurrently.
- In-flight `goToForResult` awaits survive configuration changes but not process death, the same
  contract as a coroutine-wrapped ActivityResult.
- `FragmentNavigator.backStack` is empty by design (screens can't be reconstructed from a
  restored FragmentManager); result delivery still works after restore.
- Tab hosts should pass `onRootBack = { finish() }` to `TabbedGooseContent`: hidden tabs' entries
  stay in the display list (that's what preserves their ViewModels), so back is intercepted even
  at the primary tab's root.

## Status

Early. The API is small on purpose. Issues and PRs welcome.

## License

[Apache 2.0](LICENSE)
