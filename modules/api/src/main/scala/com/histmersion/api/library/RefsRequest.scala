package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure

final case class RefsRequest(id: RefsRequest.Id, apply: RefsRequest.Apply = Api.Apply(false))
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RefsRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  export Api.Apply
