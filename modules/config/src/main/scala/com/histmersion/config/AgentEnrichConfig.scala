package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `enrich-source`: the body is sent as at most `maxWindows` windows of `windowChars` characters (by heading where the
  * source has a table of contents); the classify step sees the first `headChars`. `upstream` = check the record against
  * OpenAlex/Crossref and run `refs`/`assess` (network calls; off, those steps record that they were skipped). `guards`
  * are the findings step's extra checks ([[AgentEnrichGuardsConfig]]).
  */
final case class AgentEnrichConfig(
    windowChars: AgentEnrichConfig.WindowChars,
    maxWindows: AgentEnrichConfig.MaxWindows,
    headChars: AgentEnrichConfig.HeadChars,
    upstream: AgentEnrichConfig.Upstream,
    guards: AgentEnrichGuardsConfig
) derives ConfigReader

object AgentEnrichConfig:
  type WindowChars = WindowChars.T
  object WindowChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxWindows = MaxWindows.T
  object MaxWindows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadChars = HeadChars.T
  object HeadChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Upstream = Upstream.T
  object Upstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
