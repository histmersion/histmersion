package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class AssessRequest(id: AssessRequest.Id, apply: AssessRequest.Apply = _root_.com.histmersion.api.library.AssessRequest.Apply(false), forceStatus: AssessRequest.ForceStatus = _root_.com.histmersion.api.library.AssessRequest.ForceStatus(false))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssessRequest:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Apply = Apply.T
  object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForceStatus = ForceStatus.T
  object ForceStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
