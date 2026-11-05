package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The stall watchdog (docs/research-process-managers.md §3.3). `stallMinutes` 0 = the longest provider timeout plus 5
  * minutes ([[stallMinutesFor]]).
  */
final case class AgentWatchdogConfig(
    stallMinutes: AgentWatchdogConfig.StallMinutes,
    resumeMinutes: AgentWatchdogConfig.ResumeMinutes,
    maxStalls: AgentWatchdogConfig.MaxStalls,
    heartbeatSeconds: AgentWatchdogConfig.HeartbeatSeconds,
    interruptedResumeMinutes: AgentWatchdogConfig.InterruptedResumeMinutes
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

object AgentWatchdogConfig:
  type StallMinutes = StallMinutes.T
  object StallMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeMinutes = ResumeMinutes.T
  object ResumeMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxStalls = MaxStalls.T
  object MaxStalls extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeartbeatSeconds = HeartbeatSeconds.T
  object HeartbeatSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InterruptedResumeMinutes = InterruptedResumeMinutes.T
  object InterruptedResumeMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
