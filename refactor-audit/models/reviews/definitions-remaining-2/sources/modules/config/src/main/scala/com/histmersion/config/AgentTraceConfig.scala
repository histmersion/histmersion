package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** What a run's trace keeps: a tool result is truncated to `maxToolResultChars` before it is stored, and traced
  * messages older than `keepMessagesDays` are deleted (0 = keep them all) — checked when the run pool starts and then
  * at most every `pruneEveryMinutes`, after a run settles. Steps (the checkpoints, tokens and cost) are never pruned.
  */
final case class AgentTraceConfig(maxToolResultChars: AgentTraceConfig.MaxToolResultChars, keepMessagesDays: AgentTraceConfig.KeepMessagesDays, pruneEveryMinutes: AgentTraceConfig.PruneEveryMinutes)
    derives ConfigReader

object AgentTraceConfig:
  type MaxToolResultChars = MaxToolResultChars.T
  object MaxToolResultChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepMessagesDays = KeepMessagesDays.T
  object KeepMessagesDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PruneEveryMinutes = PruneEveryMinutes.T
  object PruneEveryMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
