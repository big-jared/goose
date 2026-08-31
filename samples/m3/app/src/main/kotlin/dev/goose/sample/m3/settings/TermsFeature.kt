package dev.goose.sample.m3.settings

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
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable

/**
 * TYPED legacy-fragment hosting: the screen is a normal feature-owned @Serializable data class,
 * and the fragment gets real typed arguments (including a Parcelable) — no string maps, no
 * reflection. The screen value rides the persisted back stack, so recreation and process death
 * rebuild the equivalent fragment.
 */
@Serializable
data class TermsScreen(val termsId: String, val revision: Int) : Screen

/** A typical legacy argument model: Parcelable, exactly as mature fragments take them. */
data class TermsAuthor(val name: String) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeString(name)

    companion object CREATOR : Parcelable.Creator<TermsAuthor> {
        override fun createFromParcel(source: Parcel) = TermsAuthor(checkNotNull(source.readString()))
        override fun newArray(size: Int): Array<TermsAuthor?> = arrayOfNulls(size)
    }
}

@ContributesTo(AppScope::class)
interface TermsEntryModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(TermsScreen::class)
        fun termsEntry(): ScreenEntry = fragmentScreenEntry<TermsFragment, TermsScreen> { screen ->
            bundleOf(
                TermsFragment.ARG_TERMS_ID to screen.termsId,
                TermsFragment.ARG_REVISION to screen.revision,
                TermsFragment.ARG_AUTHOR to TermsAuthor("Legal Goose"),
            )
        }
    }
}

/** Unmigrated fragment reading TYPED arguments from its Bundle, as legacy fragments do. */
class TermsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val termsId = requireArguments().getString(ARG_TERMS_ID)
        val revision = requireArguments().getInt(ARG_REVISION)
        @Suppress("DEPRECATION")
        val author = requireArguments().getParcelable<TermsAuthor>(ARG_AUTHOR)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(TextView(context).apply {
                text = "Terms $termsId rev $revision by ${author?.name}"
                textSize = 18f
                gravity = Gravity.CENTER
            })
        }
    }

    companion object {
        const val ARG_TERMS_ID = "termsId"
        const val ARG_REVISION = "revision"
        const val ARG_AUTHOR = "author"
    }
}
