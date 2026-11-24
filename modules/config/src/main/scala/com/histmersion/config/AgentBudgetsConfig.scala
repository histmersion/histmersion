package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.domain.automation.Pipeline
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** Per run defaults (overridable per run) and the global caps checked before every LLM call. */
@movesTo(FeaturePlace.AutomationAgents)
final case class AgentBudgetsConfig(
    runUsd: AgentBudgetsConfig.RunUsd,
    runTokens: AgentBudgetsConfig.RunTokens,
    dailyUsd: AgentBudgetsConfig.DailyUsd,
    monthlyUsd: AgentBudgetsConfig.MonthlyUsd,
    pipelineRunTokens: AgentBudgetsConfig.PipelineRunTokens = AgentBudgetsConfig.PipelineRunTokens(""),
    cacheReadWeight: AgentBudgetsConfig.CacheReadWeight = AgentBudgetsConfig.CacheReadWeight(1.0)
) derives ConfigReader:
  /** `run-tokens` for a run of `pipeline` that asks for none: its `pipeline-run-tokens` entry, else `run-tokens`. An
    * entry that does not parse (or is not positive) is ignored.
    */
  def runTokensFor(pipeline: Pipeline.Wire): AgentBudgetsConfig.RunTokens =
    AgentBudgetsConfig.parsePipelineTokens(pipelineRunTokens).getOrElse(pipeline, runTokens)

object AgentBudgetsConfig:
  /** `"review-article=1200000, compile-article=800000"` → the map; malformed entries dropped. */

  type RunUsd = RunUsd.T
  object RunUsd extends Newtype[Double, Pure]
  type RunTokens = RunTokens.T
  object RunTokens extends Newtype[Long, Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends Newtype[Double, Pure]
  type MonthlyUsd = MonthlyUsd.T
  object MonthlyUsd extends Newtype[Double, Pure]
  type PipelineRunTokens = PipelineRunTokens.T
  object PipelineRunTokens extends Newtype[String, Pure]
  type CacheReadWeight = CacheReadWeight.T
  object CacheReadWeight extends Newtype[Double, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parsePipelineTokens(s: PipelineRunTokens): Map[Pipeline.Wire, RunTokens] =
    s.value
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(e =>
        e.split("=", 2) match
          case Array(k, v) =>
            v.trim.replace("_", "").toLongOption.filter(_ > 0).map(n => Pipeline.Wire(k.trim) -> RunTokens(n))
          case _ => None
      )
      .toMap
