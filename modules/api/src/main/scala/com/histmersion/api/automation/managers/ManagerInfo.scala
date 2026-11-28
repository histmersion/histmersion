package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Manager
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerInfo(
    id: ManagerInfo.Id,
    kind: ManagerInfo.Kind,
    goal: Option[Goal.Slug] = None,
    state: ManagerInfo.State = Manager.State.Active.wire,
    pauseRuns: ManagerInfo.PauseRuns = ManagerInfo.PauseRuns.Run,
    config: ManagerConfig = ManagerConfig(),
    effective: ManagerConfig = ManagerConfig(),
    statusNote: Option[Manager.Status] = None,
    cron: Option[ManagerInfo.Cron] = None,
    nextTickAt: Option[ManagerInfo.NextTickAt] = None,
    createdAt: ManagerInfo.CreatedAt = ManagerInfo.CreatedAt(""),
    updatedAt: ManagerInfo.UpdatedAt = ManagerInfo.UpdatedAt(""),
    counts: ItemCounts = ItemCounts(),
    inFlight: ManagerInfo.InFlight = ManagerInfo.InFlight(0),
    lastEvent: Option[ManagerEvent] = None,
    spentTodayUsd: ManagerInfo.SpentTodayUsd = ManagerInfo.SpentTodayUsd(0.0),
    tokensToday: ManagerInfo.TokensToday = ManagerInfo.TokensToday((0).toLong),
    backlog: ManagerInfo.Backlog = ManagerInfo.Backlog(0),
    backlogDays: Option[ManagerInfo.BacklogDays] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManagerInfo:
  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  export Manager.Kind.Wire as Kind
  export Manager.State.Wire as State
  enum PauseRuns derives CanEqual:
    case Pause, Run
  object PauseRuns extends TwoStates[PauseRuns]:
    extension (pauseRuns: PauseRuns) def pauseRuns: Boolean = pauseRuns == Pause
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
  type NextTickAt = NextTickAt.T
  object NextTickAt extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type InFlight = InFlight.T
  object InFlight extends Newtype[Int, Pure]
  type SpentTodayUsd = SpentTodayUsd.T
  object SpentTodayUsd extends Newtype[Double, Pure]
  type TokensToday = TokensToday.T
  object TokensToday extends Newtype[Long, Pure]
  type Backlog = Backlog.T
  object Backlog extends Newtype[Int, Pure]
  type BacklogDays = BacklogDays.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object BacklogDays extends Newtype[Int, Pure]
