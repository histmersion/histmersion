package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MatchSpan(start: MatchSpan.Start, end: MatchSpan.End)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MatchSpan:
  type Start = Start.T
  object Start extends Newtype[Int, Pure]
  type End = End.T
  object End extends Newtype[Int, Pure]
