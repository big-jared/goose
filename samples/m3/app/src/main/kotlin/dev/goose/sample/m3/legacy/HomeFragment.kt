package dev.goose.sample.m3.legacy

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksView
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import androidx.fragment.app.FragmentActivity
import dev.goose.fragment.gooseNavigator
import dev.goose.runtime.Navigator
import dev.goose.sample.m3.DetailScreen
import dev.goose.sample.m3.ProfileScreen
import dev.goose.sample.m3.settings.SettingsActivity
import kotlinx.coroutines.launch

data class HomeState(
    val lastResult: String? = null,
) : MavericksState

/**
 * A legacy-style VM, mid-migration: still created through Mavericks' fragment path (companion
 * factory, `fragmentViewModel()`), but its navigation already goes through the injected
 * [Navigator] — so when this screen's view migrates to compose, the VM file doesn't change.
 */
class HomeViewModel(
    initialState: HomeState,
    private val navigator: Navigator,
) : MavericksViewModel<HomeState>(initialState) {

    fun openLegacyDetail() {
        viewModelScope.launch {
            val result = navigator.goToForResult(DetailScreen("42"))
            setState { copy(lastResult = "detail → " + (result?.message ?: "no answer")) }
        }
    }

    fun openMigratedProfile() {
        viewModelScope.launch {
            val result = navigator.goToForResult(ProfileScreen("ada"))
            setState { copy(lastResult = "profile → counter was " + (result?.counterAtClose ?: "?")) }
        }
    }

    companion object : MavericksViewModelFactory<HomeViewModel, HomeState> {
        override fun create(viewModelContext: ViewModelContext, state: HomeState): HomeViewModel {
            // Legacy hand-wiring: the navigator comes off the host activity.
            val navigator = (viewModelContext.activity as FragmentActivity).gooseNavigator
            return HomeViewModel(state, navigator)
        }
    }
}

/** A 100% legacy MavericksView fragment — programmatic views, fragmentViewModel, invalidate(). */
class HomeFragment : Fragment(), MavericksView {

    private val viewModel: HomeViewModel by fragmentViewModel()
    private val counterViewModel: CounterViewModel by activityViewModel()

    private lateinit var resultText: TextView
    private lateinit var counterButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.WHITE)
        setPadding(48, 96, 48, 48)

        addView(TextView(context).apply {
            text = "Legacy Home (fragment)"
            textSize = 24f
        })
        resultText = TextView(context).apply { text = "no result yet"; textSize = 16f }
        addView(resultText)
        counterButton = Button(context).apply {
            setOnClickListener { counterViewModel.increment() }
        }
        addView(counterButton)
        addView(Button(context).apply {
            text = "Open detail (legacy fragment)"
            setOnClickListener { viewModel.openLegacyDetail() }
        })
        addView(Button(context).apply {
            text = "Open profile (compose screen)"
            setOnClickListener { viewModel.openMigratedProfile() }
        })
        addView(Button(context).apply {
            text = "Settings (converted flow)"
            setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        })
    }

    override fun invalidate() {
        withState(viewModel) { state ->
            resultText.text = state.lastResult ?: "no result yet"
        }
        withState(counterViewModel) { counter ->
            counterButton.text = "Shared counter: ${counter.count} (tap +1)"
        }
    }
}
