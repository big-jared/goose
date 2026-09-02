# The samples

Two apps. **Gaggle** (`samples/gaggle`) is one in-depth, mid-migration shop app that exercises
the whole library; **dagger-interop** (`samples/dagger-interop`) is deliberately tiny and proves
goose composes with an existing Dagger graph.

Gaggle is multi-module by construction (auth/catalog/cart features with :api/:impl splits, plus
the app shell owning the legacy corner), button-only (no text input anywhere), and every claim
below maps to the file that demonstrates it and the test that pins it. Tests live in
`samples/gaggle/app/src/test`.

| Claim | Demonstrated in | Pinned by |
|---|---|---|
| Login gates a session child graph; logout disposes it | `auth/api/Session.kt`, `MainActivity.kt` | `GaggleFlowTest.loginAndLogout`, `GaggleHardeningTest.relogingInGetsAFreshSession` |
| `@GooseUi` wires state + VM by type | `catalog/impl/CatalogFeature.kt` | every test, transitively |
| Mavericks `Async` incl. Fail + retry | `CatalogRepository.loadDeal`, `CatalogUi` | `GaggleFlowTest.asyncFailThenRetry` |
| Scope-registered screens + cross-feature session deps | `ProductFeature.kt` (catalog registers into LoggedInScope, injects the cart) | `GaggleFlowTest.addToCartAcrossFeatures` |
| Shared elements with :api-declared keys, multiple per screen, multiple origins, scale-to-bounds for growing text | `ProductImageKey` + `ProductTitleKey`; `CatalogUi` (rows AND the deal banner) / `ProductUi` (`sharedScreenBounds`) | `GaggleFlowTest.dealBannerOpensProduct`, rendered in every product test |
| Observable cart state: add-to-cart flips to "In cart · n" | `SessionCart` / `CartMutator.quantityOf`, `AddToCartButton` in `ProductFeature.kt` | `GaggleShopTest.addToCartReflectsCartState`, `addToCartStateSurvivesReentry`, `SessionCartTest` |
| Reviews: seeded aggregates, custom-drawn rating chart, write-review typed result | `ReviewsRepository`, `ReviewFeature.kt` (Canvas bars + form), `ProductHolder` | `GaggleShopTest.writeReviewRoundTrip`, `ReviewsRepositoryTest` |
| A real chat: scoped deps (agent + session) injected into a fragment-hosted VM, deterministic replies | `SupportFlow.kt` (`SupportAgent`, `SupportChatViewModel`) | `GaggleShopTest.supportChatAgentReplies`, `SupportAgentTest`, `GaggleHardeningTest.supportScopeAndVmContractAcrossFragmentBoundary` |
| Embedding a screen as a NESTED fragment (no back stack entry) | `SupportFlowFragment.onViewCreated` + `SupportStatusPanelScreen` | `GaggleShopTest.embeddedStatusPanelResolvesScopedSession` |
| Per-push identity for equal screen values | `ProfileFeature.kt` ("Open stats again") | `GaggleHardeningTest.equalStatsScreensAreIndependent`, `rapidDoubleTapIsSafe` |
| StateHolder (presenter without Mavericks) | `TeamStatsHolder`, `CartHolder` | `equalStatsScreensAreIndependent`, every cart result test |
| Typed results: picker, dialog, wizard | `cart/api/CartScreens.kt`, `CheckoutFeature.kt` | `GaggleFlowTest.removeDialogResult`, `checkoutEndToEndIntoLegacyOrderHistory` |
| goToForResult awaited from RETAINED presenters | `CartHolder`, `CheckoutFlowViewModel.chooseAddress` | `checkoutEndToEndIntoLegacyOrderHistory`, `wizardSurvivesRecreationAtEveryStep` |
| Nested flow + flow-shared VM + @PersistState | `CheckoutFeature.kt` | `wizardSurvivesRecreationAtEveryStep`, `tools/process-death-test.sh` |
| Nested child scope (checkout inside session) | `CheckoutScope.kt`, `GiftNoteStepUi` | `checkoutEndToEndIntoLegacyOrderHistory` |
| Retained child graphs across rotation | `CheckoutUi` (`rememberRetainedGraph`) | `wizardSurvivesRecreationAtEveryStep` |
| OverlayScreen dialog + dialogProperties | `RemoveItemScreen`, `RemoveItemUi` | `GaggleFlowTest.removeDialogResult` |
| Custom dialog windows: full-width peek, forced-choice confirm | `ProductPeekScreen`/`ProductPeekUi` (`usePlatformDefaultWidth = false`, pop-then-push promotion), `SignOutConfirmScreen`/`SignOutConfirmUi` (`dismissOnClickOutside = false`) | `peekDialogPromotesToFullPage`, `signOutConfirmStayKeepsSession`, `loginAndLogout` |
| Host default transitions (side-to-side slides + predictive back preview) | `MainActivity.kt` / `CheckoutUi` (`defaultTransitions = SlideScreenTransitions`), manifest `enableOnBackInvokedCallback` | rendered by every test |
| ScreenTransitions overriding the default (modal slide-up wizard + review form, scale on stats) | `CheckoutScreen` in `cart/api`, `WriteReviewScreen` in `catalog/api`, `TeamStatsScreen` in `auth/api` | rendered by every checkout and review test |
| Tabs: independent stacks, cross-stack `switchTo(...).goTo(...)` | `MainActivity.kt`, `CartUi` ("View order history") | `tabStacksSurviveSwitchAndRecreation`, `checkoutEndToEndIntoLegacyOrderHistory` |
| Deep links: cold start parks until login, warm jumps tabs | `MainActivity.handleDeepLink` | `coldDeepLinkParksUntilLogin`, `warmDeepLinkJumpsTabs` |
| Typed legacy fragments on Nav3 (Parcelable args) | `legacy/LegacyFragments.kt` | `checkoutEndToEndIntoLegacyOrderHistory`, `legacyTermsTypedArgsSurviveRecreation` |
| Child scope + VM contract across a FragmentManager | `legacy/SupportFlow.kt` | `supportScopeAndVmContractAcrossFragmentBoundary` |
| `@GooseFragmentBinder`: migrated screen navigates by typed screen to a legacy fragment, legacy `popBackStack()` resumes it | `SupportFaqBinder` / `SupportFaqFragment` in `legacy/SupportFlow.kt` | `GaggleShopTest.faqBinderPushesLegacyFragmentAndLegacyPopResumesChat` |
| `@GooseFragmentNavigation`: a screen shown as a legacy DialogFragment instead of a transaction | `SupportHoursNavigation` in `legacy/SupportFlow.kt` | `GaggleShopTest.hoursNavigationOverrideShowsLegacyDialog` |
| Abuse: double-tap, back-spam, 11-deep stacks | the hardening suite | `GaggleHardeningTest`, throughout |
| Process death: stacks + @PersistState resume after re-login | everything above | `tools/process-death-test.sh` (on a device) |

Maestro flows for the same core workflows live in `.maestro/` (see the root README).
