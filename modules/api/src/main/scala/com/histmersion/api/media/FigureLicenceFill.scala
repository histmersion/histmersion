package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class FigureLicenceFill(candidates: FigureLicenceFill.Candidates, updated: FigureLicenceFill.Updated, applied: FigureLicenceFill.Applied, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FigureLicenceFill:
  type Candidates = Candidates.T
  object Candidates extends RefinedType[Int, Pure]
  type Updated = Updated.T
  object Updated extends RefinedType[Int, Pure]
  type Applied = Applied.T
  object Applied extends RefinedType[Boolean, Pure]
