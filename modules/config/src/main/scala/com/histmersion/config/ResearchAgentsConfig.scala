package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{movesTo, FeaturePlace}

/** Research's settings among `agents.*`, read from the same block as the engine's [[AgentsConfig]]: what its workflows
  * send and check (`review`, `compile`, `infer-filter`) and its review manager (`managers.review`).
  */
@movesTo(FeaturePlace.ResearchWorkflows)
final case class ResearchAgentsConfig(
    review: AgentReviewConfig,
    compile: AgentCompileConfig,
    inferFilter: AgentInferFilterConfig,
    managers: ResearchManagersConfig
) derives ConfigReader
