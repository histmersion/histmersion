package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Managers.Rationale
import io.github.iltotore.iron.constraint.any.Pure

final case class ItemPatch(
    action: ItemPatch.Action,
    priority: Option[ItemPatch.Priority] = None,
    until: Option[ItemPatch.Until] = None,
    untilSource: Option[ItemPatch.UntilSource] = None,
    reason: Option[Rationale] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ItemPatch:

  type Action = Action.T
  object Action extends Newtype[String, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type UntilSource = UntilSource.T
  object UntilSource extends Newtype[String, Pure]

  val Actions = List("hold", "release", "priority", "skip", "reopen")
