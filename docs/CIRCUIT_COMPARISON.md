# Goose and Circuit

Goose's API shapes — `Screen`, `Navigator`, `ScreenUi` — are borrowed from Slack's
[Circuit](https://slackhq.github.io/circuit/), and the kdocs in this repo say so. Under
the hood, though, the two are different animals, and the differences are instructive.
(Details reflect Circuit as of early 2026; it moves fast.)

## What Circuit nav does

The surface is familiar: `Navigator.goTo(screen)`, `pop(result)`, `resetRoot(screen)`,
screens are small value classes, and `NavigableCircuitContent(navigator, backStack)`
renders the top of the stack. Typed results exist too, via
`rememberAnsweringNavigator(navigator) { result -> ... }` — callback-shaped in
composition, rather than goose's `suspend goToForResult` from a retained presenter.

## Under Circuit's hood: it owns everything

Circuit does **not** sit on androidx navigation at all — no Nav2, no Nav3. Its stack is
its own `SaveableBackStack`: a snapshot-state list of `Record`s, where each record is
screen + args + a random UUID key. (That UUID is exactly goose's `PushedScreen`
per-push-identity trick — two pushes of equal screens are distinct records.)

Rendering is Circuit's own `AnimatedContent`-based display with pluggable
`NavDecoration`s for transitions and predictive back, plus a `SaveableStateHolder` and a
`RetainedStateRegistry` scoped per record. That last one matters: Circuit presenters are
*composables*, and config-change survival comes from `rememberRetained` backed by that
registry — not ViewModels.

Screen resolution goes through factory lookup (`Presenter.Factory` / `Ui.Factory`
registered on a `Circuit` object, usually via Dagger/Anvil multibinding) — the analogue
of goose's Metro-contributed `ScreenRegistry`. Persistence across process death rides
`rememberSaveable` with `Parcelable` screens, vs goose's kotlinx-serialized stacks.

## Where the philosophies split

**Tabs.** Circuit ships no tab host. Its bottom-nav answer is
`resetRoot(newRoot, saveState = true, restoreState = true)` — one active stack, swapped
wholesale, with departing stacks parked by root key. That is Nav2's
`saveState`/`restoreState` semantics reimplemented. Goose's `StackHost` keeps all stacks
*live* in one NavDisplay (hidden tabs' ViewModels stay alive), and there is no Circuit
equivalent of `switchTo` — cross-stack-then-push means resetRoot with restore flags,
then a push.

**Dialogs.** The opposite call from the one goose made. Circuit's overlays (dialogs,
bottom sheets) deliberately live *off* the back stack — an `OverlayHost` you `show()`
and suspend on, no stack entry, no deep-linkability. Goose's `OverlayScreen` puts
dialogs *on* the stack with normal push/pop/result semantics. Circuit's stance is "an
overlay isn't navigation"; goose's is "if it can answer a `goToForResult`, it's a
screen."

**Presenters.** Composable presenters + `rememberRetained` vs Mavericks ViewModels +
ViewModelStore decorators. Circuit's is more idiomatic-Compose; goose's exists precisely
because the goal is migrating an MvRx codebase without rewriting presenters.

**Interop.** Circuit has no fragment story — it assumes greenfield Compose. Goose's
entire reason to exist is the FragmentManager-backed `Navigator` implementing the same
interface.

## The one-liner

Circuit reimplemented the whole nav runtime itself; goose outsources that layer to Nav3
(`NavDisplay`, entry decorators, dialog scenes, predictive back) while keeping Circuit's
API shape — which is why goose is a few small files where Circuit is a framework. The
trade is control: Circuit can evolve its display and retention semantics freely; goose
inherits whatever Nav3 does, for better (free predictive back, dialog scenes,
shared-element scopes) and occasionally worse (you are bound by NavDisplay's rules, like
the root-entry-can't-be-a-dialog constraint).

For a new app with no legacy stack, Circuit is a strong choice — [WHY.md](../WHY.md)
says as much. Goose is for the app that already has hundreds of MvRx screens and needs
each one to move, and be able to move back, in its own PR.
