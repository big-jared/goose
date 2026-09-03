package dev.goose.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate

private const val APP_SCOPE = "dev.zacsweers.metro.AppScope"

/**
 * Turns one `@GooseFragmentNavigation(SomeScreen::class)`, `@GooseFragmentBinder(...)`, or
 * `@GoosePresentationNavigation(SomePresentation::class)` class into the full Metro
 * registration: a contributed module whose @Provides function returns the class bound as its
 * interop interface, keyed by the annotation's class argument (a screen class, or for
 * presentation navigation a [dev.goose.runtime.Presentation] class under the
 * `@PresentationNavigations` qualifier) into the map [dev.goose.fragment.FragmentNavigator]
 * reads. Constructor parameters become injected provider parameters (Metro qualifier
 * annotations copied over); an `object` is provided as-is.
 *
 * Supported grammar: a public or internal, top-level, non-generic, concrete class or object
 * implementing the annotation's interop interface, with a non-private primary constructor.
 * Anything else is a compile error naming the rule.
 *
 * Contributions are always AppScope: the fragment runtime snapshots its maps from the app graph
 * at install time, so a scope parameter here would drop entries into a map nothing reads.
 */
class GooseFragmentProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /** One entry per interop annotation: where it points and what the class must implement. */
    private data class Registration(
        val annotationFqn: String,
        val interfaceFqn: String,
        val moduleSuffix: String,
        /** Name of the annotation's KClass argument that becomes the map key. */
        val keyArgument: String = "screen",
        /** Extra qualifier annotation (FQN) on the generated @Provides, or null for none. */
        val providerQualifierFqn: String? = null,
    )

    private val registrations = listOf(
        Registration(
            annotationFqn = "dev.goose.fragment.GooseFragmentNavigation",
            interfaceFqn = "dev.goose.fragment.FragmentScreenNavigation",
            moduleSuffix = "GooseNavigationModule",
        ),
        Registration(
            annotationFqn = "dev.goose.fragment.GooseFragmentBinder",
            interfaceFqn = "dev.goose.fragment.ScreenFragmentBinder",
            moduleSuffix = "GooseBinderModule",
        ),
        Registration(
            annotationFqn = "dev.goose.fragment.GoosePresentationNavigation",
            interfaceFqn = "dev.goose.fragment.FragmentScreenNavigation",
            moduleSuffix = "GoosePresentationModule",
            keyArgument = "presentation",
            // Keeps the presentation-keyed map distinct from the screen-keyed override map.
            providerQualifierFqn = "dev.goose.fragment.PresentationNavigations",
        ),
    )

    /** (package, module name) pairs generated so far, to fail fast on name collisions. */
    private val generatedModules = mutableSetOf<Pair<String, String>>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        for (registration in registrations) {
            for (symbol in resolver.getSymbolsWithAnnotation(registration.annotationFqn)) {
                if (!symbol.validate()) {
                    deferred += symbol
                    continue
                }
                val declaration = symbol as? KSClassDeclaration ?: run {
                    logger.error("@${registration.label} is only valid on classes", symbol)
                    continue
                }
                generate(declaration, registration)
            }
        }
        return deferred
    }

    private val Registration.label: String get() = annotationFqn.substringAfterLast('.')

    private fun validateShape(declaration: KSClassDeclaration, registration: Registration): Boolean {
        val label = registration.label
        fun err(message: String): Boolean {
            logger.error("@$label: $message", declaration)
            return false
        }
        if (declaration.classKind != ClassKind.CLASS && declaration.classKind != ClassKind.OBJECT) {
            return err("must be a class or object")
        }
        if (declaration.parentDeclaration != null) {
            return err("must be top-level (nested and inner classes are not supported)")
        }
        if (Modifier.PRIVATE in declaration.modifiers) {
            return err("must not be private (the generated registration lives in a separate file)")
        }
        if (declaration.isAbstract()) {
            return err("must not be abstract")
        }
        if (declaration.typeParameters.isNotEmpty()) {
            return err("must not be generic")
        }
        val implementsInterface = declaration.getAllSuperTypes().any {
            it.declaration.qualifiedName?.asString() == registration.interfaceFqn
        }
        if (!implementsInterface) {
            return err("must implement ${registration.interfaceFqn.substringAfterLast('.')}")
        }
        return true
    }

    private fun generate(declaration: KSClassDeclaration, registration: Registration) {
        if (!validateShape(declaration, registration)) return
        val label = registration.label
        val annotation = declaration.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == registration.annotationFqn
        }
        val keyFqn = annotation.classArgument(registration.keyArgument, index = 0)
            ?.declaration?.qualifiedName?.asString() ?: run {
            logger.error("@$label requires a ${registration.keyArgument} class argument", declaration)
            return
        }
        val classFqn = declaration.qualifiedName?.asString() ?: run {
            logger.error("@$label class has no qualified name", declaration)
            return
        }

        val packageName = declaration.packageName.asString()
        val className = declaration.simpleName.asString()
        val moduleName = "$className${registration.moduleSuffix}"
        if (!generatedModules.add(packageName to moduleName)) {
            logger.error(
                "@$label: two annotated classes named '$className' in package '$packageName'. " +
                    "Generated registrations are named after the class; rename one.",
                declaration,
            )
            return
        }

        val creation: String
        val providerParams: String
        if (declaration.classKind == ClassKind.OBJECT) {
            creation = classFqn
            providerParams = ""
        } else {
            val constructor = declaration.primaryConstructor ?: run {
                logger.error("@$label: class must have a primary constructor", declaration)
                return
            }
            if (Modifier.PRIVATE in constructor.modifiers) {
                logger.error("@$label: the primary constructor must not be private", declaration)
                return
            }
            val params = mutableListOf<String>()
            val args = mutableListOf<String>()
            for (param in constructor.parameters) {
                val name = param.name?.asString() ?: run {
                    logger.error("@$label: constructor parameters must be named", declaration)
                    return
                }
                if (param.isVararg) {
                    logger.error("@$label: vararg constructor parameters are not supported", declaration)
                    return
                }
                val qualifiers = param.renderQualifiers(logger, "@$label", declaration) ?: return
                params += "$qualifiers$name: ${param.type.resolve().render()}"
                args += "$name = $name"
            }
            creation = "$classFqn(${args.joinToString(", ")})"
            providerParams = params.joinToString(",\n            ")
        }

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, declaration.containingFile!!),
            packageName = packageName,
            fileName = moduleName,
        )
        val qualifierLine = registration.providerQualifierFqn
            ?.let { "\n                |        @$it" } ?: ""
        file.bufferedWriter().use { writer ->
            writer.write(
                """
                |// Generated by goose-compiler from @$label on $className. Do not edit.
                |package $packageName
                |
                |@dev.zacsweers.metro.ContributesTo($APP_SCOPE::class)
                |public interface $moduleName {
                |    public companion object {
                |        @dev.zacsweers.metro.Provides
                |        @dev.zacsweers.metro.IntoMap
                |        @dev.zacsweers.metro.ClassKey($keyFqn::class)$qualifierLine
                |        public fun provide$className(
                |            $providerParams
                |        ): ${registration.interfaceFqn} = $creation
                |    }
                |}
                |""".trimMargin()
            )
        }
    }
}

class GooseFragmentProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        GooseFragmentProcessor(environment.codeGenerator, environment.logger)
}
