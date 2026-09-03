package dev.goose.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class GooseRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "goose"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(NoFullyQualifiedReference(config)))
}
