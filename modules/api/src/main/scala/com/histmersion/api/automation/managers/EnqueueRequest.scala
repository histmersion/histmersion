package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import io.github.iltotore.iron.constraint.any.Pure

final case class EnqueueRequest(
    kind: EnqueueRequest.Kind,
    target: EnqueueRequest.Target,
    priority: Option[EnqueueRequest.Priority] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object EnqueueRequest:
  export EnqueueKind.Wire as Kind
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
