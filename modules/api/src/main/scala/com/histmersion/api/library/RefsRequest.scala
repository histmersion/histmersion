package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class RefsRequest(id: RefsRequest.Id, apply: RefsRequest.Apply = Api.Apply(false)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object RefsRequest:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  export Api.Apply
