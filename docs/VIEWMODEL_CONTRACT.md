# The screen-scoped ViewModel contract

One contract, two hosts. `screenViewModel` gives a screen's composable a Mavericks ViewModel
with the rules below, and the rules are identical whether the screen is hosted by a Navigation 3
entry or rides a legacy FragmentManager back stack inside a `ScreenFragment` during migration.
Every rule names the test that pins it.

## Identity

**The screen instance is the ViewModel's arguments.** `Screen` is `java.io.Serializable`
precisely so it can be Mavericks args: your state class keeps the fragment-args convention,
`constructor(screen: ProfileScreen) : this(userId = screen.userId)`, and `@PersistState`
restoration works through Mavericks' own machinery, unchanged.

**Identity is the screen VALUE, not the screen type.** Two pushes of `ProfileScreen("ada")` and
`ProfileScreen("grace")` are two entries with two ViewModels; nothing leaks between them, whether
sequential (pop then push) or stacked.
Tests: `M1FlowRobolectricTest.viewModelsAreArgumentScoped` (Nav3),
`M3MigrationRobolectricTest.fragmentHostDistinctArgsGetDistinctVms` (fragments).

**Known limitation: two EQUAL screen values on one stack share an entry, and therefore a
ViewModel.** This is Navigation 3's value-based entry identity. If the same destination with the
same arguments can be pushed twice concurrently, give the screen a distinguishing field. (Tracked
for a real fix if Nav3 gains per-push instance identity.)

**The ViewModel key is stable per pushed entry.** Internally: the VM class name plus a
`rememberSaveable` per-entry id, so the key survives recreation and process death without
depending on instance identity.

## Lifetime

The entry's lifetime, not the graph's:

| Event | ViewModel |
|---|---|
| Recomposition | same instance |
| Configuration change / recreation | same instance, retained |
| Entry popped (any way: `pop`, system back, legacy `popBackStack()`) | cleared; its SavedStateRegistry provider unregistered (no leak) |
| Process death and relaunch | new instance; `@PersistState` fields restored; back stack restored |

The dependency graph is longer-lived than any screen: constructor dependencies are injected from
the graph, but the graph never owns the ViewModel. Flow-shared ViewModels (`flowViewModel()`)
are the deliberate exception: they live in the enclosing flow's store and clear when the flow
pops, shared by every step.

Tests: `M1FlowRobolectricTest.stateAndResultSurviveRecreation` (Nav3 retention + restoration),
`M3MigrationRobolectricTest.fragmentHostRetainsVmAcrossRecreation` and
`fragmentHostClearsVmOnPop` (fragment host), `BackStackRestoreTest` (persistence layer),
`tools/process-death-test.sh` (real process death on device).

## Results and cancellation

`goToForResult` suspends the calling ViewModel's coroutine. The contract mirrors a
coroutine-wrapped ActivityResult:

- Every dismissal path resumes the caller: a typed result, or `null` for "dismissed without
  answering" (system back, plain pop, a legacy fragment popping itself).
- The suspension survives recreation (the caller VM is retained; routing keys are
  class + stack scoped, never instance-identity based).
- The suspension does NOT survive process death; treat a relaunch as "no answer".
- If the awaiting coroutine is cancelled (its owner cleared), the registration is removed:
  the screen stays where the user can see it, and its eventual answer resumes nobody.

Tests: `M1FlowRobolectricTest.backDeliversNullResult` and `stateAndResultSurviveRecreation`,
`ResultCorrelationTest` (cancellation, out-of-order answers, failure paths),
`FragmentRequestCorrelationTest` (the same through real fragment machinery).

## What restoration restores, precisely

Three separate mechanisms, deliberately independent:

1. **The back stack** serializes screens (kotlinx) into saved state; restoration is resilient:
   an undecodable stack restarts at its roots instead of crash-looping.
2. **Mavericks state** restores per ViewModel via `@PersistState`, keyed by the stable entry key.
3. **The dependency graph** is never serialized; it is rebuilt by the application, and
   ViewModels re-inject from the fresh graph on recreation.
