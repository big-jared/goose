# Design decisions

Answers to the hard questions, so they are decisions instead of accidents. Each item is marked
**fixed** (enforced in code, usually with a test), **decided** (a deliberate contract, documented
here and in KDoc), or **deferred** (real, tracked in TODO.md, not blocking).

## @GooseUi

1. **Supported grammar. Fixed.** `@GooseUi` requires a public or internal, top-level,
   non-suspend, non-generic, non-extension `@Composable` function, and its screen class must be
   `@Serializable` (caught at the annotation, not at the first state save). Anything else is a
   KSP error naming the rule. Private functions are rejected because the generated registration
   lives in a separate file; member and extension functions because the generated adapter has no
   receiver.

2. **Qualified dependencies. Fixed.** Metro qualifier annotations (any annotation itself
   annotated `@Qualifier`, such as `@Named`) on injected parameters are copied onto the generated
   provider parameter, including simple arguments (strings, numbers, class literals, arrays). A
   qualifier argument the generator cannot render is a compile error pointing at the hand-written
   `@Provides` form.

3. **Default parameter values. Decided.** Allowed, but the generated registration always
   supplies every argument; defaults are for previews and direct calls. The generator cannot
   know whether an omitted graph binding was intentional, so "missing binding" stays a Metro
   compile error rather than a silent fallback to the default.

4. **Same-name functions. Fixed within a module, decided across modules.** Two `@GooseUi`
   functions with the same name in one package are a KSP error telling you to rename one.
   Generated modules are named after the function (`ProfileUiGooseModule`) because readable
   names beat collision-proof mangled ones. Across Gradle modules the processor cannot see, so
   two modules sharing one package could still collide; the rule is one package per module,
   which the Android namespace every library module declares already encourages.

5. **Generated identifier hygiene. Fixed.** Generated lambda locals use reserved names
   (`gooseScreen`, `gooseModifier`); using those as parameter names is a compile error. Generated
   factory parameter names are de-duplicated against user parameter names.

6. **Factory selection. Fixed.** Only nested classifiers annotated `@AssistedFactory` are
   considered. Two matching create functions across them is a compile error; structural matching
   alone is no longer enough.

7. **Factory shape. Fixed.** The create function may be declared or inherited, and the factory
   may carry other non-abstract members. The requirement is exactly one abstract
   `(state, navigator) -> VM` function among the assisted factories.

8. **Exact state-type matching. Decided.** A state parameter must be exactly the ViewModel's
   state class. Assignability matching (interfaces, supertypes) invites ambiguity between two
   VMs whose states share an interface; exactness keeps the rule explainable in one sentence.

9. **Scope. Done.** `@GooseUi(GiftNoteScreen::class, scope = CheckoutScope::class)` registers a
   screen into a Metro child graph, consumed via `GooseScope` (item 36). The parameter was
   removed once, earlier, precisely because nothing could consume it; it returned together with
   the registries that read it, in the same change.

10. **Compile-testing fixtures. Deferred.** The grammar rules are enforced but exercised only
    through the samples (happy path) today. A compile-testing suite for the error cases is
    tracked in TODO.md; the blocker is a compile-testing harness compatible with this Kotlin/KSP2
    toolchain.

## Navigation and results

11. **Request identity. Fixed.** A result request is keyed by screen class + owning stack
    instance. Every stack host generates a saveable per-instance tag (`rememberSaveable` UUID
    keyed on the stack instance on the compose side, a retained-ViewModel UUID on the fragment
    side), so keys are stable across recreation but unique per stack, per activity, and per
    swapped-in stack. The tags are mandatory constructor parameters on the concrete navigators,
    so hand-built hosts cannot silently lose the isolation. Within one stack, same-class
    requests resolve LIFO, which is CORRECT for stack-disciplined destinations (a stack removes
    its most recent same-class screen first). Destinations that bypass the stack, i.e. custom
    fragment adapters showing dialogs or activities, do not get LIFO assumptions: an awaited
    navigation carries an opaque `ResultAwaiter` token explicitly, from `goToForResult` through
    the `goToAwaited` hook into that specific `FragmentNavigationRequest`, and `deliverResult`
    completes exactly that caller. A request created by plain `goTo` carries no awaiter at all,
    so it can never steal an outstanding same-class request's caller. The awaiter is one-shot
    and no-ops after cancellation; a navigation that throws unregisters its awaiter on the way
    out. The whole contract has direct JVM tests in `:runtime` (out-of-order answers, plain
    same-class pushes, double delivery, cancellation, failing navigation). Identity-based
    ROUTING KEYS were still rejected because a restored stack holds new-but-equal instances (a
    real bug, caught by test); the token complements keys, it does not replace them.

12. **Simultaneous activities. Fixed.** Covered by 11: two activities awaiting the same screen
    class no longer share a routing key, even though `ResultRouter` remains app-scoped.

13. **deliverResult one-shot. Fixed.** `FragmentNavigationRequest.deliverResult` delivers at
    most once; later calls no-op. A dismiss callback firing after a result callback cannot
    consume another pending request. The one-shot property lives in the `ResultAwaiter` itself
    (completion consumes the registration), and the router's queue protocol is internal: the
    token is the only cross-module surface, so adapters cannot misuse the queue.

14. **Adapters that forget to deliver. Decided.** Accepted adapter responsibility, stated in the
    `FragmentScreenNavigation` KDoc: push onto the FragmentManager back stack under
    `backStackEntryName` (delivery is then automatic on pop, whoever pops), or call
    `deliverResult` from your dismissal path. A forgotten delivery leaves the caller suspended
    until its presenter scope is cleared (screen popped, flow finished, activity done); the
    coroutine is cancelled then, so nothing leaks beyond that lifetime, but the caller does
    stay parked in the meantime.

15. **Typed answering. Decided.** `ScreenWithResult<R>` types the caller; the destination's
    `pop(result)` is untyped by design, because the destination's own screen type makes the
    expected result obvious at the call site and an enforcing API would need reified interface
    methods Kotlin does not have. A wrong-type answer surfaces as a cast error at the awaiting
    caller.

16. **Cancelled awaiters. Decided.** Cancellation unregisters the awaiter and leaves the screen
    alone; the user is still looking at it. When it eventually pops, delivery no-ops. This is the
    same contract as a coroutine-wrapped ActivityResult.

17. **Sequential consistency. Fixed.** Nav3 mutates synchronously. The fragment host queues
    commits, which made `goTo(A); pop()` in one main-loop turn read the stale pre-commit stack
    and drop the pop; `FragmentNavigator.pop` now flushes pending transactions first, covered
    by an m3 test. The flush executes any transactions the legacy app queued too, which is
    within FragmentManager's contract (commit promises only "as soon as possible on the main
    thread", so running earlier is always legal, via the public API that exists for exactly
    this). It is skipped when state is saved, and catches only IllegalStateException for the
    reentrant case, where the queue drains in order anyway. Writes remain strictly ordered
    through the FragmentManager queue; only observation timing differs between hosts.

18. **backStack introspection. Decided.** `Navigator.backStack` is best-effort, documented as
    such: the fragment host returns an empty list because Screen instances cannot be
    reconstructed from a restored FragmentManager. Mid-migration code should not introspect the
    legacy stack; post-migration hosts always support it.

19. **parent visibility. Decided.** Public. Hosts need it for wiring nested flows, and a feature
    deliberately escaping its flow boundary is making a legitimate (if unusual) navigation
    decision, not violating an invariant the library must guard.

20. **Thread contract. Fixed.** Concrete navigators now fail fast with a clear message when
    mutated off the main thread. ViewModels are unaffected: their `NavigatorHandle` dispatches
    to main from any thread.

## Identity and restoration

21. **Equal screens on one stack. Decided, documented limitation.** Two equal screen values on
    the same stack share entry state and therefore a ViewModel, inherited from Nav3's
    value-based `contentKey`. The mitigation is a distinguishing field on the screen. Solving it
    for real means per-push instance ids inside persisted keys, which changes screen equality
    semantics; deferred until Nav3 itself offers instance identity. Stated in the README, not
    just an internal comment.

22. **Saved-stack migration across releases. Fixed.** Restoration is resilient by design: a
    stack that cannot be decoded (renamed or removed screen class, incompatible field change,
    or a class-loading LinkageError from a broken split or static initializer) restarts at its
    initial roots instead of crash-looping. Covered by tests in `:runtime-nav3`.

23. **@SerialName and reflection. Fixed/decided.** The reflective fallback resolves default
    serial names, including nested classes (progressive dollar substitution to the JVM binary
    name, which was a real bug this review surfaced). A custom `@SerialName` is not resolvable
    reflectively and needs explicit `screenSerializers { }` registration; unregistered ones
    degrade to a fresh restart rather than a crash.

24. **R8. Fixed.** `:runtime` ships consumer keep rules (`-keepnames` for Screen
    implementations), so minified builds restore reflectively without app-side configuration.
    Apps registering every screen explicitly may drop the rule.

25. **Dynamic features. Decided.** A screen whose module is not loaded at restoration time
    resolves like a removed class: the stack restarts fresh. Loading the module and re-linking
    is app policy, not library policy.

26. **Process-death contract. Decided, stated in README.** Guaranteed: back stacks, selected
    tab, `@PersistState` fields, screen identity. Not guaranteed: suspended `goToForResult`
    callers (the coroutine dies with the process; a restart reads as "no answer", same as
    ActivityResult).

27. **Kill-and-restore testing. Done.** Two layers: the serialization layer is unit tested
    (encode, decode in a fresh context, corrupt-input degradation), and
    `tools/process-death-test.sh` performs the real thing on a device over adb: background,
    `am kill` (verified new pid), relaunch, assert the pushed stack and `@PersistState` fields
    restored. Host-side by necessity: instrumentation dies with the process it kills. Not a CI
    gate (needs an emulator); run it before releases.

## Tabs and deep links

28. **Tab specs. Fixed.** Duplicate tab keys are a construction error. A restored tab selection
    naming a tab that no longer exists falls back to the first tab instead of crashing.

29. **Cross-tab navigation. Fixed.** `TabNavigator.goTo(tab, screen)` atomically selects the tab
    and pushes, with no intermediate frame of the tab's old top.

30. **Equal roots across tabs. Fixed.** Distinct root screens are a construction requirement:
    all tabs share one NavDisplay, so equal roots would collide in entry state ownership.

31. **onNewIntent. Decided, README example.** A new deep link into a running activity is a
    navigator mutation like any other: parse the intent, then `resetRoot` + `goTo` (or
    `goTo(tab, screen)`) to build the target stack. `rememberGooseBackStack(initial)` is only
    the cold-start half.

## Presentation and dependency boundaries

32. **Compose in :api modules. Decided.** Api modules stay free of compose UI, but presentation
    behavior riding on the screen contract (`ScreenTransitions` transforms, `OverlayScreen`
    dialog properties) is allowed deliberately: the point of putting it on the screen is that
    any module can push the screen and get its declared presentation. The convention-plugin
    comment now says exactly this.

33. **Screen-owned transitions. Decided.** Transitions belong to the screen type, for
    consistency from every call site. A call-site override would reintroduce the "every caller
    styles it differently" problem this API removes. If a screen genuinely presents two ways,
    that is two screens (or a screen field). Host-level default overrides are deferred.

34. **Root overlays degrade. Decided.** A dialog screen landing at a stack root (deep link,
    resetRoot) renders full-screen instead of failing: Nav3 requires a non-empty scene beneath
    an overlay, and crashing on a deep link is the worst possible answer. Documented in KDoc.

35. **Shared-element keys stay Any. Decided.** Compose's shared-element API keys on Any; a
    library-side wrapper would add a type without adding safety (equality is still the
    mechanism). The convention that prevents collisions is declaring key TYPES in :api modules,
    which namespaces them by class.

36. **Registry caching vs session scopes. Done.** Registries form a chain mirroring the graph
    tree. The app graph provides the root; `GooseScope(childGraph)` builds a child registry from
    the graph's `GooseScopeAccessors` contributions, resolving locally first with parent
    fallback, and `GooseContent` uses the nearest active registry. Caching is per registry, and
    the child registry is remembered in the scope's composition: leaving the subtree drops the
    registry, its cached entries, and (with the graph) every session-scoped dependency. The
    graph itself is created with `rememberRetainedGraph`, retained in the owning entry's
    ViewModelStore: it must live exactly as long as the ViewModels it was injected into, or a
    rotation would split session dependencies between old (VM-held) and new (recomposed)
    graphs; a rotation test pins this. AutoCloseable graphs are closed on release. The scope
    does not cross a FragmentManager push (a ScreenFragment builds a fresh composition from the
    app graph); scoped screens stay inside compose-hosted flows during migration, and the
    registry's miss message names GooseScope. Screens register
    into a scope with `@GooseUi(scope = ...)` or hand-written contributions. Duplicate ownership
    of one screen key across parent and child is a Metro compile error (multibindings merge into
    the child map), not a silent shadow. Exercised end to end by the m2 checkout session sample
    and its Robolectric test (shared within a flow, fresh per flow, parent fallback).

36b. **Dagger/Hilt adoption. Done.** The m4 sample proves the biggest adoption question: a
    Metro graph `@Includes` an existing Dagger component, whose public accessors become
    ordinary bindings for goose screens and ViewModels; Dagger's KSP processor, Metro's
    compiler plugin, and goose-compiler coexist in one module. Tested. Hilt exposes bindings
    the same way via an accessor interface; Metro's `includeDagger()` annotation interop
    covers classes still carrying javax.inject annotations.

37. **Flavors and controlled replacement. Decided.** Metro's own mechanisms are the answer:
    `replaces = [...]` on a contribution swaps a registration wholesale, and flavor source sets
    can contribute different entries per variant. Goose adds no second replacement system; two
    unmanaged entries for one key stays a compile error.
