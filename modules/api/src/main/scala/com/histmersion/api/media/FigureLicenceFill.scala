package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class FigureLicenceFill(
    candidates: FigureLicenceFill.Candidates,
    updated: FigureLicenceFill.Updated,
    applied: FigureLicenceFill.Applied,
    bySource: List[MediaCount] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FigureLicenceFill:
  type Candidates = Candidates.T
  object Candidates extends Newtype[Int, Pure]
  type Updated = Updated.T
  object Updated extends Newtype[Int, Pure]
  enum Applied derives CanEqual:
    case Applied, Previewed
  object Applied extends TwoStates[Applied]:
    extension (applied: Applied) def applied: Boolean = applied == Applied
