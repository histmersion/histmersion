package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class DeleteCount(part: DeletePart, rows: DeleteCount.Rows) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object DeleteCount:
  type Rows = Rows.T
  object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
