# Goose and Plain MvRx + AndroidX Navigation

The baseline goose competes with is not another architecture library — it is doing
nothing: keep Mavericks as-is and navigate with what AndroidX ships. That comes in two
flavors, the fragment status quo and the hand-wired Compose future, and goose's
argument is different against each. ([WHY.md](../WHY.md) tells the fuller migration
story; this doc is just the comparison. See also
[CIRCUIT_COMPARISON.md](CIRCUIT_COMPARISON.md) for the Circuit side.)

## Flavor one: MvRx + fragments (the status quo)

Plain Mavericks does not have a navigation opinion. In practice that means:

- **Navigation lives in the fragment.** The ViewModel decides ("user tapped followers"),
  then a click listener acts — `findNavController().navigate(...)` or a
  `FragmentManager` commit. The decision and the action are split across layers, and
  the acting half lives in the class a Compose migration deletes.
- **Arguments are Bundles.** Either raw string keys, Safe Args' XML codegen, or
  Mavericks' own `Mavericks.KEY_ARG` convention (a Parcelable/Serializable handed to
  the fragment, matched to a state-constructor overload). All of them are per-screen
  conventions, none of them a compiler-checked contract between feature modules.
- **Results are string-keyed.** The Fragment Result API, or a shared `activityViewModel`
  used as a bus. Nothing ties the question to the type of its answer.
- **The good part is genuinely good.** `fragmentViewModel` retention, `@PersistState`,
  JVM-testable reducers — the state layer needs nothing from goose, and goose changes
  none of it.

Against this flavor goose is not an alternative so much as a destination: the same
ViewModels, with the navigation calls moved inside them behind a `Navigator` the
fragment world can still execute (`FragmentNavigator` drives your existing
FragmentManager). Screens become typed classes in `:api` modules, results become
`goToForResult` with a typed answer, and navigation becomes testable on the JVM with a
fake `Navigator` — while the back stack underneath is still the one you have.

## Flavor two: MvRx + Nav3, hand-wired

Mavericks officially supports Compose (`mavericks-compose`: `mavericksViewModel()`,
`collectAsState()`), and Nav3 gives you the back stack as a plain list. So the obvious
question: why not just use them together?

You can — this is exactly the "Nav3 plus glue we write ourselves" path — but the glue
is not thin, and each piece of it is something goose is:

- **ViewModel lifetime.** A Mavericks VM must be scoped to the back-stack entry:
  retained across rotation, cleared on pop, distinct for two pushes of equal screens.
  That means a ViewModelStore decorator per entry, per-push identity keys, and a
  `MavericksViewModelFactory` that can see them. (Mavericks resolves factories only
  through VM companions, which is why goose needs its `gooseVmFactory` ThreadLocal
  handoff — a wart you would rediscover, not avoid.)
- **Navigation from the ViewModel.** Nav3 has no navigator to inject; you would hoist
  nav events out of the VM (a `Channel` collected in a `LaunchedEffect`) or pass
  lambdas down. Both put half the navigation decision back in composables.
- **Typed results.** Nothing exists. You would build result routing yourself, and the
  hard part is not the happy path — it is delivery across recreation (restored stacks
  hold new-but-equal screen instances, so identity-keyed routing silently orphans
  awaiters) and across same-class requests in different tabs.
- **Process death.** Nav3 wants serializable NavKeys; Mavericks wants
  Parcelable/Serializable args. Making one class satisfy both, with a multi-module
  `SerializersModule`, is goose's `Screen` contract.
- **Fragment coexistence.** Plain MvRx + Nav3 has no answer for the years where both
  stacks run in one app. This is the piece you cannot skip in a migration and the
  hardest to write.

None of these is exotic — they are quarters of platform work, and the bugs live in the
corners (recreation, process death, R8, dialogs at stack roots). Goose is that glue
with tests pinning the corners.

## What plain MvRx + nav does better

Honesty cuts both ways:

- **No new dependency risk.** AndroidX navigation is Google-staffed; goose is young and
  unproven at scale. Safe Args and the nav component have a decade of Stack Overflow
  behind them.
- **No codegen or DI opinion.** Goose brings a KSP processor and is anchored to Metro.
  A Dagger/Hilt/Anvil shop has an interop path, but it is a path, not zero.
- **Fewer moving parts during steady state.** If you are not migrating — a small app,
  or one staying on fragments — goose's host fragments, registries, and adapters are
  machinery you do not need. The nav component as designed is simpler.
- **Single-activity XML apps get real graph tooling.** The nav editor, deep-link
  manifest generation, and `NavigationUI` glue have no goose equivalent; goose treats
  deep links as code you write.

## The one-liner

Plain MvRx + AndroidX nav keeps the state layer healthy and leaves navigation in the
view layer, argued over Bundles — fine forever if you never migrate, and the thing that
makes every screen's migration a navigation rewrite if you do. Goose is the same MvRx
with the navigation decision moved into the ViewModel behind a typed, stack-agnostic
seam — which is worth its machinery precisely when screens need to move between stacks
one PR at a time, and not before.
