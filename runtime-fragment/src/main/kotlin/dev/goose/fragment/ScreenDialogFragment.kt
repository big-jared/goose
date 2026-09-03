package dev.goose.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.DialogFragment
import dev.goose.runtime.effectiveOverlay

/**
 * The fragment-host twin of the Nav3 host's dialog scene: hosts a migrated compose screen with
 * the [dev.goose.runtime.Overlay] facet in a DialogFragment, so marking a screen `OverlayScreen`
 * (or giving its [dev.goose.runtime.Presentation] the facet) shows the SAME dialog whether a
 * Compose host or a legacy FragmentManager reaches it. [FragmentNavigator] uses it automatically
 * for unmapped overlay screens; apps whose dialogs need their own base class register a
 * replacement via `installGooseNavigator(dialogHost = ...)` with [gooseScreenView] +
 * [applyGooseDialogProperties] as the implementation.
 *
 * Instantiated through the FragmentManager's own FragmentFactory (like [ScreenFragment]), so it
 * recreates correctly after rotation and process death; the screen rides [Fragment.getArguments].
 */
class ScreenDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = gooseScreenView()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { applyGooseDialogProperties(it) }
}

/**
 * Applies the hosted screen's [dev.goose.runtime.Overlay.dialogProperties] to a platform
 * [Dialog], mapping the compose-world properties where they overlap: outside-tap dismissal,
 * back-press dismissal (the platform's single cancelable flag, so disabling back-press also
 * disables outside-tap), and the secure-window policy. Sizing needs no mapping — the platform
 * dialog window wraps whatever the screen's composable measures. Public for custom dialog
 * hosts built on [gooseScreenView].
 */
fun DialogFragment.applyGooseDialogProperties(dialog: Dialog) {
    val screen = ScreenBundler.fromBundle(requireArguments())
    val properties = screen.effectiveOverlay()?.dialogProperties() ?: return
    if (!properties.dismissOnClickOutside) dialog.setCanceledOnTouchOutside(false)
    if (!properties.dismissOnBackPress) isCancelable = false
    val window = dialog.window ?: return
    val secure = WindowManager.LayoutParams.FLAG_SECURE
    when (properties.securePolicy) {
        SecureFlagPolicy.SecureOn -> window.addFlags(secure)
        SecureFlagPolicy.SecureOff -> window.clearFlags(secure)
        SecureFlagPolicy.Inherit -> {
            val hostFlags = activity?.window?.attributes?.flags ?: 0
            if (hostFlags and secure != 0) window.addFlags(secure)
        }
    }
}
