# Goals

A multi-module Compose navigation + state runtime combining **Metro** (DI),
**Mavericks/MvRx** (presentation), and **Navigation 3** (back stack), exposing a
**Circuit-like Screen/Ui/Navigator API** with a **first-class Fragment interop
migration path**.

Core theses:

1. Existing `MavericksViewModel` implementations port **one-to-one**: keep the VM
   file, swap the companion factory for a Metro contribution, replace the fragment
   with a `Ui`, define a `Screen`.
2. The `Navigator` interface is the seam. VMs and UIs never know whether a
   FragmentManager or a Nav3 `SnapshotStateList` owns the stack, so migration is
   per-screen and reversible.
3. Feature modules contribute everything (screens, UIs, VM factories) via Metro
   `@ContributesIntoMap`; the app module only assembles the graph.

## Milestone 1 — Happy path (single stack, pure Compose)

A runnable `:app` with 2–3 screens proving the full loop:

- `Screen` (`@Serializable NavKey`) → Metro-created `MavericksViewModel`
  (assisted: screen + navigator) → `Ui` rendering `StateFlow<S>`.
- Nav3 `NavDisplay` host with per-entry `ViewModelStoreOwner` +
  `SavedStateRegistryOwner` decorators: config-change retention, pop-to-clear,
  `@PersistState` restoration all work.
- `goTo` / `pop` / `resetRoot` / `goToForResult` (typed `ScreenWithResult<R>`,
  suspend API in the VM, durable keyed delivery underneath).
- `LocalAppGraph` + `inject { }` for one-off composable injection via
  `@ContributesTo` accessors.
- Feature lives in `:feature:*:api` + `:feature:*:impl`; app knows neither.

**Done when:** detail screen returns a typed result to the list screen's VM,
survives rotation and process death, and the app module has zero references to
the feature impl module.

## Milestone 2 — Multi-stack, nested stacks, shared-element transitions (multi-module)

A bottom-tab sample stressing the navigator tree, built the way a real app
would be: **each tab is its own feature module pair** (`:feature:*:api` +
`:feature:*:impl`), impl modules never depend on each other, and the app module
only wires tab roots to `StackKey`s.

- `StackHost`: one saved back stack per tab, stack state preserved across
  tab switches, re-select pops to root.
- Cross-module navigation: tab A navigates into a screen owned by tab B's
  feature (via its `:api` module only), proving screens compose across module
  boundaries without impl coupling.
- Nested flow: a screen hosting its own child `NavDisplay` with a child
  `Navigator(parent = ...)`; unhandled `pop()` bubbles to the parent.
- Predictive back works at every level (child stack → tab stack → root).
- Shared-element transition across entries: `SharedTransitionLayout` wrapping
  `NavDisplay`, shared `Modifier` keys flowing through `Ui`s (grid → detail
  image expand), including across a tab switch if feasible. Shared-element keys
  are declared in `:api` modules so the animation works even when grid and
  detail live in different feature modules.
- `goToForResult` across stacks (nested flow returns a result to a parent-stack
  VM).

**Done when:** tab switching preserves each stack, back bubbles correctly, the
grid→detail shared-element animation runs in gesture-driven predictive back —
and each tab lives in its own feature module with zero impl→impl dependencies
(app module wires only tab roots).

## Milestone 3 — Migration sample: Metro + Fragment + MvRx app, 50% migrated

A realistic "legacy" app (fragments, MvRx VMs, FragmentManager navigation,
Metro DI) frozen mid-migration, demonstrating every interop mechanism at once:

- `FragmentNavigator` implementing `Navigator` over FragmentManager, with the
  `Screen → Fragment` registry contributed via Metro.
- Direction 1: migrated Compose screens hosted in the legacy stack via
  `ScreenFragment` (fragment owns the stack, screen is already pure).
- Direction 2: legacy fragments hosted on a Nav3 stack via `FragmentScreen` +
  `AndroidFragment` (one fully-converted flow flipped to `NavDisplay`).
- Shared `MavericksViewModel` used by BOTH a fragment and a compose `Ui`
  (unchanged VM file) to prove one-to-one portability.
- Cross-boundary results: compose VM awaits a result from a fragment screen
  (Fragment Result API bridge) and vice versa.
- Pop bubbling across the boundary: compose stack at root delegates back to the
  fragment navigator parent.

**Done when:** the sample app navigates seamlessly across the boundary in both
directions, and the diff for "migrate one more screen" touches only: delete
fragment, add `Ui`, add screen mapping — no VM changes.
