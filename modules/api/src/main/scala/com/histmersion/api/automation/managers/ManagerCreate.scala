package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.Manager

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerCreate(
    kind: ManagerCreate.Kind,
    goal: Option[Goal.Slug] = None,
    config: ManagerConfig = ManagerConfig(),
    paused: Option[ManagerCreate.Paused] = None,
    cron: Option[ManagerCreate.Cron] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object ManagerCreate:
  export Manager.Kind.Wire as Kind
  enum Paused derives CanEqual:
    case Paused, Active
  object Paused extends TwoStates[Paused]:
    extension (paused: Paused) def paused: Boolean = paused == Paused
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
