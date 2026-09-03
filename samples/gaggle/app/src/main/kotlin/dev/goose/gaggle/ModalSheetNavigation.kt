package dev.goose.gaggle

import androidx.fragment.app.commit
import dev.goose.fragment.FragmentNavigationRequest
import dev.goose.fragment.FragmentScreenNavigation
import dev.goose.fragment.GoosePresentationNavigation
import dev.goose.gaggle.catalog.api.ModalSheet

/**
 * Demonstrates: `@GoosePresentationNavigation` — the fragment-host half of a shared
 * presentation, bound once for EVERY screen whose presentation is [ModalSheet] (compare
 * `@GooseFragmentNavigation`, which binds one screen). Compose hosts read the presentation's
 * ScreenTransitions facet directly and never see this class.
 *
 * The transaction is goose's default shape with the platform fade standing in for the sheet's
 * slide-up (fragment animations are anim resources, and the sample app ships none). Keeping
 * [FragmentNavigationRequest.backStackEntryName] as the `addToBackStack` name is what lets
 * awaited results resolve when the sheet pops.
 */
@GoosePresentationNavigation(ModalSheet::class)
class ModalSheetNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        request.fragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out,
            )
            replace(request.containerId, request.createFragment())
            addToBackStack(request.backStackEntryName)
        }
    }
}
