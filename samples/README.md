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
| Shared elements with :api-declared keys, multiple per screen, multiple origins | `ProductImageKey` + `ProductTitleKey`; `CatalogUi` (rows AND the deal banner) / `ProductUi` | `GaggleFlowTest.dealBannerOpensProduct`, rendered in every product test |
| Per-push identity for equal screen values | `ProfileFeature.kt` ("Open stats again") | `GaggleHardeningTest.equalStatsScreensAreIndependent`, `rapidDoubleTapIsSafe` |
| StateHolder (presenter without Mavericks) | `TeamStatsHolder`, `CartHolder` | `equalStatsScreensAreIndependent`, every cart result test |
| Typed results: picker, dialog, wizard | `cart/api/CartScreens.kt`, `CheckoutFeature.kt` | `GaggleFlowTest.removeDialogResult`, `checkoutEndToEndIntoLegacyOrderHistory` |
| goToForResult awaited from RETAINED presenters | `CartHolder`, `CheckoutFlowViewModel.chooseAddress` | `checkoutEndToEndIntoLegacyOrderHistory`, `wizardSurvivesRecreationAtEveryStep` |
| Nested flow + flow-shared VM + @PersistState | `CheckoutFeature.kt` | `wizardSurvivesRecreationAtEveryStep`, `tools/process-death-test.sh` |
| Nested child scope (checkout inside session) | `CheckoutScope.kt`, `GiftNoteStepUi` | `checkoutEndToEndIntoLegacyOrderHistory` |
| Retained child graphs across rotation | `CheckoutUi` (`rememberRetainedGraph`) | `wizardSurvivesRecreationAtEveryStep` |
| OverlayScreen dialog + dialogProperties | `RemoveItemScreen`, `RemoveItemUi` | `GaggleFlowTest.removeDialogResult` |
| Custom dialog windows: full-width peek, forced-choice confirm | `ProductPeekScreen`/`ProductPeekUi` (`usePlatformDefaultWidth = false`, pop-then-push promotion), `SignOutConfirmScreen`/`SignOutConfirmUi` (`dismissOnClickOutside = false`) | `peekDialogPromotesToFullPage`, `signOutConfirmStayKeepsSession`, `loginAndLogout` |
| ScreenTransitions (modal slide, fade+scale beside shared elements, predictive back) | `CheckoutScreen` in `cart/api`, `ProductScreen` in `catalog/api`, `TeamStatsScreen` in `auth/api` | rendered by every checkout and product test |
| Tabs: independent stacks, cross-stack `switchTo(...).goTo(...)` | `MainActivity.kt`, `CartUi` ("View order history") | `tabStacksSurviveSwitchAndRecreation`, `checkoutEndToEndIntoLegacyOrderHistory` |
| Deep links: cold start parks until login, warm jumps tabs | `MainActivity.handleDeepLink` | `coldDeepLinkParksUntilLogin`, `warmDeepLinkJumpsTabs` |
| Typed legacy fragments on Nav3 (Parcelable args) | `legacy/LegacyFragments.kt` | `checkoutEndToEndIntoLegacyOrderHistory`, `legacyTermsTypedArgsSurviveRecreation` |
| Child scope + VM contract across a FragmentManager | `legacy/SupportFlow.kt` | `supportScopeAndVmContractAcrossFragmentBoundary` |
| Abuse: double-tap, back-spam, 11-deep stacks | the hardening suite | `GaggleHardeningTest`, throughout |
| Process death: stacks + @PersistState resume after re-login | everything above | `tools/process-death-test.sh` (on a device) |

Maestro flows for the same core workflows live in `.maestro/` (see the root README).
