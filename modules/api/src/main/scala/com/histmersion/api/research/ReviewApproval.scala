package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ReviewApproval(id: ReviewApproval.Id, kind: ReviewApproval.Kind)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ReviewApproval:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
