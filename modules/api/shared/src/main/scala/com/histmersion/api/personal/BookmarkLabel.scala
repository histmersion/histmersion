package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

/** The name a person gives a bookmark ("Troy before the Iliad"). */
type BookmarkLabel = BookmarkLabel.T

object BookmarkLabel extends RefinedType[String, Pure]:
  /** The most characters a label keeps. */
  val MaxChars = 200
  given Encoder[BookmarkLabel] = Encoder.encodeString.contramap(_.value)
  given Decoder[BookmarkLabel] = Decoder.decodeString.map(BookmarkLabel(_))
  given Schema[BookmarkLabel]  = Schema.string[BookmarkLabel]
