package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The library's settings among `agents.*`, read from the same block as the engine's [[AgentsConfig]]: what its
  * workflows send and check (`enrich`, `verify`), the plans it seeds (`seed`) and its watchers (`managers.watch`).
  */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class LibraryAgentsConfig(
    verify: AgentVerifyConfig,
    enrich: AgentEnrichConfig,
    seed: AgentSeedConfig,
    managers: LibraryManagersConfig
) derives ConfigReader
