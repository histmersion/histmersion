package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** What a run's trace keeps: a tool result is truncated to `maxToolResultChars` before it is stored, and traced
  * messages older than `keepMessagesDays` are deleted (0 = keep them all) — checked when the run pool starts and then
  * at most every `pruneEveryMinutes`, after a run settles. Steps (the checkpoints, tokens and cost) are never pruned.
  */
final case class AgentTraceConfig(maxToolResultChars: Int, keepMessagesDays: Int, pruneEveryMinutes: Int)
    derives ConfigReader
