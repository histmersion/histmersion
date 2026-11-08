package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** What a run's trace keeps: a tool result is truncated to `maxToolResultChars` before it is stored, and traced
  * messages older than `keepMessagesDays` are deleted (0 = keep them all) — checked when the run pool starts and then
  * at most every `pruneEveryMinutes`, after a run settles. Steps (the checkpoints, tokens and cost) are never pruned.
  */
final case class AgentTraceConfig(
    maxToolResultChars: AgentTraceConfig.MaxToolResultChars,
    keepMessagesDays: AgentTraceConfig.KeepMessagesDays,
    pruneEveryMinutes: AgentTraceConfig.PruneEveryMinutes
) derives ConfigReader

object AgentTraceConfig:
  type MaxToolResultChars = MaxToolResultChars.T
  object MaxToolResultChars extends Newtype[Int, Pure]
  type KeepMessagesDays = KeepMessagesDays.T
  object KeepMessagesDays extends Newtype[Int, Pure]
  type PruneEveryMinutes = PruneEveryMinutes.T
  object PruneEveryMinutes extends Newtype[Int, Pure]
