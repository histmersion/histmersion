package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class DiscoverResult(query: DiscoverResult.Query, count: DiscoverResult.Count, works: List[WorkRef])
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object DiscoverResult:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
