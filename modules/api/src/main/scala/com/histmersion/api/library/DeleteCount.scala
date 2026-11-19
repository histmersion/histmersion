package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class DeleteCount(part: DeletePart, rows: DeleteCount.Rows)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object DeleteCount:
  type Rows = Rows.T
  object Rows extends Newtype[Int, Pure]
