package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** The stall watchdog (docs/research-process-managers.md §3.3). `stallMinutes` 0 = the longest provider timeout plus 5
  * minutes ([[stallMinutesFor]]).
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class AgentWatchdogConfig(
    stallMinutes: AgentWatchdogConfig.StallMinutes,
    resumeMinutes: AgentWatchdogConfig.ResumeMinutes,
    maxStalls: AgentWatchdogConfig.MaxStalls,
    heartbeatSeconds: AgentWatchdogConfig.HeartbeatSeconds,
    interruptedResumeMinutes: AgentWatchdogConfig.InterruptedResumeMinutes
) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stallMinutesFor(p: AgentProvidersConfig): AgentWatchdogConfig.StallMinutes =
    if stallMinutes.value > 0 then stallMinutes
    else
      val longest = List(
        p.anthropic.timeoutSeconds.value,
        p.openai.timeoutSeconds.value,
        p.local.timeoutSeconds.value,
        p.claudeCode.timeoutSeconds.value,
        p.codex.timeoutSeconds.value,
        p.opencode.timeoutSeconds.value
      ).max
      AgentWatchdogConfig.StallMinutes((longest + 59) / 60 + 5)

object AgentWatchdogConfig:

  type StallMinutes = StallMinutes.T
  object StallMinutes extends Newtype[Int, Pure]
  type ResumeMinutes = ResumeMinutes.T
  object ResumeMinutes extends Newtype[Int, Pure]
  type MaxStalls = MaxStalls.T
  object MaxStalls extends Newtype[Int, Pure]
  type HeartbeatSeconds = HeartbeatSeconds.T
  object HeartbeatSeconds extends Newtype[Int, Pure]
  type InterruptedResumeMinutes = InterruptedResumeMinutes.T
  object InterruptedResumeMinutes extends Newtype[Int, Pure]
