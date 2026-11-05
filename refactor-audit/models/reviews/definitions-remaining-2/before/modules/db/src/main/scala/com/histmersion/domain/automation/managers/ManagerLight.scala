package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Managers.ManagerConfig
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.DbCodec

/** A manager without its counts: what the autopilot's ensure and status read for every manager. `planItem` = it has a
  * plan-goal item (any status).
  */
final case class ManagerLight(
    id: String,
    kind: String,
    goal: Option[String],
    state: String,
    cron: Option[String],
    config: ManagerConfig,
    statusNote: Option[String],
    planItem: Boolean
)

object ManagerLight:
  private given DbCodec[ManagerConfig] = Jsonb.codec(ManagerConfig(), dropNulls = true)
  given DbCodec[ManagerLight] = DbCodec.derived
