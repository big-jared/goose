# 🪿 goose

**Modern Compose navigation for apps that grew up on MvRx and fragments, without the rewrite.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

The risky part of a mature app isn't its views, it's the state machines behind them. So keep
your Mavericks ViewModels exactly as they are (`setState`, `execute`, `Async`, `@PersistState`
all work unchanged) and replace what's around them: **Navigation 3** owns the back stack (a
plain list you can print), **[Metro](https://zacsweers.github.io/metro/)** wires features
together at compile time, and a **`Navigator` interface** is the only thing your ViewModels ever
see. Because they never touch a FragmentManager or NavController, the same ViewModel works
fragment-hosted today and Compose-hosted after migration. Each screen migrates in one small,
revertable PR.

## Migrating a screen

Once per app: expose a Metro graph from your Application and build a `FragmentNavigator` over
your activity's existing FragmentManager ([`samples/m3`](samples/m3) is the copy-paste
reference). Then, per screen:

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

You converted a screen, not the flow. The FragmentManager still owns the back stack; the new
Compose screen rides it inside an auto-created `ScreenFragment` and behaves like any other
fragment. When a whole flow is converted, point its host at `NavigableGooseContent` and the
fragment layer disappears. Until then, half-migrated is a stable place to live. (The reverse
works too: a Nav3 flow carries stragglers as `FragmentScreen.of<LegacyAboutFragment>()`.)

## The annotations, decoded

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class)
@Inject
```

`@Inject`: the graph may construct this class, and its constructor is the DI point for the
screen. Any binding works; a screen with no ViewModel just injects what it renders.
`@ContributesIntoMap`: at compile time, add this class to an app-wide map assembled by the app's
`@DependencyGraph`, which is how the app renders screens from modules it never imports.
`@ClassKey`: the map key navigation looks up. `binding = binding<ScreenEntry>()`: store it as
the interface the registry collects (Metro's default, the direct supertype `ScreenUi<S>`, isn't
it; forgetting this produces a first-navigation error naming the fix).

Everything else fails at compile time: wrong constructor deps fail the feature's build with a
trace, duplicate screen keys fail the app's build, and an `:impl` module depending on another
feature's `:impl` fails at configuration.

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

Typed end to end, rotation-proof, routed per stack, and every dismissal path (back gesture,
`resetRoot`, a legacy fragment's own `popBackStack()`) resumes the caller with `null` instead of
hanging it.

## Also in the box

One persisted stack per tab (`TabbedGooseContent`), nested flows with shared
`flowViewModel()`s, dialog screens (`OverlayScreen`) on the same stack, shared-element keys
declared in `:api` modules, and deep-link stacks via `rememberGooseBackStack(List<Screen>)`.
Three sample apps in [`samples/`](samples) are the spec: happy path (`m1`), multi-module tabs +
wizard (`m2`), and a 50% migrated fragment app (`m3`), each verified on-device and on
Robolectric.

<p align="center">
  <img src="docs/screenshots/m2_catalog.png" width="200" alt="M2 catalog tab" />
  <img src="docs/screenshots/m2_item_detail.png" width="200" alt="M2 item detail" />
  <img src="docs/screenshots/m2_cart.png" width="200" alt="M2 cart tab" />
</p>

## License

[Apache 2.0](LICENSE)
