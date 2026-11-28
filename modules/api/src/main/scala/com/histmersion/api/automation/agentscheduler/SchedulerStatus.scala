package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class SchedulerStatus(
    strategy: StrategyLine.Name,
    strategyFrom: SchedulerStatus.StrategyFrom = SchedulerStatus.StrategyFrom("config"),
    strategies: List[StrategyLine] = Nil,
    at: Option[SchedulerStatus.At] = None,
    dry: SchedulerStatus.Dry = SchedulerStatus.Dry.Live,
    maxRuns: SchedulerStatus.MaxRuns = SchedulerStatus.MaxRuns(0),
    occupied: SchedulerStatus.Occupied = SchedulerStatus.Occupied(0),
    pools: List[PoolUse] = Nil,
    types: List[TypeLine] = Nil,
    started: List[Started] = Nil,
    waiting: List[Waiting] = Nil,
    waitingTotal: SchedulerStatus.WaitingTotal = SchedulerStatus.WaitingTotal(0),
    forPerson: SchedulerStatus.ForPerson = SchedulerStatus.ForPerson(0),
    stop: Option[SchedulerStatus.Stop] = None,
    settings: List[SchedulerStatus.Settings] = Nil,
    changes: List[SchedulerStatus.Changes] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SchedulerStatus:
  type StrategyFrom = StrategyFrom.T
  object StrategyFrom extends Newtype[String, Pure]
  type At = At.T
  object At extends Newtype[String, Pure]
  enum Dry derives CanEqual:
    case Dry, Live
  object Dry extends TwoStates[Dry]:
    extension (dry: Dry) def dry: Boolean = dry == Dry
  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
  type Occupied = Occupied.T
  object Occupied extends Newtype[Int, Pure]
  type WaitingTotal = WaitingTotal.T
  object WaitingTotal extends Newtype[Int, Pure]
  type ForPerson = ForPerson.T
  object ForPerson extends Newtype[Int, Pure]
  type Stop = Stop.T
  object Stop extends Newtype[String, Pure]
  type Settings = Settings.T
  object Settings extends Newtype[String, Pure]
  type Changes = Changes.T
  object Changes extends Newtype[String, Pure]
