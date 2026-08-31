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

**Identity is per PUSH, which subsumes per-argument.** Different arguments were always
different entries; per-push records make even EQUAL values independent, so no combination of
arguments can ever share state accidentally.
Tests: `GaggleHardeningTest.equalStatsScreensAreIndependent` (the strongest case) and
`deepStackSurvivesRecreationAndUnwinds` (distinct-argument screens stacked eleven deep).

**Every push is its own entry, even for EQUAL screen values.** Internally each push wraps the
screen in a per-push record with a unique id; the record is what Navigation 3 entry identity,
saveable state, and ViewModel stores scope to, and it serializes with the back stack, so the
association survives recreation and process death. Two concurrent pushes of the same data
object get independent ViewModels, and popping one touches only its own entry. The screen's
own equality and serialized payload stay untouched.
Test: `GaggleHardeningTest.equalStatsScreensAreIndependent`.

**The ViewModel key is stable per pushed entry.** Internally: the VM class name plus a
`rememberSaveable` per-entry id inside the push record's saveable scope, so the key survives
recreation and process death without depending on instance identity.

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

Tests: `GaggleFlowTest.wizardSurvivesRecreationAtEveryStep` (Nav3 retention, including an
await surviving recreation), `GaggleHardeningTest.supportScopeAndVmContractAcrossFragmentBoundary`
(fragment host: retained across rotation, cleared on pop), `BackStackRestoreTest` (persistence
layer), `tools/process-death-test.sh` (real process death on device).

## StateHolder: the same contract without Mavericks

`StateHolder` + `rememberStateHolder` is the presenter-agnostic option (pure Kotlin plus
coroutines). It implements the identity and lifetime rules above unchanged: entry-scoped,
retained across recreation, cleared on pop with its `holderScope` cancelled. The deliberate
difference is restoration: no `@PersistState`, so state that must survive process death belongs
in a Mavericks ViewModel or the screen's saveable state.
Test: `GaggleHardeningTest.equalStatsScreensAreIndependent` (retention, async work, pop-clears).

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

Tests: `GaggleFlowTest.removeDialogResult` (the "Keep it" path is a plain pop delivering
null), `wizardSurvivesRecreationAtEveryStep` (an await surviving recreation),
`ResultCorrelationTest` (cancellation, out-of-order answers, failure paths),
`FragmentRequestCorrelationTest` (the same through real fragment machinery).

## What restoration restores, precisely

Three separate mechanisms, deliberately independent:

1. **The back stack** serializes screens (kotlinx) into saved state; restoration is resilient:
   an undecodable stack restarts at its roots instead of crash-looping.
2. **Mavericks state** restores per ViewModel via `@PersistState`, keyed by the stable entry key.
3. **The dependency graph** is never serialized; it is rebuilt by the application, and
   ViewModels re-inject from the fresh graph on recreation.
