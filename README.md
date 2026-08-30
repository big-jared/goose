# goose

**Metro + Mavericks (MvRx) + Navigation 3, under a Circuit-like API, with a fragment interop
migration path.** Multi-module by construction: features contribute screens, UIs, and ViewModel
factories to the app graph via Metro; the app module assembles and knows nothing else. The
compose surface mirrors Circuit's: `GooseCompositionLocals` ≈ CircuitCompositionLocals,
`GooseContent` ≈ CircuitContent, `NavigableGooseContent` ≈ NavigableCircuitContent, and
`ScreenUi.Content(screen, modifier)` ≈ `Ui.Content(state, modifier)`.

See [GOALS.md](GOALS.md) for the milestone definitions and [TODO.md](TODO.md) for status.

## The shape

```
Screen (kotlinx-@Serializable NavKey, lives in :feature:x:api)
   │  pushed onto a back stack owned by…
   ├── NavigableGooseContent (Nav3, compose)     ← end state
   ├── TabbedGooseContent (multi-stack tabs)     ← bottom-nav apps
   └── FragmentNavigator + ScreenFragment        ← during migration
   │
Navigator (goTo / pop(result) / resetRoot / suspend goToForResult, tree with parent)
   │  handed (assisted) to…
MavericksViewModel — your existing MvRx VMs, byte-for-byte
   │  rendered by…
ScreenUi<S> (@ContributesIntoMap, keyed by screen class; VM factory co-located)
```

One class per screen carries everything a feature contributes:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ProfileScreen::class)
@Inject
class ProfileUi(private val vmFactory: ProfileViewModel.Factory) : ScreenUi<ProfileScreen>() {
    @Composable override fun Content(screen: ProfileScreen, modifier: Modifier) {
        val vm = screenViewModel<ProfileViewModel, ProfileState>(screen) { state, navigator ->
            vmFactory.create(state, navigator)
        }
        val state by vm.collectAsState()
        ProfileContent(state, vm::toggleFollow, vm::done, modifier)
    }
}
```

A root host is a handful of lines:

```kotlin
setContent {
    GooseCompositionLocals(graph) {
        NavigableGooseContent(rememberGooseBackStack(HomeScreen))
    }
}
```

- **Presenters are Mavericks ViewModels.** `setState`/`withState`/`execute`/`Async`/`@PersistState`
  all work unchanged; `screenViewModel<VM, S>(screen)` scopes them to the nav entry
  (config-change retention, pop-to-clear, saved-state restoration).
- **Typed results:** `ScreenWithResult<R>` + `navigator.goToForResult(screen)` suspends the caller
  VM until the target pops with `navigator.pop(result)`. Back = null ("no answer"). Result routing
  is stack-scoped, so the same screen type awaited in two tabs can't cross-wire.
- **Multi-module:** feature `:impl` modules contribute one `ScreenUi` per screen (VM factory
  injected right into it) plus a `screenSerializers { subclass(...) }` registration (Nav3
  persistence needs polymorphic NavKey registration); `:api` modules hold only screens, results,
  and shared-element keys. Zero impl→impl edges, enforced at configuration time.
- **Fragment interop, both directions:** migrated compose screens ride a legacy FragmentManager
  stack via `ScreenFragment`; unconverted fragments ride a Nav3 stack via `FragmentScreen`.
  The `Navigator` interface is the seam — VMs never learn which world they're in.

## Samples

| Sample | Shows | Verified |
|---|---|---|
| `samples/m1` | Happy path: 2 features, typed result round trip, `@PersistState`, recreation survival | 3 instrumented tests (emulator) + Robolectric |
| `samples/m2` | Tabs (stack-per-tab, preserved across switches), nested checkout wizard, cross-module `goToForResult`, shared-element keys in `:api` | 4 instrumented tests (emulator) + Robolectric |
| `samples/m3` | 50%-migrated app: legacy MvRx fragments + migrated compose screens on one stack, shared activity-scoped VM across both worlds, results across the boundary in both directions, `FragmentScreen` on a Nav3 stack | 2 instrumented tests (emulator) + Robolectric |

<p align="center">
  <img src="docs/screenshots/m2_catalog.png" width="200" alt="M2 catalog tab" />
  <img src="docs/screenshots/m2_item_detail.png" width="200" alt="M2 item detail (shared-element end state, graph-injected pricing)" />
  <img src="docs/screenshots/m2_cart.png" width="200" alt="M2 cart tab" />
</p>

## Scopes and sharing

- Per-screen: `screenViewModel` (entry-scoped Mavericks VM, assisted screen + navigator).
- Per-flow: wrap a nested stack in `FlowViewModelScope`; every step shares VMs via
  `flowViewModel()` (the M2 checkout wizard shares a `CheckoutFlowViewModel` this way).
- Per-activity (migration): `mavericksViewModel(scope = activity)` — the same VM instance a
  legacy fragment sees through `activityViewModel()` (demonstrated in M3).
- App graph: `AppScope` root assembled by `@DependencyGraph`; per-login/per-flow Metro
  `@GraphExtension`s slot in later without touching this library.
- Dialogs: mark a screen `OverlayScreen` and it renders in a dialog over the previous entry —
  same stack, same results.

## Migration recipe (per screen)

1. Keep the ViewModel file. Swap its companion for
   `companion object : MavericksViewModelFactory<VM, S> by gooseVmFactory(VM::class)` and its
   constructor to `@AssistedInject` (state + `Navigator` assisted, real deps injected).
2. Write a `ScreenUi<S>` that injects the VM's factory, calls `screenViewModel(screen) { state,
   navigator -> factory.create(state, navigator) }`, and renders what the fragment rendered.
3. Define the `@Serializable` screen in the feature's `:api`; contribute the ui and the
   `screenSerializers { subclass(...) }` registration.
4. Delete the fragment. While its FLOW is still fragment-hosted, the screen runs in a
   `ScreenFragment` automatically; when the flow converts, flip the host to
   `NavigableGooseContent`.

## Known limitations

- Two EQUAL screen values on one stack share a Nav3 entry scope — and therefore a ViewModel
  (Nav3's default contentKey semantics). Give screens a distinguishing field when a destination
  can appear twice concurrently.
- In-flight `goToForResult` awaits survive configuration changes but not process death (same
  contract as a coroutine-wrapped ActivityResult).
- `FragmentNavigator.backStack` is empty by design: screens can't be reconstructed from a
  restored FragmentManager. Result delivery still works after restore (it rides entry names via a
  back-stack-changed listener, so it fires for system back and direct `popBackStack()` calls too).
- Tab hosts must pass `onRootBack` (e.g. `{ finish() }`) to `TabbedGooseContent`: hidden tabs'
  entries stay in the display list (that's what preserves their ViewModels), so NavDisplay
  intercepts back even at the primary tab's root.

## Environment note

Sample verification runs as instrumented tests (`connectedDebugAndroidTest` / `am instrument`) and
Robolectric unit tests. On the development host used to build this (macOS 26.2 + emulator +
API 35/36 images), typing into Compose 1.12 text fields via any injection path hard-wedges the
emulator process — an emulator/host bug unrelated to this library (system apps unaffected; the
sample text fields were made button-driven so every flow stays verifiable). Real devices and CI
Linux emulators are not expected to hit this.
