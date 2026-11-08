package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class TimeBucket(start: TimeBucket.Start, end: TimeBucket.End, count: TimeBucket.Count)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object TimeBucket:
  type Start = Start.T
  object Start extends Newtype[Long, Pure]
  type End = End.T
  object End extends Newtype[Long, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
