package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import sttp.tapir.Schema

/** A bookmark's own id: a UUID, written in lower case. */
type BookmarkId = BookmarkId.T

object BookmarkId extends RefinedType[String, Match[PersonalIds.UuidRule]]:
  def parse(raw: String): Either[String, BookmarkId] =
    either(Option(raw).fold("")(_.trim.toLowerCase)).left.map(_ => s"not a bookmark id: '$raw'")
  given Encoder[BookmarkId] = Encoder.encodeString.contramap(_.value)
  given Decoder[BookmarkId] = Decoder.decodeString.emap(parse)
  given Schema[BookmarkId]  = Schema.string[BookmarkId]
