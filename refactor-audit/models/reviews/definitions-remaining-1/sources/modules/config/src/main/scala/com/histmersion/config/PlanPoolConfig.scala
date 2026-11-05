package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** One plan pool: a floor (% that must stay LEFT) per window kind (`five_hour=25, seven_day=25`; Codex windows by
  * length, `300m`, `10080m`), how many LLM calls may draw on it at once, and how often it is read while the autopilot
  * has queued work for it.
  */
final case class PlanPoolConfig(
    floors: PlanPoolConfig.Floors,
    maxCallsInFlight: PlanPoolConfig.MaxCallsInFlight,
    refreshEveryMinutes: ScheduledRefresh = ScheduledRefresh.Off
) derives ConfigReader

object PlanPoolConfig:
  type Floors = Floors.T
  object Floors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxCallsInFlight = MaxCallsInFlight.T
  object MaxCallsInFlight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
