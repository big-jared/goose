package dev.goose.gaggle.legacy

import android.graphics.Color
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dev.goose.fragment.fragmentScreenEntry
import dev.goose.gaggle.auth.api.OrderHistoryScreen
import dev.goose.gaggle.auth.api.TermsScreen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable

/**
 * Demonstrates: the legacy corner every mid-migration app has. These fragments are UNMIGRATED
 * (programmatic views, Bundle args) yet ride the Nav3 back stack through typed screens: the
 * fragmentScreenEntry registrations build each Bundle from the screen's typed fields, and
 * recreation/process death rebuild the same fragment from the restored screen.
 */
@ContributesTo(AppScope::class)
interface LegacyEntriesModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(OrderHistoryScreen::class)
        fun orderHistoryEntry(): ScreenEntry =
            fragmentScreenEntry<OrderHistoryFragment, OrderHistoryScreen> { screen ->
                bundleOf(OrderHistoryFragment.ARG_COUNT to screen.orderCount)
            }

        @Provides
        @IntoMap
        @ClassKey(TermsScreen::class)
        fun termsEntry(): ScreenEntry =
            fragmentScreenEntry<TermsFragment, TermsScreen> { screen ->
                bundleOf(
                    TermsFragment.ARG_TERMS_ID to screen.termsId,
                    TermsFragment.ARG_REVISION to screen.revision,
                    TermsFragment.ARG_AUTHOR to TermsAuthor("Legal Goose"),
                )
            }
    }
}

class OrderHistoryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = legacyText("Order history: ${requireArguments().getInt(ARG_COUNT)} order(s)\n(legacy fragment)")

    companion object {
        const val ARG_COUNT = "count"
    }
}

/** A typical legacy argument model: Parcelable, exactly as mature fragments take them. */
data class TermsAuthor(val name: String) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeString(name)

    companion object CREATOR : Parcelable.Creator<TermsAuthor> {
        override fun createFromParcel(source: Parcel) = TermsAuthor(checkNotNull(source.readString()))
        override fun newArray(size: Int): Array<TermsAuthor?> = arrayOfNulls(size)
    }
}

class TermsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val id = requireArguments().getString(ARG_TERMS_ID)
        val revision = requireArguments().getInt(ARG_REVISION)
        @Suppress("DEPRECATION")
        val author = requireArguments().getParcelable<TermsAuthor>(ARG_AUTHOR)
        return legacyText("Terms $id rev $revision by ${author?.name}\n(legacy fragment)")
    }

    companion object {
        const val ARG_TERMS_ID = "termsId"
        const val ARG_REVISION = "revision"
        const val ARG_AUTHOR = "author"
    }
}

private fun Fragment.legacyText(text: String): View =
    LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            this.text = text
            textSize = 18f
            gravity = Gravity.CENTER
        })
    }
