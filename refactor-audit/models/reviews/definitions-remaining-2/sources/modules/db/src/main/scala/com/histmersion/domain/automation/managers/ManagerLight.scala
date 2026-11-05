package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Managers.ManagerConfig
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.DbCodec

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
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cron = Cron.T
  object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNote = StatusNote.T
  object StatusNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanItem = PlanItem.T
  object PlanItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private given DbCodec[ManagerConfig] = Jsonb.codec(ManagerConfig(), dropNulls = true)
  given DbCodec[ManagerLight] = DbCodec.derived
