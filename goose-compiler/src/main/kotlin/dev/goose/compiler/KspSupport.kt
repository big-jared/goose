package dev.goose.compiler

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability

internal const val METRO_QUALIFIER = "dev.zacsweers.metro.Qualifier"

/** Reads a KClass annotation argument by name, falling back to position for shorthand usage. */
internal fun KSAnnotation.classArgument(name: String, index: Int): KSType? =
    (arguments.firstOrNull { it.name?.asString() == name }
        ?: arguments.getOrNull(index)?.takeIf { it.name == null })?.value as? KSType

/** Renders a type with qualified names, generics, and nullability. */
internal fun KSType.render(): String {
    val base = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    val args = if (arguments.isEmpty()) "" else arguments.joinToString(", ", "<", ">") { arg ->
        arg.type?.resolve()?.render() ?: "*"
    }
    val nullable = if (nullability == Nullability.NULLABLE) "?" else ""
    return "$base$args$nullable"
}

/**
 * Renders the Metro qualifier annotations on an injected parameter (`@Named("x") ` etc.) so
 * they carry over to the generated provider parameter. Returns "" when there are none, null
 * after logging (prefixed with [label], located at [errorSite]) when a qualifier has arguments
 * the generator cannot render.
 */
internal fun KSValueParameter.renderQualifiers(
    logger: KSPLogger,
    label: String,
    errorSite: KSNode,
): String? {
    val rendered = StringBuilder()
    for (ann in annotations) {
        val annDecl = ann.annotationType.resolve().declaration
        val isQualifier = annDecl.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == METRO_QUALIFIER
        }
        if (!isQualifier) continue
        val fqn = annDecl.qualifiedName?.asString() ?: continue
        val args = ann.arguments.mapNotNull { arg ->
            val value = arg.value ?: return@mapNotNull null
            val renderedValue = renderAnnotationValue(value) ?: run {
                logger.error(
                    "$label: cannot render qualifier @${annDecl.simpleName.asString()} argument " +
                        "'${arg.name?.asString()}' (${value::class.simpleName}); use a hand-written " +
                        "@Provides registration for this screen",
                    errorSite,
                )
                return null
            }
            arg.name?.asString()?.let { "$it = $renderedValue" } ?: renderedValue
        }
        rendered.append("@$fqn")
        if (args.isNotEmpty()) rendered.append(args.joinToString(", ", "(", ")"))
        rendered.append(" ")
    }
    return rendered.toString()
}

private fun renderAnnotationValue(value: Any): String? = when (value) {
    is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    is Char -> "'$value'"
    is Boolean, is Int, is Short, is Byte, is Double -> value.toString()
    is Long -> "${value}L"
    is Float -> "${value}f"
    is KSType -> value.declaration.qualifiedName?.asString()?.let { "$it::class" }
    is KSDeclaration -> value.qualifiedName?.asString()
    is List<*> -> value.map { it?.let(::renderAnnotationValue) ?: return null }
        .joinToString(", ", "[", "]")
    else -> null
}
