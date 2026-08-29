package dev.goose.sample.m3.legacy

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.airbnb.mvrx.Mavericks
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksView
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import dev.goose.fragment.FragmentNavigatorOwner
import dev.goose.fragment.ScreenFragmentBinder
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.sample.m3.DetailResult
import dev.goose.sample.m3.DetailScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

data class DetailState(val id: String = "") : MavericksState {
    @Suppress("unused")
    constructor(args: DetailScreen) : this(id = args.id)
}

/** Legacy VM answering through the navigator — result flows to fragment AND compose callers. */
class DetailViewModel(
    initialState: DetailState,
    private val navigator: Navigator,
) : MavericksViewModel<DetailState>(initialState) {

    fun sendResult() {
        viewModelScope.launch {
            navigator.pop(DetailResult(message = "hello from legacy detail ${awaitState().id}"))
        }
    }

    companion object : MavericksViewModelFactory<DetailViewModel, DetailState> {
        override fun create(viewModelContext: ViewModelContext, state: DetailState): DetailViewModel {
            val navigator = (viewModelContext.activity as FragmentNavigatorOwner).gooseNavigator
            return DetailViewModel(state, navigator)
        }
    }
}

class DetailFragment : Fragment(), MavericksView {

    private val viewModel: DetailViewModel by fragmentViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.WHITE)
        setPadding(48, 96, 48, 48)
        addView(TextView(context).apply { text = "Legacy Detail (fragment)"; textSize = 24f })
        addView(Button(context).apply {
            text = "Send result and close"
            setOnClickListener { viewModel.sendResult() }
        })
    }

    override fun invalidate() = Unit

    companion object {
        fun newInstance(screen: DetailScreen): DetailFragment = DetailFragment().apply {
            arguments = bundleOf(Mavericks.KEY_ARG to screen)
        }
    }
}

/** Maps DetailScreen to its legacy fragment while it awaits migration. */
@ContributesIntoMap(AppScope::class)
@ClassKey(DetailScreen::class)
@Inject
class DetailFragmentBinder : ScreenFragmentBinder {
    override fun createFragment(screen: Screen): DetailFragment =
        DetailFragment.newInstance(screen as DetailScreen)
}
