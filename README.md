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

## Setup (once per app)

Build requirement first, because it fails confusingly otherwise: Gradle must run on JDK 21 or
newer (Metro's compiler plugin requires it). On JDK 17 the build fails before configuration.


Not on Maven yet; include the `runtime*` modules with Gradle's `includeBuild` or as a
submodule. Then two steps:

**1. Give your Application a graph.** The graph interface stays empty; features fill it by
contribution:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph

class MyApp : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<AppGraph>() }
    override fun onCreate() { super.onCreate(); Mavericks.initialize(this) }
}
```

**2. Install a navigator in your existing activity.** One call:

```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)               // your existing layout
        installGooseNavigator(R.id.fragment_container)       // your existing container
    }
}
```

Why is even this needed, if the nav API drives your FragmentManager anyway? Because three
things can't be guessed: which container in your layout to push into, a stable object for
rotation-surviving ViewModels to hold (the FragmentManager itself dies with the activity), and
routing back presses so awaited results resolve. This call wires all three; there is no new nav
stack here. During migration, your FragmentManager back stack IS the stack. Afterwards the
navigator is available anywhere as `activity.gooseNavigator`.

Have multiple activities? Call `installGooseNavigator` in each one that owns a fragment stack.
Each activity gets its own independent navigator; separate activities are separate navigation
roots, stacked by Android itself, same as today.

That's it. Nothing about your existing fragments changes yet.

## Migrating a screen

Here's a screen the way most Mavericks apps have it. The fragment renders state and also does
the navigating:

```kotlin
class ProfileFragment : Fragment(R.layout.fragment_profile), MavericksView {
    private val viewModel: ProfileViewModel by fragmentViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.followButton.setOnClickListener { viewModel.toggleFollow() }
        binding.followersRow.setOnClickListener {
            parentFragmentManager.commit {                   // navigation lives in the view layer
                replace(R.id.container, FollowersFragment.newInstance(userId))
                addToBackStack(null)
            }
        }
    }

    override fun invalidate() = withState(viewModel) { state ->
        binding.userName.text = state.name
        binding.followButton.isSelected = state.followed
    }
}

data class ProfileState(
    val userId: String = "",
    val name: String = "",
    val followed: Boolean = false,
) : MavericksState {
    constructor(args: ProfileArgs) : this(userId = args.userId)  // the fragment-args convention
}

class ProfileViewModel(initialState: ProfileState) :
    MavericksViewModel<ProfileState>(initialState) {
    fun toggleFollow() = setState { copy(followed = !followed) }
}
```

Five steps to migrate it.

**1. Define the screen.** This replaces the args Bundle. It's a data class in the feature's
`:api` module, so other features can navigate to it without depending on your implementation:

```kotlin
@Serializable data class ProfileScreen(val userId: String) : Screen
```

Initial state keeps the fragment-args convention you already use, with the screen as the args:

```kotlin
data class ProfileState(...) : MavericksState {
    constructor(screen: ProfileScreen) : this(userId = screen.userId)
}
```

**2. Move navigation into the ViewModel, and change how it's built. The state logic doesn't
change:**

```kotlin
@AssistedInject                                            // was: nothing
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,            // new
    private val repo: ProfileRepository,                   // real deps, from the graph
) : MavericksViewModel<ProfileState>(initialState) {

    fun toggleFollow() = setState { copy(followed = !followed) }   // unchanged

    fun openFollowers() = viewModelScope.launch {          // was: a fragment transaction
        navigator.goTo(FollowersScreen(awaitState().userId))  //    in the fragment's click listener
    }

    @AssistedFactory fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }
    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState>
        by gooseVmFactory(ProfileViewModel::class)         // was: your hand-rolled factory
}
```

**3. Replace the fragment + XML with an annotated composable.** One annotation is the entire
registration; the goose-compiler KSP processor generates everything else:

```kotlin
@GooseUi(ProfileScreen::class)
@Composable
fun ProfileUi(state: ProfileState, vm: ProfileViewModel, modifier: Modifier) {
    Column(modifier) {
        Text(state.name)
        OutlinedButton(onClick = vm::toggleFollow) {
            Text(if (state.followed) "Following" else "Follow")
        }
        TextButton(onClick = vm::openFollowers) { Text("Followers") }
    }
}
```

Parameters are wired by type. The `ProfileViewModel` parameter is the VM from step 2, scoped
the way `fragmentViewModel` scoped it: same instance across rotation, cleared when the screen
pops, `@PersistState` restored after process death. The `ProfileState` parameter is that VM's
state, observed, so the function recomposes on every state change. `Modifier` comes from the
host, a parameter typed as the screen receives the screen, and any other parameter is injected
from the app graph, checked at compile time. All of them are optional: a screen with no
ViewModel just asks for whatever it renders.

**4. Delete the fragment and its XML, and update the call sites that opened it.** A legacy
fragment opens the new screen through the activity's navigator:

```kotlin
// in a legacy fragment that used to push ProfileFragment
binding.profileRow.setOnClickListener {
    requireActivity().gooseNavigator.goTo(ProfileScreen(user.id))
}
```

Migrated ViewModels just call `navigator.goTo(ProfileScreen(user.id))`.

### What's on the stack now?

You deleted `ProfileFragment`, but the FragmentManager still owns navigation. So what happens
when something calls this?

```kotlin
navigator.goTo(ProfileScreen("ada"))
```

The `FragmentNavigator` looks for a fragment registered for that screen. There isn't one
anymore, so it wraps your Compose `ProfileUi` in an invisible host fragment and pushes that:

```kotlin
// inside FragmentNavigator.goTo, simplified
val fragment = binders[screen::class]?.createFragment(screen)   // legacy screens land here
    ?: ScreenFragment.newInstance(screen)                       // migrated screens land here
fragmentManager.commit {
    replace(containerId, fragment)
    addToBackStack(resultKey(screen))
}
```

Your back stack is now a mix, and everything on it pushes, pops, and rotates like a fragment:

```
FragmentManager back stack
├── HomeFragment                      // legacy
├── ScreenFragment(ProfileScreen)     // your new Compose screen, riding along
└── DetailFragment                    // legacy
```

### Flipping a flow once it's fully converted

When every screen in a flow is Compose, delete the fragment host and let a plain list own that
stack instead:

```kotlin
// before: fragments own the stack
gooseNavigator = installGooseNavigator(R.id.fragment_container)

// after: a list owns the stack
setContent {
    GooseCompositionLocals(graph) {
        NavigableGooseContent(rememberGooseBackStack(HomeScreen))
    }
}
```

No screen code changes in the flip. The same `ProfileUi` that rode in a `ScreenFragment`
yesterday renders as a Nav3 entry today, driven by the same ViewModel.

### The reverse also works

A converted flow can still carry a fragment you haven't gotten to yet:

```kotlin
navigator.goTo(FragmentScreen.of<LegacyAboutFragment>())    // a fragment on a Compose stack
```

## What @GooseUi generates

The annotation expands (via KSP, at compile time, no reflection) to plain code you could write
by hand:

```kotlin
@ContributesTo(AppScope::class)
interface ProfileUiGooseModule {
    companion object {
        @Provides @IntoMap @ClassKey(ProfileScreen::class)
        fun provideProfileUi(vmFactory: ProfileViewModel.Factory): ScreenEntry =
            ScreenEntry { screen, modifier ->
                val vm = screenViewModel(screen, ProfileViewModel::class.java,
                    ProfileState::class.java, vmFactory::create)
                ProfileUi(state = vm.collectAsState().value, vm = vm, modifier = modifier)
            }
    }
}
```

Reading it bottom to top: the VM parameter becomes a `screenViewModel` call, with the assisted
factory from step 2 injected from the graph. The state parameter observes that VM. The whole
thing lands in an app-wide map keyed by the screen class. That map is how the app renders
screens from modules it never imports, and what `goTo(ProfileScreen(...))` looks up.

Two notes. A flow-shared ViewModel is never a parameter; call `flowViewModel()` inside the
function (next section). And the hand-written forms remain supported if you prefer them: a
`@Provides` function with `screenUi { }`, or a `ScreenUi<S>` class (the [samples/m2](samples/m2) app uses the
class style).

## Typed results

Screens that are questions (pickers, confirmations, editors) declare their answer:

```kotlin
@Serializable data class PickShippingAddressScreen(val orderId: String) :
    ScreenWithResult<ShippingAddress>
@Serializable data class ShippingAddress(val line1: String, val city: String) : PopResult
```

```kotlin
// in the calling ViewModel
fun changeAddress() = viewModelScope.launch {
    val address = navigator.goToForResult(PickShippingAddressScreen(orderId))
        ?: return@launch                                  // null: user backed out
    setState { copy(shippingAddress = address) }
}

// in the picker's ViewModel
fun onAddressChosen(address: ShippingAddress) = navigator.pop(address)
```

Results are typed, survive rotation, and can't cross between stacks, tabs, or activities.
Every way a user can dismiss the screen resumes the caller with `null` instead of hanging it,
including a legacy fragment popping itself.

Two contracts to know. Suspended `goToForResult` callers do not survive process death: the
coroutine dies with the process, so treat a restart as "no answer", the same deal as a
coroutine-wrapped ActivityResult (stacks, tabs, and `@PersistState` fields all DO come back).
And two equal screen values on the same stack share one ViewModel (inherited from Navigation 3's
entry identity); if the same destination can be pushed twice at once, give the screen a
distinguishing field.

## Nested flows and deep links

A flow (a checkout wizard, an onboarding sequence) is just a screen that hosts its own back
stack. Steps inside it navigate normally. Navigators form a tree: pass the enclosing navigator
as `parent`, and when the flow's own stack runs out of screens, back bubbles out to the parent
stack. `FlowViewModelScope` gives every step one shared `flowViewModel()` to accumulate answers
in, retained until the flow pops:

```kotlin
@GooseUi(CheckoutScreen::class)
@Composable
fun CheckoutUi(screen: CheckoutScreen, modifier: Modifier) {
    val parent = LocalNavigator.current
    FlowViewModelScope {
        val steps = rememberGooseBackStack(
            if (screen.startAtPayment) listOf(ShippingStepScreen, PaymentStepScreen)
            else listOf(ShippingStepScreen)
        )
        NavigableGooseContent(steps, modifier, parent = parent)
    }
}
```

Deep links are the same idea one level up. A back stack is a list of screens, so a deep link is
not a route table entry: it is you building the list the user should land on. From a
notification that should open the payment step, three screens deep inside the flow:

```kotlin
// in the activity
val stack = rememberGooseBackStack(
    if (isPaymentDeepLink(intent)) listOf(HomeScreen, CheckoutScreen(startAtPayment = true))
    else listOf(HomeScreen)
)
NavigableGooseContent(stack)
```

The `CheckoutUi` above reads `startAtPayment` and synthesizes its child stack with shipping
beneath payment. The user lands on payment; back walks down exactly what was built: payment to
shipping, shipping out of the flow to home. State restoration uses the same mechanism, so if
this survives process death (it does, the stacks serialize), a deep link does too.

A deep link arriving while the app is already running (`onNewIntent`) is just a navigator
mutation: parse the intent, then call `resetRoot` and `goTo` (or `goTo(tab, screen)` on a tab
host, which switches and pushes atomically) to build the same stack. The list overload above is
only the cold-start half.

One more restoration guarantee, because deep links and app updates meet here: if a saved stack
cannot be decoded on launch (a screen class was renamed or removed in an update), the stack
restarts at its roots instead of crash-looping. Losing navigation state is recoverable; a crash
loop is not.

## Keeping your existing navigation APIs

Mature apps have their own navigation helpers that everything else uses: a
`startDialogForResult` extension, a router module, a framework someone built years ago.
Migrated screens do not need to bypass them. The compose side keeps talking to the goose
`Navigator`, so the ViewModel is portable and testable:

```kotlin
// the migrated VM neither knows nor cares how this screen gets shown
fun changePlan() = viewModelScope.launch {
    val plan = navigator.goToForResult(PickPlanScreen(currentPlanId)) ?: return@launch
    setState { copy(plan = plan) }
}
```

And you contribute one adapter per screen telling the fragment host how to actually execute it,
using whatever API your project already trusts:

```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<FragmentScreenNavigation>())
@ClassKey(PickPlanScreen::class)
@Inject
class PickPlanNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        val screen = request.screen as PickPlanScreen
        // your existing helper, unchanged
        request.fragmentManager.startDialogForResult(
            PlanPickerDialog.newInstance(screen.currentPlanId)
        ) { picked ->
            request.deliverResult(picked?.let { PlanResult(it) })
        }
    }
}
```

`deliverResult` answers the caller suspended in `goToForResult` (null means dismissed). If your
destination pushes onto the FragmentManager back stack instead of showing a dialog, use
`request.backStackEntryName` as the `addToBackStack` name; results then deliver automatically
when the entry pops, no matter what pops it. Screens without an adapter get the default
transaction, a plain `replace` + `addToBackStack`. Once the app is fully migrated, delete the
adapters and nothing else changes.

## Animations

Three tools, all optional.

**Per-screen transitions.** A screen declares how it enters and leaves by implementing
`ScreenTransitions`, the same pattern as `OverlayScreen`:

```kotlin
@Serializable
data class CheckoutScreen(val itemId: String? = null) :
    ScreenWithResult<CheckoutResult>, ScreenTransitions {

    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}
```

That checkout now slides up over the cart and slides back down when it pops, from every call
site, because the screen owns its presentation. Screens without the interface get the host's
default (Nav3's standard transition). Nothing here is serialized; the functions are behavior,
not state.

**Shared elements.** Tag an element on both screens with the same key and it animates between
them during the transition:

```kotlin
// on the list screen
ItemImage(item, Modifier.sharedScreenElement(ItemImageKey(item.id)))
// on the detail screen
ItemImage(state.item, Modifier.sharedScreenElement(ItemImageKey(screen.itemId)))
```

Declare the key type in an `:api` module so both features can use it without depending on each
other. The modifier no-ops when no transition scope exists (for example, while the screen is
hosted inside a fragment mid-migration), so it is safe to add before the app is fully
converted.

**Dialogs.** Mark a screen `OverlayScreen` and it renders as a dialog above the previous
screen, on the same back stack, with the same result semantics: push it with `goTo` or
`goToForResult`, and tapping outside or pressing back pops it (a `null` result for anyone
awaiting one). The window is configured on the screen; the size is whatever your composable
measures:

```kotlin
@Serializable
data class ConfirmDeleteScreen(val itemId: String) :
    OverlayScreen, ScreenWithResult<ConfirmDeleteResult> {

    override fun dialogProperties() = DialogProperties(
        dismissOnClickOutside = false,        // force a real answer
        usePlatformDefaultWidth = false,      // my content decides its own width
    )
}
```

```kotlin
@GooseUi(ConfirmDeleteScreen::class)
@Composable
fun ConfirmDeleteUi(screen: ConfirmDeleteScreen, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Card(Modifier.fillMaxWidth(0.92f)) {      // the dialog is exactly this size
        Column(Modifier.padding(24.dp)) {
            Text("Delete ${screen.itemId}?")
            Row {
                TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
                Button(onClick = { navigator.pop(ConfirmDeleteResult(confirmed = true)) }) {
                    Text("Delete")
                }
            }
        }
    }
}
```

There is no custom dialog machinery here. `OverlayScreen` forwards to Nav3's
`DialogSceneStrategy`, which renders a regular Compose `Dialog`; `DialogProperties` is passed
straight through, not wrapped. Goose only moves the "I am a dialog" declaration onto the screen
class, so a feature can say it in its `:api` module and every host renders it right.

Why not skip `OverlayScreen` and call `Dialog()` inside a normal screen? Because a normal
screen replaces the one before it: your dialog would float over an empty background.
`OverlayScreen` is what keeps the previous screen visible underneath.

Two things dialogs cannot do:

- **Animate in.** Android shows dialog windows instantly; `ScreenTransitions` cannot change
  that, because it animates screens inside your app's window and a dialog is its own window.
  A screen that should slide up is not really a dialog: make it a normal screen with
  `ScreenTransitions` (like the checkout above) and draw it shaped like a sheet.
- **Render as a dialog on the fragment side.** If this screen gets pushed while navigation is
  still running on fragments, it shows through a normal fragment transaction. To get a dialog
  there during migration, contribute a `FragmentScreenNavigation` adapter (previous section)
  that shows a `DialogFragment`.

## Design decisions

The sharp edges are decided, not accidental: result-request identity, the exact `@GooseUi`
grammar, thread contracts, saved-state compatibility across releases, R8, tabs, and scoping are
all written down with their reasoning in [DESIGN.md](DESIGN.md).

## License

[Apache 2.0](LICENSE)
