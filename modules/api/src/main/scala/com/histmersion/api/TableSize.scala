package com.histmersion
package api

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class TableSize(name: TableSize.Name, bytes: ByteCount) derives JsonCodec, KindlingsSchema, json.Schema

object TableSize:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
