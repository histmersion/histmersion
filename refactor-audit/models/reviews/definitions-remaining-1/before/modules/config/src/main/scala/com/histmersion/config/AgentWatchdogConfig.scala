package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The stall watchdog (docs/research-process-managers.md §3.3). `stallMinutes` 0 = the longest provider timeout plus 5
  * minutes ([[stallMinutesFor]]).
  */
final case class AgentWatchdogConfig(
    stallMinutes: Int,
    resumeMinutes: Int,
    maxStalls: Int,
    heartbeatSeconds: Int,
    interruptedResumeMinutes: Int
) derives ConfigReader:
  def stallMinutesFor(p: AgentProvidersConfig): Int =
    if stallMinutes > 0 then stallMinutes
    else
      val longest = List(
        p.anthropic.timeoutSeconds,
        p.openai.timeoutSeconds,
        p.local.timeoutSeconds,
        p.claudeCode.timeoutSeconds,
        p.codex.timeoutSeconds,
        p.opencode.timeoutSeconds
      ).max
      (longest + 59) / 60 + 5
