package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

/** What names an item of the collection, as the web UI names it: a source's citekey, an article's or a goal's
  * slug, an event's or a place's id, a picture's uid. */
type ItemKey = ItemKey.T

object ItemKey extends RefinedType[String, Pure]:
  given Encoder[ItemKey] = Encoder.encodeString.contramap(_.value)
  given Decoder[ItemKey] = Decoder.decodeString.map(s => ItemKey(s.trim))
  given Schema[ItemKey]  = Schema.string[ItemKey]
