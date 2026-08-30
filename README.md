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

Note what you did NOT do: convert the whole flow. The FragmentManager still owns the back
stack. Goose wraps your new Compose screen in an invisible `ScreenFragment`, so it acts like any
other fragment. Once every screen in a flow is converted, swap the flow's host to
`NavigableGooseContent` and the fragments are gone. Until then, half-migrated works fine. (The
reverse also works: a Compose flow can carry leftover fragments with
`FragmentScreen.of<LegacyAboutFragment>()`.)

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
