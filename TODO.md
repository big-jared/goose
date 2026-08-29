# TODO

Ordered roughly by dependency. See [GOALS.md](GOALS.md) for milestone definitions.

## 0. Project scaffolding

- [ ] `git init`, Gradle 9 / AGP / Kotlin setup, version catalog
      (nav3, compose BOM, mavericks, metro, kotlinx-serialization, fragment-compose)
- [ ] Module skeleton: `:runtime`, `:runtime-metro`, `:runtime-nav3`,
      `:runtime-mavericks`, `:runtime-fragment`, `:app`,
      `:feature:home:{api,impl}`, `:feature:profile:{api,impl}`
- [ ] Convention plugins (android-library + compose + metro + ksp boilerplate)

## 1. `:runtime` — core contracts (no Android nav deps)

- [ ] `Screen : NavKey` (`@Serializable`), `ScreenWithResult<R : PopResult>`
- [ ] `PopResult` (`@Serializable` sealed interface)
- [ ] `Navigator`: `goTo`, `pop(result)`, `resetRoot(saveState)`, `parent`,
      observable `backStack`, `suspend goToForResult(ScreenWithResult<R>): R?`
- [ ] `TabNavigator : Navigator`: `selectTab(StackKey)`, saved per-tab stacks
- [ ] `Ui<S>` fun interface (`@Composable Content(state, modifier)`)
- [ ] `StateHolder<S>` (presenter-agnostic: exposes `StateFlow<S>`)
- [ ] Result bus: keyed, serializable, buffered in requester's `SavedStateHandle`
      (durable layer under the suspend API; survives process death)

## 2. `:runtime-metro` — DI wiring

- [ ] `@ScreenKey(KClass<out Screen>)` map key
- [ ] `PresenterFactory` / `UiFactory` fun interfaces + `ScreenRegistry`
      aggregating the two multibound maps
- [ ] `LocalAppGraph` CompositionLocal + `inject { }` composable helper
      (compile-safe via `@ContributesTo` accessor interfaces)
- [ ] Decide/document graph shape: `AppScope` root, `@GraphExtension` for
      logged-in / per-flow scopes (defer implementation until M3 needs it)

## 3. `:runtime-mavericks` — MvRx as the presenter layer

- [ ] `StateHolder` adapter: resolve `MavericksViewModel` against the NavEntry's
      `ViewModelStoreOwner` + `SavedStateRegistryOwner`
- [ ] Initial-state convention: secondary `State(screen: FooScreen)` constructor
      (mirror of Mavericks' fragment-args convention)
- [ ] `mavericks-metro`: `AssistedViewModelFactory` multibinding + custom
      `MavericksViewModelFactory` (port of the mavericks-hilt pattern)
- [ ] Assisted params: `screen`, `navigator` injected into VM constructors;
      navigator must be the stable host-level delegate, not a composition capture
- [ ] Verify `@PersistState` restoration through the per-entry saved-state owner
- [ ] Shared-VM story: flow-level `ViewModelStoreOwner` replacing
      `activityViewModel()` / `existingViewModel()` (design in M1, harden in M2)

## 4. `:runtime-nav3` — Compose host

- [ ] `Nav3Navigator` over `SnapshotStateList<Screen>` + `rememberNavBackStack`
- [ ] `ScreenNavDisplay(backStack, registry)`: `NavDisplay` + saved-state +
      viewmodel-store entry decorators, `ScreenContent` lookup via registry
- [ ] Pop bubbling: stack-at-root `pop()` returns false → delegate to `parent`
- [ ] `TabNavigatorImpl`: per-tab saved stacks, reselect-pops-to-root
- [ ] Nested host support: child `ScreenNavDisplay` with child navigator
- [ ] Predictive back through the navigator tree
- [ ] Shared elements: `SharedTransitionLayout` integration, shared-key
      modifiers plumbed to `Ui`s

## 5. `:runtime-fragment` — interop

- [ ] `FragmentNavigator(FragmentManager)` implementing `Navigator`, with
      `Screen → Fragment` mapping contributed via Metro multibinding
- [ ] Direction 1: `ScreenFragment` hosting `ScreenContent` in a `ComposeView`
      (compose screen on the legacy stack)
- [ ] Direction 2: `FragmentScreen` key + `UiFactory` rendering via
      `AndroidFragment` from fragment-compose (legacy fragment on the Nav3 stack)
- [ ] Result bridge: `goToForResult` ↔ Fragment Result API in both directions
- [ ] Cross-boundary pop bubbling (compose child stack → fragment parent)

## 6. Milestone samples

- [ ] **M1 happy path**: 2–3 screens in `:app`, typed result detail→list,
      rotation + process-death (don't-keep-activities) verification
- [ ] **M2 multi-stack (multi-module)**: bottom tabs with one
      `:feature:*:{api,impl}` pair per tab, nested flow, cross-module
      navigation via `:api` deps only, shared-element keys declared in `:api`,
      grid→detail expand under predictive back, cross-stack result; enforce
      zero impl→impl deps
- [ ] **M3 migration sample**: separate `:sample-migration` app — legacy
      fragments + MvRx + Metro, 50% migrated, both interop directions live, one
      VM shared verbatim between a fragment and a `Ui`
- [ ] Screenshot/gif each milestone for the README

## 7. Hardening / later

- [ ] Deep links → back stack synthesis
- [ ] Overlay/dialog screens (bottom sheets as entries)
- [ ] Instrumentation tests: retention, result delivery, process death, back
- [ ] Lint/detekt rule: no `:feature:*:impl` → `:feature:*:impl` deps
- [ ] README: migration cookbook ("migrate one screen" recipe, before/after diff)
