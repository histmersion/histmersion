package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class AssessRequest(
    id: AssessRequest.Id,
    apply: AssessRequest.Apply = AssessRequest.Apply(false),
    forceStatus: AssessRequest.ForceStatus = AssessRequest.ForceStatus(false)
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object AssessRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Apply = Apply.T
  object Apply extends Newtype[Boolean, Pure]
  type ForceStatus = ForceStatus.T
  object ForceStatus extends Newtype[Boolean, Pure]
