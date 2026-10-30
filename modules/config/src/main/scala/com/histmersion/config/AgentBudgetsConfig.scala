package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Per run defaults (overridable per run) and the global caps checked before every LLM call. */
final case class AgentBudgetsConfig(
    runUsd: Double,
    runTokens: Long,
    dailyUsd: Double,
    monthlyUsd: Double,
    pipelineRunTokens: String = "",
    cacheReadWeight: Double = 1.0
) derives ConfigReader:
  /** `run-tokens` for a run of `pipeline` that asks for none: its `pipeline-run-tokens` entry, else `run-tokens`. An
    * entry that does not parse (or is not positive) is ignored.
    */
  def runTokensFor(pipeline: String): Long =
    AgentBudgetsConfig.parsePipelineTokens(pipelineRunTokens).getOrElse(pipeline, runTokens)

object AgentBudgetsConfig:
  /** `"review-article=1200000, compile-article=800000"` → the map; malformed entries dropped. */
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
