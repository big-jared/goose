# Migrating a Few Hundred Screens to Compose

*How we are choosing a path from fragments and MvRx to Compose, told through one
screen's migration.*

Our app runs on fragments, XML views, and MvRx, and we are moving its UI to Jetpack
Compose.

The app is not small. It has a few hundred screens, ViewModels carrying years of
business logic, and a navigation and state test suite that runs on the JVM. Whatever
path we pick has to hold up under that weight.

Along the way we will cover a few concepts:

- Typed Screen classes that replace argument Bundles
- A small Navigator interface that ViewModels navigate through
- Host fragments that let migrated screens ride the old back stack
- Typed results that replace our fragment result plumbing

The document is in three parts. First we describe our stack and what a migration has to
do. Then we weigh four paths against those needs. Finally we walk one screen through the
path we plan to pilot.

## Background on our stack

[Mavericks](https://airbnb.io/mavericks/) (formerly MvRx) is Airbnb's state library, and
it is the backbone of every screen we have. A screen's state is one immutable data
class. The ViewModel updates it through reducers, and the view re-renders on every
change.

```kotlin
data class ProfileState(
    val userId: String = "",
    val name: String = "",
    val followed: Boolean = false,
    val avatarId: String = "",
) : MavericksState

class ProfileViewModel(initialState: ProfileState) :
    MavericksViewModel<ProfileState>(initialState) {
    fun toggleFollow() = setState { copy(followed = !followed) }
}
```

`ProfileState` is the whole screen contract, and `toggleFollow` is the whole update
path. Rendering and navigation belong to a fragment. It observes the ViewModel, binds
views in `invalidate`, and opens other screens through the FragmentManager from its
click listeners. The arrangement has served us for years, and its halves are aging
differently.

## Challenges with our fragment architecture

The state half of the arrangement is healthy. Our ViewModels are unidirectional,
immutable, and tested on the JVM, and nothing about Compose requires changing them. The
trouble is in how the pieces point at each other, which can be visualized with the
following diagram.

```
        ProfileFragment  (view layer)
          │           │
  renders │           │ navigates
          ▼           ▼
ProfileViewModel    FragmentManager
  (state layer)     (navigation layer)
```

Both arrows leave the fragment. Rendering is what Compose replaces, so that arrow moves
no matter what we choose. Navigation is the hard arrow, because the code that navigates
lives inside the class we plan to delete. In practice, migrating a screen means facing
five difficulties at once:

1. **Coupling.** Navigation calls sit in click listeners inside fragments. Deleting a
   fragment deletes how its screen is reached, so every screen migration is also a
   navigation change.
2. **Reversibility.** A change that touches state, view, and navigation together is hard
   to ship one screen at a time. Rolling it back one screen at a time is harder still.
3. **Type safety.** Screens reach each other through Bundles of string keys. The
   compiler checks none of it, so a modularized app cannot express "feature A opens
   feature B's screen" as a checked contract.
4. **Testability.** Anything that touches the FragmentManager needs an emulator or
   Robolectric. Our JVM suite stops at the navigation boundary.
5. **Coexistence.** A few hundred screens migrate over years, not weeks. Fragments and
   Compose have to run in the same app, on the same back stack, the whole time.

## What we need from a migration

The difficulties translate into needs.

We want to leave the state layer alone. Most of our code is ViewModels that already
work, and risk scales with the amount of working code a migration rewrites.

We want each screen to move in its own PR and to come back in its own PR. Ship one
screen, watch it in production, and roll back that screen alone if it misbehaves.

We want screen contracts the compiler checks, including across module boundaries.

We want our tests to stay on the JVM, navigation included.

And we want infrastructure we can staff. Whatever we adopt or build, a team our size
has to maintain it.

## The paths we considered

With those five needs in hand, we looked at four paths.

**A Circuit rewrite.** Slack's [Circuit](https://slackhq.github.io/circuit/) is a mature
public Compose architecture that runs in their production apps. Screens are value types,
results are typed, and its tests run on the JVM without mocks, which meets our
type-safety and testing needs outright. However, Circuit's presenters live inside the
Compose runtime, so each migrated screen would rewrite a working ViewModel into a
presenter and trade MvRx retention for Compose retention. We did not see a way to keep
the state layer unchanged under this design, so our first need went unmet. For a new
app, we believe Circuit would be on our short list.

**Rebuilding what Airbnb built.** Airbnb's
[Trio](https://medium.com/airbnb-engineering/introducing-trio-part-i-7f5017a1a903) faced
our situation almost exactly, and its two big calls match our needs. Keep Mavericks, and
move navigation into the ViewModel. But Trio is not open source, so this path means
rebuilding it, including the router codegen and the IDE tooling Airbnb built to manage
its boilerplate. We did not feel we could staff that, so our maintainability need went
unmet.

Two smaller notes for the record. Trio stores the back stack inside ViewModel state, a
complexity its authors say they accept for the benefits. It also predates
[Navigation 3](https://developer.android.com/guide/navigation/navigation-3), so it
maintains machinery Google now ships. Had Trio been open source, we believe this
document would be about adopting it.

**Navigation 3 plus glue we write ourselves.** We believe Nav3 is the right base to
build on. The back stack is a plain list, and predictive back and transitions come built
in. Nav3 itself says nothing about MvRx ViewModels, dependency injection, typed results,
or coexisting with a FragmentManager. We would write that glue, and the glue is where
the hard bugs live: result delivery across process death, ViewModel lifetimes on a mixed
stack, interop in both directions.

This path meets every need on paper. We estimated it as quarters of platform work before
the first screen moves.

**Adopting goose.** [Goose](https://big-jared.github.io/goose/) is that same glue
already written. ViewModels stay MvRx, Nav3 owns the back stack, a small Navigator
interface hides which stack is underneath, and
[Metro](https://zacsweers.github.io/metro/) wires features together at compile time. The
interop we would otherwise build exists and has tests pinning its contracts. However,
goose is young, and we have not seen it proven at our scale. That risk is real, and a
three-screen pilot can measure it.

The third and fourth paths are the same bet. The question between them is build or
adopt.

Given our needs, our decision was to pilot goose on three screens before committing to
anything.

The rest of this document walks through what those pilot PRs look like.

## The shape of a migrated screen

A migrated screen is four pieces.

A Screen is a small data class that identifies the destination and carries its
arguments. A ViewModel is our existing Mavericks class, now holding a Navigator it can
call. A UI is one composable function that renders the state. A registry, generated at
compile time, maps the Screen class to the UI so any feature can open a screen it
cannot see the code of.

```
ProfileScreen (data class, in feature-profile/api)
      │
      │  looked up in a compile-time registry
      ▼
ProfileUi ◀── state ── ProfileViewModel ── goTo ──▶ Navigator
                                                       │
                                     FragmentManager today, Nav3 list later
```

Nothing in the top row knows what sits at the bottom. That one fact carries most of the
migration, because it means a screen written this way runs on either stack.

Goose is anchored to Mavericks and Android. It also ships a Mavericks-free
`StateHolder`, but we would use the MvRx path everywhere, so that is the only path this
document walks.

## The Screen class

A migrated screen starts as a Kotlin data class. It lives in the feature's `:api`
module, which lets other features open the screen without depending on its
implementation.

```kotlin
// in feature-profile/api
@Serializable data class ProfileScreen(val userId: String) : Screen
@Serializable data class FollowersScreen(val userId: String) : Screen
```

`ProfileScreen` is now the profile feature's public surface, and `FollowersScreen` sits
beside it. Every call site constructs them with a typed `userId`, so the compiler checks
what the old Bundle of string keys never did.

## Navigating from the ViewModel

Why should navigation live in the ViewModel? Because that is where the decision to
navigate is made. Today the decision and the action are split across layers. The
ViewModel decides, and a click listener like this one acts.

```kotlin
// in ProfileFragment
binding.followersRow.setOnClickListener {
    parentFragmentManager.commit {
        replace(R.id.container, FollowersFragment.newInstance(userId))
        addToBackStack(null)
    }
}
```

In a migrated screen the ViewModel does both, through the Navigator it now holds.

```kotlin
@AssistedInject
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,
    private val repo: ProfileRepository,
) : MavericksViewModel<ProfileState>(initialState) {

    fun toggleFollow() = setState { copy(followed = !followed) }

    fun openFollowers() = viewModelScope.launch {
        navigator.goTo(FollowersScreen(awaitState().userId))
    }
}
```

The `navigator` parameter and the `openFollowers` function are the new code, and
`toggleFollow` is untouched. That is the point.

The constructor does change. The hand-rolled companion factory becomes a one-line
`by gooseVmFactory(...)` delegation plus a small nested assisted factory, and real
dependencies like `repo` arrive from the graph. If you have written a Mavericks screen
before, everything else here should look familiar!

The ViewModel never learns what executes its navigation calls. During migration a
FragmentManager does. After migration a Nav3 list does. The same class runs on both,
which is what makes each screen's move reversible.

There is one cost worth naming here. Because the stack lives outside the ViewModel,
navigation and state cannot update in a single atomic reducer the way Trio allows. We
sequence them in a coroutine instead, and part of the pilot's job is to find a screen
where that difference matters.

## The UI function

The fragment and its XML become one annotated function.

```kotlin
@GooseUi(ProfileScreen::class)
@Composable
fun ProfileUi(state: ProfileState, vm: ProfileViewModel, modifier: Modifier) {
    Column(modifier) {
        Text(state.name)
        TextButton(onClick = vm::openFollowers) { Text("Followers") }
    }
}
```

The `@GooseUi` annotation generates the registration at compile time, and it scopes
`ProfileViewModel` to the screen, which guarantees the lifetime `fragmentViewModel` gave
us: retained across rotation, cleared when the screen pops, and restored after process
death with `@PersistState`. The `state` parameter observes the ViewModel, so the
function recomposes on every state change.

The last step of the PR is deleting `ProfileFragment` and its XML, and pointing the call
sites that opened it at the navigator.

## Where the two stacks meet

After that PR, the FragmentManager still owns navigation. The migrated screen rides the
old back stack inside a host fragment the library provides, and it pushes, pops, and
rotates like everything around it.

```
FragmentManager back stack
├── HomeFragment                      not migrated
├── ScreenFragment(ProfileScreen)     migrated, riding along
└── DetailFragment                    not migrated
```

When every screen in a flow has moved, we flip that flow to a Nav3 list, and no screen
code changes in the flip. The reverse direction also works. A fragment we have not
migrated can sit on a Compose stack behind a typed screen, so rolling a screen back is
re-registering the old fragment.

The mixed period has a cost. Host fragments, screen registrations, and per-screen
adapters are moving parts we would carry for the whole migration. Each one is temporary
by design, and the pilot should tell us what carrying them feels like.

## Returning results

Suppose the profile screen lets the user pick a new avatar. A screen that asks a
question declares its answer as part of its contract.

```kotlin
// in feature-profile/api
@Serializable data class PickAvatarScreen(val userId: String) :
    ScreenWithResult<AvatarChoice>
@Serializable data class AvatarChoice(val avatarId: String) : PopResult
```

`PickAvatarScreen` now promises an `AvatarChoice` to whoever opens it, and the compiler
holds it to that. The calling ViewModel suspends until the picker answers or the user
backs out.

```kotlin
fun changeAvatar() = viewModelScope.launch {
    val choice = navigator.goToForResult(PickAvatarScreen(awaitState().userId))
        ?: return@launch
    setState { copy(avatarId = choice.avatarId) }
}
```

`goToForResult` returns a typed `AvatarChoice`, survives rotation, and comes back null
on any dismissal, so `changeAvatar` never hangs. This replaces our fragment result
plumbing and its string keys.

One caveat here. A ViewModel suspended on a result does not survive process death, so a
restart means no answer arrives. This is the same contract a coroutine-wrapped
ActivityResult already gives us, and we have found it acceptable there.

The snippets above elide dependency injection details and error handling. A real screen
carries more than a name and two buttons, and the pilot will show how the pattern holds
when it does.

## Conclusion

In this document we described our three layers, the five things a migration has to do,
and the four paths we weighed. Two paths asked us to rewrite or rebuild more than we
believe our risk budget allows. The remaining two are the same architecture, built by us
or adopted, and adoption is the cheaper hypothesis to test.

The pilot is three screens, one PR each. One simple, one that returns a result, and one
inside a shared ViewModel flow. We will measure the diff sizes, roll one screen back to
prove reversibility, and let the JVM tests run in CI for a sprint. The costs we named
along the way stay open questions until then, and the pilot exists to tell us whether
they are worth carrying.

One practical note for the branch: Metro's compiler needs Gradle running on JDK 21.

If the numbers hold up, we set a per-quarter screen target and start the long walk. If
they do not, building the glue ourselves stays on the table. The next document will
report what the three PRs measured. Comments are welcome before the pilot begins!
