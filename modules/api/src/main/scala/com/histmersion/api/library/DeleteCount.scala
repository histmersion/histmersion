package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class DeleteCount(part: DeletePart, rows: DeleteCount.Rows) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object DeleteCount:
  type Rows = Rows.T
  object Rows extends Newtype[Int, Pure]
