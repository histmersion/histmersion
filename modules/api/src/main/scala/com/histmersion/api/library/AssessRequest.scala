package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class AssessRequest(id: AssessRequest.Id, apply: AssessRequest.Apply = AssessRequest.Apply(false), forceStatus: AssessRequest.ForceStatus = AssessRequest.ForceStatus(false))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssessRequest:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Apply = Apply.T
  object Apply extends RefinedType[Boolean, Pure]
  type ForceStatus = ForceStatus.T
  object ForceStatus extends RefinedType[Boolean, Pure]
