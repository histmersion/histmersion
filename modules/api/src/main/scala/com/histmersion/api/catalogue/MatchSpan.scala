package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MatchSpan(start: MatchSpan.Start, end: MatchSpan.End)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MatchSpan:
  type Start = Start.T
  object Start extends Newtype[Int, Pure]
  type End = End.T
  object End extends Newtype[Int, Pure]
