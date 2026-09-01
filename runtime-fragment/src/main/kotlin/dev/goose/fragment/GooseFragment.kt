package dev.goose.fragment

import dev.goose.runtime.Screen
import kotlin.reflect.KClass

/**
 * The concise form of [fragmentScreenEntry]: annotate a LEGACY fragment with the typed screen
 * that opens it, and goose-compiler generates the whole registration — the contributed module,
 * the multibinding, and a Bundle built from the screen's constructor properties BY NAME:
 *
 * ```
 * @Serializable data class TermsScreen(val termsId: String, val revision: Int) : Screen
 *
 * @GooseFragment(TermsScreen::class)
 * class TermsFragment : Fragment() {
 *     // requireArguments().getString("termsId"), getInt("revision")
 * }
 * ```
 *
 * The name convention is the whole contract: the fragment reads each argument under the
 * screen property's own name. A fragment whose keys differ, or that needs Bundle entries
 * beyond the screen's fields (a Parcelable the screen doesn't carry, say), keeps the explicit
 * `fragmentScreenEntry { screen -> bundleOf(...) }` registration instead.
 *
 * [scope] mirrors `@GooseUi`: the Metro scope the entry contributes to, AppScope by default.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GooseFragment(
    val screen: KClass<out Screen>,
    val scope: KClass<*> = Unit::class,
)
