package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

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
  object Floors extends Newtype[String, Pure]
  type MaxCallsInFlight = MaxCallsInFlight.T
  object MaxCallsInFlight extends Newtype[Int, Pure]
