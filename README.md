# 🪿 goose

**Compose navigation for apps that grew up on MvRx and fragments, without the rewrite.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![API docs](https://img.shields.io/badge/API%20docs-big--jared.github.io%2Fgoose-blue)](https://big-jared.github.io/goose/)

Goose moves a Mavericks (MvRx) + fragments app to Compose one screen at a time.

Your ViewModels don't change. What changes is the stuff around them:

- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) owns the back
  stack. It's just a list.
- [Metro](https://zacsweers.github.io/metro/) wires your feature modules at compile time.
- ViewModels navigate through a small `Navigator` interface. They never see a FragmentManager
  or a NavController, so the same screen runs on either stack.

That last part is the whole trick: migrate one screen per PR, ship it, roll it back if you
have to.

## Setup

Gradle must run on JDK 21+ (Metro requires it). Then:

```kotlin
val gooseVersion = "0.4.0"

dependencies {
    implementation("io.github.big-jared:goose-runtime:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-metro:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-mavericks:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-nav3:$gooseVersion")
    implementation("io.github.big-jared:goose-runtime-fragment:$gooseVersion") // during migration
    ksp("io.github.big-jared:goose-compiler:$gooseVersion")

    // Generates readResolve on object screens and the Mavericks factory on ViewModels.
    add("kotlinCompilerPluginClasspath", "io.github.big-jared:goose-compiler-plugin:$gooseVersion")
}
```

Expose your Metro graph from the Application, and install a navigator over your existing
fragment container:

```kotlin
class MyApp : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<AppGraph>() }
    override fun onCreate() { super.onCreate(); Mavericks.initialize(this) }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        installGooseNavigator(R.id.fragment_container)
    }
}
```

That's the whole integration. Your FragmentManager back stack is still the stack, and the
navigator is available anywhere as `activity.gooseNavigator`. A pure-Compose activity skips
both: `setContent` with `GooseCompositionLocals` + `NavigableGooseContent` is the setup there.

If your root graph uses a custom scope marker, merge goose's in:
`@DependencyGraph(scope = MyRootScope::class, additionalScopes = [AppScope::class])`.

## Migrating a screen

Six steps. The last two only apply while the screen's destinations are still fragments.

**1. A typed screen instead of an args Bundle.** It lives in the feature's `:api` module so
other features can navigate to it:

```kotlin
@Serializable data class ProfileScreen(val userId: String) : Screen

data class ProfileState(...) : MavericksState {
    constructor(screen: ProfileScreen) : this(userId = screen.userId)  // same args convention
}
```

**2. Navigation moves into the ViewModel.** Two assisted params and a factory. The state logic
doesn't change:

```kotlin
@AssistedInject
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,
    private val repo: ProfileRepository,
) : MavericksViewModel<ProfileState>(initialState) {

    fun openFollowers() = viewModelScope.launch {
        navigator.goTo(FollowersScreen(awaitState().userId))
    }

    @AssistedFactory fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }
}
```

No companion: the compiler plugin generates the Mavericks factory. (Without the plugin, write
`companion object : MavericksViewModelFactory<...> by gooseVmFactory(ProfileViewModel::class)`.)

**3. The fragment and its XML become one annotated composable:**

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

Parameters are wired by type: the VM parameter gets a screen-scoped ViewModel (retained across
rotation, cleared on pop, `@PersistState` restored), the state parameter observes it, and
anything else is injected from the graph.

**4. Delete the fragment.** Legacy call sites use
`requireActivity().gooseNavigator.goTo(ProfileScreen(user.id))`.

**5. Register the fragments the migrated screen calls.** Step 2's `openFollowers()` navigates
to `FollowersScreen`, and that's still a fragment. On the fragment host, `goTo` resolves in
order:

1. A per-screen `@GooseFragmentNavigation` override, when one exists (step 6).
2. The host-wide `defaultNavigation` policy, if you passed one to `installGooseNavigator`.
3. The default transaction, showing whatever fragment is registered for the screen, or the
   screen's composable once it's migrated.

So this step is about layer 3: tell goose which fragment the screen means. There are two ways
to register it, and they route differently.

`@GooseFragment` makes the screen a registry entry, so it works on BOTH stacks: called from a
fragment-hosted screen it shows through a host fragment, and called from a Compose-hosted flow
the legacy fragment rides the Nav3 list (goose embeds it for you). The Bundle maps from the
screen's properties by name:

```kotlin
@GooseFragment(TermsScreen::class)
class TermsFragment : Fragment()   // reads "termsId", "revision" from arguments
```

`@GooseFragmentBinder` registers with the fragment host only, and pushes the real fragment as
a plain transaction, no wrapping. Use it while the flow is still on the FragmentManager, and
for cross-module cases where the typed screen (in `:api`) and the fragment can't see each
other, or the Bundle needs hand-building:

```kotlin
@GooseFragmentBinder(FollowersScreen::class)
class FollowersBinder : ScreenFragmentBinder {
    override fun createFragment(screen: Screen) =
        FollowersFragment.newInstance((screen as FollowersScreen).userId)
}
```

Either way, when a destination migrates, delete the registration and register a composable for
the same screen. No caller notices. One thing to remember: a flow can't flip to a Compose host
until every destination it reaches has an entry, because binders aren't consulted there.

**6. Route through your existing navigation helpers, when a plain transaction is wrong.** A
dialog, a `startDialogForResult` extension, a router someone built years ago: a per-screen
override runs your API instead, and `request.deliverResult(...)` answers a caller suspended in
`goToForResult`:

```kotlin
@GooseFragmentNavigation(PickPlanScreen::class)
class PickPlanNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        request.fragmentManager.startDialogForResult(...) { request.deliverResult(it) }
    }
}
```

That's the whole loop. The migrated screen rides the old back stack in an invisible host
fragment, so it pushes, pops, and rotates like everything around it. When a whole flow is
migrated, swap the fragment host for
`NavigableGooseContent(rememberGooseBackStack(HomeScreen))` and no screen code changes.

## Theming fragment-hosted screens

Fragment-hosted screens render outside your Compose shell's theme, so contribute the theme
once:

```kotlin
@ContributesIntoSet(AppScope::class)
@Inject
class AppThemeDecoration : GooseDecoration {
    @Composable override fun Decorate(content: @Composable () -> Unit) = AppTheme { content() }
}
```

## Typed results

Screens that are questions declare their answer:

```kotlin
@Serializable data class PickAddressScreen(val orderId: String) : ScreenWithResult<Address>
@Serializable data class Address(val line1: String) : PopResult

// caller
fun changeAddress() = viewModelScope.launch {
    val address = navigator.goToForResult(PickAddressScreen(orderId)) ?: return@launch
    setState { copy(address = address) }
}

// picker
fun onPicked(address: Address) = navigator.pop(address)
```

Results survive rotation and never cross between stacks. Every dismissal resumes the caller
with null. The one limit: a suspended caller doesn't survive process death, same as a
coroutine-wrapped ActivityResult.

## Nested flows and deep links

A flow is a screen that hosts its own back stack, with `parent` linking back presses out:

```kotlin
@GooseUi(CheckoutScreen::class)
@Composable
fun CheckoutUi(screen: CheckoutScreen, modifier: Modifier) {
    val parent = LocalNavigator.current
    FlowViewModelScope {   // steps share one flowViewModel() until the flow pops
        NavigableGooseContent(rememberGooseBackStack(ShippingStepScreen), modifier, parent = parent)
    }
}
```

A deep link is just you building the list:
`rememberGooseBackStack(listOf(HomeScreen, CheckoutScreen(startAtPayment = true)))`. Warm
links (`onNewIntent`) are navigator calls. If a saved stack can't be decoded after an app
update, it restarts at its roots instead of crash-looping.

## Tabs

```kotlin
val tabs = rememberTabNavigator(tabs = listOf(
    TabSpec(Tabs.Shop, CatalogScreen),
    TabSpec(Tabs.Profile, ProfileScreen),
))
TabbedGooseContent(tabs, onRootBack = { finish() })
MyTabBar(selected = tabs.currentStack, onSelect = tabs::selectTab)
```

Two rules. `goTo(screen)` always pushes onto the stack you're in (any screen works in any
tab). Leaving your stack is explicit and works from anywhere in the tree:

```kotlin
navigator.switchTo(Tabs.Profile).goTo(OrderHistoryScreen(orderId))
```

## Session scopes

Dependencies that live shorter than the app (a signed-in user, a checkout session) go in Metro
child graphs. Register screens into the scope and activate it around a subtree:

```kotlin
@GooseUi(GiftNoteScreen::class, scope = CheckoutScope::class)
@Composable
fun GiftNoteUi(modifier: Modifier, session: CheckoutSession) { ... }

val graph = rememberRetainedGraph { factory.createCheckoutGraph() }
GooseScope(graph) { NavigableGooseContent(childStack, parent = parentNavigator) }
```

Leaving the flow drops the graph. Re-entering builds a fresh one.

## Without Mavericks

`StateHolder` is the plain-Kotlin presenter with the same entry-scoped lifecycle, for screens
that don't need `Async` or `@PersistState`:

```kotlin
class StatsHolder(private val navigator: Navigator) : StateHolder<StatsState>(StatsState()) {
    fun done() { navigator.pop() }
}
// in the UI: val holder = rememberStateHolder { navigator -> StatsHolder(navigator) }
```

## Already on Dagger or Hilt?

You don't migrate your graph. Metro `@Includes` your existing component, and everything it
exposes becomes injectable:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @DependencyGraph.Factory
    fun interface Factory { fun create(@Includes legacy: LegacyComponent): AppGraph }
}
```

`@GooseUi` accepts Dagger's `@AssistedFactory` as well as Metro's, nested in the ViewModel or
top-level in its package (where factory codegen puts them). If your ViewModels extend a base
class, tell the plugin once:
`-P plugin:dev.goose.compiler-plugin:extraViewModelBases=AppViewModel`.

No Metro at all? Build the wiring by hand and use it anywhere a graph goes:

```kotlin
val environment = GooseEnvironment.Builder()
    .addUi<ProfileScreen> { screen, modifier -> ProfileUi(screen, modifier, dagger.factory()) }
    .addDecoration { content -> AppTheme { content() } }
    .build()
```

The `@GooseUi` codegen and session scopes stay Metro-only. Everything else runs on a built
environment.

## Custom hosts and embedding

Per-screen navigation overrides are migration step 6. The host is configurable too:
`installGooseNavigator` takes a host-wide `defaultNavigation` policy that sees every screen
without a per-screen override, and a custom FragmentManager for nested stack ownership.

Embedding without navigating works too: `ScreenFragment.newInstance(childFragmentManager,
screen)` attaches a goose screen in your own container, no back stack entry. It finds the
nearest navigator and scope by walking up the parent-fragment chain.

## Animations

- **Host defaults**: `TabbedGooseContent(tabs, defaultTransitions = SlideScreenTransitions)`.
  Slides with predictive back preview (opt into `enableOnBackInvokedCallback` in the
  manifest).
- **Per screen**: implement `ScreenTransitions` on the screen class.
- **Shared elements**: `Modifier.sharedScreenElement(key)` on both screens, key declared in
  `:api`. Use `sharedScreenBounds` for text that changes size, so glyphs never crop.
- **Dialogs**: mark the screen `OverlayScreen` and it renders as a dialog over the previous
  screen, same stack, same result semantics. Window config via `dialogProperties()` on the
  screen.

```kotlin
@Serializable
data class CheckoutScreen(val itemId: String) : Screen, ScreenTransitions {
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}
```

## Going deeper

- [DESIGN.md](DESIGN.md): every sharp edge, with reasoning.
- [docs/VIEWMODEL_CONTRACT.md](docs/VIEWMODEL_CONTRACT.md): the screen-scoped ViewModel
  lifecycle and the tests that pin it.
- [Gaggle](samples/gaggle): one mid-migration shop app exercising everything above, with a
  claim-by-claim test map in [samples/README.md](samples/README.md).
- [dagger-interop](samples/dagger-interop): goose next to a real Dagger component.

## License

[Apache 2.0](LICENSE)
