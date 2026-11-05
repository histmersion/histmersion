package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FigureLicenceFill(candidates: FigureLicenceFill.Candidates, updated: FigureLicenceFill.Updated, applied: FigureLicenceFill.Applied, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FigureLicenceFill:
  type Candidates = Candidates.T
  object Candidates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Applied = Applied.T
  object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
