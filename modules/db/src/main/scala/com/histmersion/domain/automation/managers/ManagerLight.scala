package com.histmersion
package domain.automation.managers

import com.histmersion.api.automation.Managers.ManagerConfig
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** A manager without its counts: what the autopilot's ensure and status read for every manager. `planItem` = it has a
  * plan-goal item (any status).
  */
final case class ManagerLight(
    id: ManagerLight.Id,
    kind: ManagerLight.Kind,
    goal: Option[ManagerLight.Goal],
    state: ManagerLight.State,
    cron: Option[ManagerLight.Cron],
    config: ManagerConfig,
    statusNote: Option[ManagerLight.StatusNote],
    planItem: ManagerLight.PlanItem
)

object ManagerLight:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type State = State.T
  object State extends Newtype[String, Pure]
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
  type StatusNote = StatusNote.T
  object StatusNote extends Newtype[String, Pure]
  type PlanItem = PlanItem.T
  object PlanItem extends Newtype[Boolean, Pure]

  private given DbCodec[ManagerConfig] = Jsonb.codec(ManagerConfig(), dropNulls = Jsonb.DropNulls(true))
  given DbCodec[ManagerLight] = DbCodec.derived
