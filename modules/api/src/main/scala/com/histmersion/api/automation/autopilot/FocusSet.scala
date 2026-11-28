package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure

final case class FocusSet(
    action: FocusSet.Action,
    goal: Option[Goal.Slug] = None,
    value: Option[FocusSet.Value] = None,
    by: Option[FocusSet.By] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FocusSet:

  type Action = Action.T
  object Action extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[Int, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]

  val Actions = List("pin", "unpin", "priority", "max")
