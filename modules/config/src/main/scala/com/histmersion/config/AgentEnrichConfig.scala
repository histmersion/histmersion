package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** `enrich-source`: the body is sent as at most `maxWindows` windows of `windowChars` characters (by heading where the
  * source has a table of contents); the classify step sees the first `headChars`. `upstream` = check the record against
  * OpenAlex/Crossref and run `refs`/`assess` (network calls; off, those steps record that they were skipped). `guards`
  * are the findings step's extra checks ([[AgentEnrichGuardsConfig]]).
  */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class AgentEnrichConfig(
    windowChars: AgentEnrichConfig.WindowChars,
    maxWindows: AgentEnrichConfig.MaxWindows,
    headChars: AgentEnrichConfig.HeadChars,
    upstream: AgentEnrichConfig.Upstream,
    guards: AgentEnrichGuardsConfig
) derives ConfigReader

object AgentEnrichConfig:
  type WindowChars = WindowChars.T
  object WindowChars extends Newtype[Int, Pure]
  type MaxWindows = MaxWindows.T
  object MaxWindows extends Newtype[Int, Pure]
  type HeadChars = HeadChars.T
  object HeadChars extends Newtype[Int, Pure]
  type Upstream = Upstream.T
  object Upstream extends Newtype[Boolean, Pure]
