package dev.goose.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Enforces the modularity rule at configuration time: only app modules may depend on feature
 * `:impl` modules (Metro needs them on the classpath to aggregate contributions); features and
 * api modules navigate via screens instead.
 */
object GooseModuleChecks {

    fun forbidImplProjectDependencies(project: Project, role: String) {
        project.afterEvaluate {
            val offending = project.configurations
                .filter { isProductionDependencyConfiguration(it.name) }
                .flatMap { it.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
                .filter { path -> path != project.path && isImplModulePath(path) }
                .distinct()
            check(offending.isEmpty()) {
                "$role module ${project.path} depends on feature impl modules: $offending. " +
                    "Features may only depend on :api modules — navigate via screens, not " +
                    "implementations. (Only app modules may see impls, purely for Metro " +
                    "contribution aggregation.)"
            }
        }
    }

    /** api/implementation and every build-type/flavor variant of them, excluding test configs. */
    private fun isProductionDependencyConfiguration(name: String): Boolean {
        if (name.startsWith("test") || name.startsWith("androidTest")) return false
        return name == "api" || name == "implementation" ||
            name == "runtimeOnly" || name == "compileOnly" ||
            name.endsWith("Api") || name.endsWith("Implementation") ||
            name.endsWith("RuntimeOnly") || name.endsWith("CompileOnly")
    }

    /** Matches both flat (`:feature-cart-impl`) and nested (`:feature:cart:impl`) layouts. */
    private fun isImplModulePath(path: String): Boolean {
        val last = path.substringAfterLast(":")
        return last.endsWith("-impl") || last == "impl"
    }
}
