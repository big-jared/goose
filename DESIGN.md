# Design decisions

Answers to the hard questions, so they are decisions instead of accidents. Each item is marked
**fixed** (enforced in code, usually with a test), **decided** (a deliberate contract, documented
here and in KDoc), or **deferred** (real, tracked in TODO.md, not blocking).

## @GooseUi

1. **Supported grammar. Fixed.** `@GooseUi` requires a public or internal, top-level,
   non-suspend, non-generic, non-extension `@Composable` function. Anything else is a KSP error
   naming the rule. Private functions are rejected because the generated registration lives in a
   separate file; member and extension functions because the generated adapter has no receiver.

2. **Qualified dependencies. Fixed.** Metro qualifier annotations (any annotation itself
   annotated `@Qualifier`, such as `@Named`) on injected parameters are copied onto the generated
   provider parameter, including simple arguments (strings, numbers, class literals, arrays). A
   qualifier argument the generator cannot render is a compile error pointing at the hand-written
   `@Provides` form.

3. **Default parameter values. Decided.** Allowed, but the generated registration always
   supplies every argument; defaults are for previews and direct calls. The generator cannot
   know whether an omitted graph binding was intentional, so "missing binding" stays a Metro
   compile error rather than a silent fallback to the default.

4. **Same-name functions. Fixed.** Two `@GooseUi` functions with the same name in one package
   are a KSP error telling you to rename one. Generated modules are named after the function
   (`ProfileUiGooseModule`) because readable names beat collision-proof mangled ones; the
   collision is detected instead of tolerated.

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

9. **Scope. Fixed.** `@GooseUi(ProfileScreen::class, scope = LoggedInScope::class)` contributes
   to that scope; the default is `AppScope`. (`Unit::class` is the default sentinel so `:runtime`
   stays Metro-free.)

10. **Compile-testing fixtures. Deferred.** The grammar rules are enforced but exercised only
    through the samples (happy path) today. A compile-testing suite for the error cases is
    tracked in TODO.md; the blocker is a compile-testing harness compatible with this Kotlin/KSP2
    toolchain.

## Navigation and results

11. **Request identity. Fixed.** A result request is keyed by screen class + owning stack
    instance. Every stack host generates a saveable per-instance tag (`rememberSaveable` UUID on
    the compose side, a retained-ViewModel UUID on the fragment side), so keys are stable across
    recreation but unique per stack and per activity. Within one stack, same-class requests
    resolve LIFO, which matches stack discipline. Identity-based keys were rejected because a
    restored stack holds new-but-equal instances (this was a real bug, caught by test).

12. **Simultaneous activities. Fixed.** Covered by 11: two activities awaiting the same screen
    class no longer share a routing key, even though `ResultRouter` remains app-scoped.

13. **deliverResult one-shot. Fixed.** `FragmentNavigationRequest.deliverResult` delivers at
    most once; later calls no-op. A dismiss callback firing after a result callback cannot
    consume another pending request.

14. **Adapters that forget to deliver. Decided.** Accepted adapter responsibility, stated in the
    `FragmentScreenNavigation` KDoc: push onto the FragmentManager back stack under
    `backStackEntryName` (delivery is then automatic on pop, whoever pops), or call
    `deliverResult` from your dismissal path. The backstop is that awaiting is done from
    presenter coroutine scopes, which cancel when the caller is cleared, so a forgotten delivery
    leaks nothing; it just resumes nobody.

15. **Typed answering. Decided.** `ScreenWithResult<R>` types the caller; the destination's
    `pop(result)` is untyped by design, because the destination's own screen type makes the
    expected result obvious at the call site and an enforcing API would need reified interface
    methods Kotlin does not have. A wrong-type answer surfaces as a cast error at the awaiting
    caller.

16. **Cancelled awaiters. Decided.** Cancellation unregisters the awaiter and leaves the screen
    alone; the user is still looking at it. When it eventually pops, delivery no-ops. This is the
    same contract as a coroutine-wrapped ActivityResult.

17. **Sequential consistency. Decided.** Both hosts execute commands in call order on the main
    thread: Nav3 synchronously against the snapshot list, the fragment host through the
    FragmentManager's transaction queue (async but strictly ordered). What differs is only when
    effects become observable, which navigation logic should not depend on.

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
    stack that cannot be decoded (renamed or removed screen class, incompatible field change)
    restarts at its initial roots instead of crash-looping. Covered by tests in `:runtime-nav3`.

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

27. **Kill-and-restore testing. Partially addressed, deferred.** The serialization layer of
    process death is now tested directly (encode, decode in a fresh context, corrupt-input
    degradation). A true kill-and-relaunch instrumented test remains tracked in TODO.md; this
    machine's emulator wedge makes it a poor gate.

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

36. **Registry caching vs session scopes. Decided.** `ScreenEntry` instances are app-scoped and
    capture app-scoped factories; session-scoped dependencies must be reached through accessors
    resolved at composition/creation time, not captured at registration. First-class session
    graphs (`@GraphExtension`, and `@GooseUi(scope = ...)` registrations living in a child
    graph's registry) are the deferred design in TODO.md.

37. **Flavors and controlled replacement. Decided.** Metro's own mechanisms are the answer:
    `replaces = [...]` on a contribution swaps a registration wholesale, and flavor source sets
    can contribute different entries per variant. Goose adds no second replacement system; two
    unmanaged entries for one key stays a compile error.
