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
val gooseVersion = "0.5.0"

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

One caveat on that last line. Kotlin's compiler extension points have no stability guarantee
across compiler versions, so `goose-compiler-plugin` only works with the Kotlin it was built
against. This release is built on Kotlin 2.4.10, and matching that version is the only pairing
the plugin can promise. Nearby 2.4.x patch releases usually load fine, but there is no
guarantee behind that. On an incompatible Kotlin the build fails at the first compilation with
a plugin loading or API error, and the fix is to drop the line and write both pieces by hand:
`private fun readResolve(): Any = TheScreen` in each `object` screen, and the
`companion object : MavericksViewModelFactory<...> by gooseVmFactory(...)` shown later.
Everything else in goose is ordinary library code and has no such coupling.

The generated `readResolve` covers `object` screens, meaning anything implementing `Screen`.
That scope matches the runtime's R8 keep rule, which is what preserves the method in minified
builds. Earlier releases generated it for every Serializable `object`, so if you upgrade with
non-Screen Serializable singletons in your app, give those their hand-written
`readResolve` and a keep rule. Nothing at compile time will remind you.

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
navigator is available anywhere as `activity.gooseNavigator`. Both pieces are migration-only:
`GooseGraphHolder` exists so fragments (which have no constructor to hand the graph through)
can find it, and a pure-Compose activity skips both — `setContent` with
`GooseCompositionLocals` + `NavigableGooseContent` is the setup there.

If your root graph uses a custom scope marker, merge goose's in:
`@DependencyGraph(scope = MyRootScope::class, additionalScopes = [AppScope::class])`.

## Migrating a screen

Five steps. The fifth only applies while the screen's destinations are still fragments.

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

**5. Register the fragments the migrated screen calls, and pick how they route.** Step 2's
`openFollowers()` navigates to a screen that's still a fragment. Register it:

```kotlin
@GooseFragment(TermsScreen::class)
class TermsFragment : Fragment()   // Bundle maps from the screen's properties, by name
```

or with a binder when the argument keys don't match the screen's properties:

```kotlin
@GooseFragmentBinder(FollowersScreen::class)
class FollowersBinder : ScreenFragmentBinder {
    override fun createFragment(screen: Screen) =
        FollowersFragment.newInstance((screen as FollowersScreen).userId)
}
```

(`@GooseFragment` entries also render on Compose hosts. Binders are fragment-host only.)

Then `goTo` routes one of three ways:

**a. Let goose handle it.** The default. Under the hood:

```kotlin
// inside FragmentNavigator.goTo, simplified
val fragment = binders[screen::class]?.createFragment(screen)   // legacy screens land here
    ?: fragmentManager.fragmentFactory.instantiate(screenHost)  // migrated screens: your host
fragmentManager.commit {                                        // class, or ScreenFragment
    replace(containerId, fragment)
    addToBackStack(resultKey(screen))
}
```

**b. Override the host default.** Pass `defaultNavigation` where you build the navigator
(`installGooseNavigator`, or your own `FragmentNavigatorOwner`). Every screen without a
per-screen override goes through your policy.

**c. Override one screen.** For the one-off case (a dialog, your router).
`request.deliverResult(...)` answers `goToForResult` callers:

```kotlin
@GooseFragmentNavigation(PickPlanScreen::class)
class PickPlanNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        request.fragmentManager.startDialogForResult(...) { request.deliverResult(it) }
    }
}
```

When a destination migrates, delete its registration and register a composable for the same
screen. No caller notices. That holds for dialogs too: a migrated screen marked
`OverlayScreen` shows as a dialog on both hosts with no registration at all, and screens
sharing a custom presentation bind their fragment behavior once per presentation type with
`@GoosePresentationNavigation` (see Presentations below) instead of once per screen.

That's the whole loop. The migrated screen rides the old back stack in an invisible host
fragment, so it pushes, pops, and rotates like everything around it. When a whole flow is
migrated, swap the fragment host for
`NavigableGooseContent(rememberGooseBackStack(HomeScreen))` and no screen code changes.

## Your base fragment as the screen host

Fragment-hosted screens ride goose's plain `ScreenFragment` by default, outside your Compose
shell's theme and outside your fragment base class. Apps with a base fragment (lifecycle
hooks, analytics, a theme) register their own host instead:

```kotlin
class GaggleScreenFragment : GaggleFragment() {   // your base, your onDestroy analytics
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        gooseScreenView { content -> AppTheme { content() } }
}

installGooseNavigator(R.id.container, screenHost = GaggleScreenFragment::class)
```

`gooseScreenView` is the entire wiring (screen from arguments, navigator, scopes); the wrap
lambda composes your theme and chrome around the screen.

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

## How Goose gets built

Everything hosts need is one object, `Goose`, with one construction path, `Goose.Builder`
(the analogue of Circuit's `Circuit`). The only question is who calls the builder — find your
case:

**On Metro: nobody. Skip this section.** The library's `DefaultGooseModule` builds it from
your features' contributions, which is the setup at the top of this README. The one reason to
touch the builder is an app-level override at assembly time, like a debug build swapping a
screen's UI — replace the default provider and add your override to the same inputs:

```kotlin
@ContributesTo(AppScope::class, replaces = [DefaultGooseModule::class])
interface GooseModule {
    companion object {
        @Provides @SingleIn(AppScope::class)   // keep @SingleIn, or awaited results break
        fun provideGoose(
            screenEntries: Map<KClass<*>, Provider<ScreenEntry>>,
            serializersModules: Set<SerializersModule>,
        ): Goose = Goose.Builder()
            .addScreens(screenEntries)
            .addSerializers(serializersModules)
            .addUi<ProfileScreen> { s, m -> DebugProfileUi(s, m) }   // wins over the contributed one
            .build()
    }
}
```

**On Dagger, Hilt, or kotlin-inject: your graph calls it.** This is your setup path — a
provider like the one above in your own component, fed by your own multibindings, with screens
registered as `screenUi { }` entries. No Metro in your build.

**With no DI: you call it.** In `Application.onCreate`, entries closing over whatever you use
instead, and the result is what you hand to `GooseGraphHolder` / `GooseCompositionLocals`:

```kotlin
val goose = Goose.Builder()
    .addUi<ProfileScreen> { screen, modifier -> ProfileUi(screen, modifier, myFactory()) }
    .build()
```

The `@GooseUi` codegen and session scopes stay Metro-only. Everything else — typed navigation,
results, fragment hosting, persistence — runs on a built `Goose`.

## Custom hosts and embedding

Per-screen and host-wide navigation overrides are migration step 5. `installGooseNavigator`
also takes a custom FragmentManager for nested stack ownership.

Embedding without navigating works too: attach a host fragment in your own container with no
back stack entry, `MyHostFragment().withGooseScreen(screen)` (or
`ScreenFragment.newInstance(childFragmentManager, screen)`). It finds the nearest navigator
and scope by walking up the parent-fragment chain.

## Animations

- **Host defaults**: `TabbedGooseContent(tabs, defaultTransitions = rememberSlideScreenTransitions())`.
  Slides with predictive back preview (opt into `enableOnBackInvokedCallback` in the
  manifest). The remember variant mirrors the slide under RTL layouts; the raw
  `SlideScreenTransitions` object is the fixed-LTR motion.
- **Per screen**: implement `ScreenTransitions` on the screen class. Stack roots are the one
  place this is ignored: a root changing at the top is a tab switch or reset, and the host
  crossfades any change landing on a root. Landing on a stack with screens pushed above its
  root plays the top screen's motion instead.
- **Shared elements**: `Modifier.sharedScreenElement(key)` on both screens, key declared in
  `:api`. Use `sharedScreenBounds` for text that changes size, so glyphs never crop.
- **Dialogs**: mark the screen `OverlayScreen` and it renders as a dialog over the previous
  screen, same stack, same result semantics. Window config via `dialogProperties()` on the
  screen. Both hosts honor it: a Compose host renders a dialog scene, and a fragment host
  mid-migration shows the same screen in a built-in `ScreenDialogFragment`, so converting a
  dialog screen changes nothing for the callers still living in fragments.

```kotlin
@Serializable
data class CheckoutScreen(val itemId: String) : Screen, ScreenTransitions {
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}
```

## Presentations

Ten bottom-sheet screens should not carry ten copies of the same motion. When a family of
screens appears the same way, define the presentation once and point each screen at it:

```kotlin
// design-system module, defined once
object BottomSheet : Presentation, ScreenTransitions {
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}

// any :api module
@Serializable
data class HelpScreen(val topic: String) : PresentedScreen {
    override val presentation get() = BottomSheet   // a getter: behavior, not state
}
```

A presentation opts into behavior by implementing facets. `ScreenTransitions` and `Overlay`
(the dialog facet behind `OverlayScreen`) are values, so Compose hosts consume them with no
registration. A facet the screen implements itself still beats its presentation's, which
beats the host default.

Fragment hosts mid-migration need FragmentManager mechanics, so custom fragment behavior
binds once per presentation type instead of once per screen:

```kotlin
@GoosePresentationNavigation(BottomSheet::class)
class BottomSheetNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        // request.presentation carries the token; a data-class token carries its knobs
    }
}
```

Presentations carrying only the `Overlay` facet skip even that: the fragment host shows them
in its dialog host automatically, exactly like `OverlayScreen`.

## Going deeper

- [DESIGN.md](DESIGN.md): every sharp edge, with reasoning.
- [docs/VIEWMODEL_CONTRACT.md](docs/VIEWMODEL_CONTRACT.md): the screen-scoped ViewModel
  lifecycle and the tests that pin it.
- [Gaggle](samples/gaggle): one mid-migration shop app exercising everything above, with a
  claim-by-claim test map in [samples/README.md](samples/README.md).
- [dagger-interop](samples/dagger-interop): goose next to a real Dagger component.

## License

[Apache 2.0](LICENSE)
