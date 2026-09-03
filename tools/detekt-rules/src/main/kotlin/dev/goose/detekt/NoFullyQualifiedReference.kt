package dev.goose.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Flags references written with their full package path in code (`androidx.fragment.app.FragmentManager`,
 * `kotlinx.coroutines.CoroutineScope(...)`) where an import would do. Detection is syntactic: a dotted
 * chain of lowercase segments starting with a known package root, followed by a capitalized name. When
 * the qualification is deliberate (two imports would clash), use an import alias instead, or suppress
 * with `@Suppress("NoFullyQualifiedReference")`.
 */
class NoFullyQualifiedReference(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoFullyQualifiedReference",
        severity = Severity.Style,
        description = "Fully qualified reference; add an import (or an import alias on a name clash).",
        debt = Debt.FIVE_MINS,
    )

    private val packageRoots: List<String> by config(
        listOf("android", "androidx", "com", "dev", "io", "java", "javax", "kotlin", "kotlinx", "net", "org"),
    )

    // Two or more lowercase dotted segments, the first being a known package root: the shape of a
    // package prefix, not of a variable/property chain.
    private val packagePrefix by lazy {
        Regex("^(${packageRoots.joinToString("|")})(\\.[a-z_][A-Za-z0-9_]*)+$")
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.isInsideDirective()) return
        val receiver = expression.receiverExpression.text.stripWhitespace()
        val selector = expression.selectorExpression?.text?.stripWhitespace() ?: return
        if (packagePrefix.matches(receiver) && selector.first().isUpperCase()) {
            report(CodeSmell(issue, Entity.from(expression), message(expression.text.stripWhitespace())))
        }
    }

    override fun visitUserType(type: KtUserType) {
        super.visitUserType(type)
        if (type.isInsideDirective()) return
        val qualifier = type.qualifier?.text?.stripWhitespace() ?: return
        val name = type.referencedName ?: return
        if (packagePrefix.matches(qualifier) && name.first().isUpperCase()) {
            report(CodeSmell(issue, Entity.from(type), message("$qualifier.$name")))
        }
    }

    private fun org.jetbrains.kotlin.psi.KtElement.isInsideDirective(): Boolean =
        parents.any { it is KtImportDirective || it is KtPackageDirective }

    private fun String.stripWhitespace(): String = filterNot { it.isWhitespace() }

    private fun message(reference: String): String =
        "'$reference' is fully qualified; import it instead (use an import alias if the simple name clashes)."
}
