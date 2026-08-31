# TODO

Ordered roughly by dependency. See [GOALS.md](GOALS.md) for milestone definitions.

## 0. Project scaffolding

- [x] `git init`, Gradle 9 / AGP / Kotlin setup, version catalog
      (nav3, compose BOM, mavericks, metro, kotlinx-serialization, fragment-compose)
- [x] Module skeleton: `:runtime`, `:runtime-metro`, `:runtime-nav3`,
      `:runtime-mavericks`, `:runtime-fragment`, `:app`,
      `:feature:home:{api,impl}`, `:feature:profile:{api,impl}`
- [x] Convention plugins (`build-logic`: goose.android.{base,library,api,feature,application})

## 1. `:runtime` — core contracts (no Android nav deps)

- [x] `Screen : NavKey` (`@Serializable`), `ScreenWithResult<R : PopResult>`
- [x] `PopResult` (`@Serializable` sealed interface)
- [x] `Navigator`: `goTo`, `pop(result)`, `resetRoot(saveState)`, `parent`,
      observable `backStack`, `suspend goToForResult(ScreenWithResult<R>): R?`
- [x] `TabNavigator : Navigator`: `selectTab(StackKey)`, saved per-tab stacks
- [x] `ScreenUi<S>` (`@Composable Content(screen, modifier)`) + erased `ScreenEntry`
- [x] `StateHolder<S>` + rememberStateHolder: presenter-agnostic option (pure Kotlin +
      coroutines, entry-scoped retention, NavigatorHandle injection), the multiplatform seam;
      Gaggle's TeamStats screen + lifecycle test. Mavericks remains the persistence-capable default.
- [x] Result bus: stack-scoped keyed routing (`ResultRouter`); survives config
      change. NOT yet durable across process death — documented limitation,
      matching coroutine-wrapped ActivityResult semantics

## 2. `:runtime-metro` — DI wiring

- [x] `@ScreenKey(KClass<out Screen>)` map key
- [x] `PresenterFactory` / `UiFactory` fun interfaces + `ScreenRegistry`
      aggregating the two multibound maps
- [x] `LocalAppGraph` CompositionLocal + `inject { }` composable helper
      (compile-safe via `@ContributesTo` accessor interfaces)
- [x] Decide/document graph shape: `AppScope` root (documented in README);
      `@GraphExtension` for logged-in scopes deferred until a sample needs it

## 3. `:runtime-mavericks` — MvRx as the presenter layer

- [x] `StateHolder` adapter: resolve `MavericksViewModel` against the NavEntry's
      `ViewModelStoreOwner` + `SavedStateRegistryOwner`
- [x] Initial-state convention: secondary `State(screen: FooScreen)` constructor
      (mirror of Mavericks' fragment-args convention)
- [x] `mavericks-metro`: `AssistedViewModelFactory` multibinding + custom
      `MavericksViewModelFactory` (port of the mavericks-hilt pattern)
- [x] Assisted params: `screen`, `navigator` injected into VM constructors;
      navigator must be the stable host-level delegate, not a composition capture
- [x] Verify `@PersistState` restoration through the per-entry saved-state owner
- [x] Shared-VM story: `FlowViewModelScope` + `flowViewModel()` — flow-level
      shared Mavericks VMs (used by the M2 checkout wizard); `activityViewModel()`
      parity via mavericksViewModel(scope = activity) shown in M3

## 4. `:runtime-nav3` — Compose host

- [x] `Nav3Navigator` over `SnapshotStateList<Screen>` + `rememberNavBackStack`
- [x] `NavigableGooseContent(backStack)`: `NavDisplay` + saved-state +
      viewmodel-store entry decorators, `ScreenContent` lookup via registry
- [x] Pop bubbling: stack-at-root `pop()` returns false → delegate to `parent`
- [x] `TabNavigatorImpl`: per-tab saved stacks, reselect-pops-to-root
- [x] Nested host support: child `NavigableGooseContent` with child navigator
- [x] Predictive back through the navigator tree
- [x] Shared elements: `SharedTransitionLayout` integration, shared-key
      modifiers plumbed to screen UIs

## 5. `:runtime-fragment` — interop

- [x] `FragmentNavigator(FragmentManager)` implementing `Navigator`, with
      `Screen → Fragment` mapping contributed via Metro multibinding
- [x] Direction 1: `ScreenFragment` hosting `ScreenContent` in a `ComposeView`
      (compose screen on the legacy stack)
- [x] Direction 2: `FragmentScreen` key + `UiFactory` rendering via
      `AndroidFragment` from fragment-compose (legacy fragment on the Nav3 stack)
- [x] Result bridge across the fragment boundary in both directions (rides back
      stack entry names + a back-stack-changed listener rather than the Fragment
      Result API, so it also catches direct `popBackStack()` calls)
- [x] Cross-boundary pop bubbling (compose child stack → fragment parent)

## 6. Milestone samples

- [x] **M1 happy path**: 2–3 screens in `:app`, typed result detail→list,
      rotation + process-death (don't-keep-activities) verification
- [x] **M2 multi-stack (multi-module)**: bottom tabs with one
      `:feature:*:{api,impl}` pair per tab, nested flow, cross-module
      navigation via `:api` deps only, shared-element keys declared in `:api`,
      grid→detail expand under predictive back, cross-stack result; enforce
      zero impl→impl deps
- [x] **M3 migration sample**: separate `:sample-migration` app — legacy
      fragments + MvRx + Metro, 50% migrated, both interop directions live, one
      VM shared verbatim between a fragment and a compose screen
- [x] Screenshots for the README (M2 catalog / detail / cart, docs/screenshots)

## 7. Hardening / later

- [x] goose-compiler compile-testing fixtures for the @GooseUi error grammar (kctfork 0.13 +
      KSP2: 10 tests, one per rejected shape plus the happy path)
- [x] True kill-and-relaunch process-death verification: tools/process-death-test.sh drives the
      real thing over adb (new pid, stack + @PersistState restored); host-side because killing
      an instrumented target kills the test too. Run on the emulator 2026-08-30, passing.
- [x] Real-FragmentNavigationRequest integration tests (out-of-order same-class dialogs, plain
      goTo isolation, one-shot delivery) in :runtime-fragment
- [x] CI (GitHub Actions: tests + assemble + publishToMavenLocal on every push/PR)
- [x] Maven Central publication: io.github.big-jared:goose-* via the vanniktech plugin;
      label-driven release workflow (see RELEASE.md). First release not yet run.
- [ ] Host-level default transition override (screen-owned ScreenTransitions is the contract;
      see DESIGN.md #33)
- [x] Session/logged-in graphs: @GraphExtension child registries via GooseScopeAccessors +
      GooseScope, registry chaining with parent fallback, @GooseUi(scope = ...) restored now
      that a child ScreenRegistry consumes it; Gaggle's checkout session + tests (DESIGN #36)
- [x] Per-push instance identity for equal screens on one stack: internal PushedScreen records
      as NavEntry keys, no Nav3 changes needed (DESIGN.md #21)

- [x] Deep links → back stack synthesis (`rememberGooseBackStack(List<Screen>)`;
      URI parsing stays app-side)
- [x] Overlay/dialog screens: `OverlayScreen` marker → DialogSceneStrategy
      (M2 cart-info dialog)
- [x] Instrumentation tests: retention, result delivery, recreation, back
- [x] Build-time rule: goose.android.feature fails configuration on
      `:feature:*:impl` → `*-impl` project deps (verified with a synthetic violation)
- [x] README: migration cookbook ("migrate one screen" recipe, before/after diff)
