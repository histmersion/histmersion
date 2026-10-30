package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type BookmarkLabel = BookmarkLabel.T

object BookmarkLabel extends RefinedType[String, Pure]:
  val MaxChars = 200
  given Encoder[BookmarkLabel] = Encoder.encodeString.contramap(_.value)
  given Decoder[BookmarkLabel] = Decoder.decodeString.map(BookmarkLabel(_))
  given Schema[BookmarkLabel] = Schema.string[BookmarkLabel]
