package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Per run defaults (overridable per run) and the global caps checked before every LLM call. */
final case class AgentBudgetsConfig(
    runUsd: AgentBudgetsConfig.RunUsd,
    runTokens: AgentBudgetsConfig.RunTokens,
    dailyUsd: AgentBudgetsConfig.DailyUsd,
    monthlyUsd: AgentBudgetsConfig.MonthlyUsd,
    pipelineRunTokens: AgentBudgetsConfig.PipelineRunTokens = "",
    cacheReadWeight: AgentBudgetsConfig.CacheReadWeight = 1.0
) derives ConfigReader:
  /** `run-tokens` for a run of `pipeline` that asks for none: its `pipeline-run-tokens` entry, else `run-tokens`. An
    * entry that does not parse (or is not positive) is ignored.
    */
  def runTokensFor(pipeline: String): Long =
    AgentBudgetsConfig.parsePipelineTokens(pipelineRunTokens).getOrElse(pipeline, runTokens)

object AgentBudgetsConfig:
  /** `"review-article=1200000, compile-article=800000"` → the map; malformed entries dropped. */
  type RunUsd = RunUsd.T
  object RunUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunTokens = RunTokens.T
  object RunTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MonthlyUsd = MonthlyUsd.T
  object MonthlyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PipelineRunTokens = PipelineRunTokens.T
  object PipelineRunTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CacheReadWeight = CacheReadWeight.T
  object CacheReadWeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parsePipelineTokens(s: String): Map[String, Long] =
    s.split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(e =>
        e.split("=", 2) match
          case Array(k, v) => v.trim.replace("_", "").toLongOption.filter(_ > 0).map(k.trim -> _)
          case _           => None
      )
      .toMap
