package com.histmersion
package api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type ItemKey = ItemKey.T

object ItemKey extends RefinedType[String, Pure]:
  given Encoder[ItemKey] = Encoder.encodeString.contramap(_.value)
  given Decoder[ItemKey] = Decoder.decodeString.map(s => ItemKey(s.trim))
  given Schema[ItemKey] = Schema.string[ItemKey]
