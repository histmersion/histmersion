package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type BookmarkLabel = BookmarkLabel.T

object BookmarkLabel extends RefinedType[String, Pure]:
  val MaxChars = 200

  /** A name as given, tidied (spaces collapsed): none when empty, refused when too long. */
  def tidy(raw: Option[BookmarkLabel]): Either[String, Option[BookmarkLabel]] =
    raw.map(_.value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty) match
      case Some(l) if l.length > MaxChars => Left(s"a bookmark's name is at most $MaxChars characters")
      case other                          => Right(other.map(BookmarkLabel(_)))
  given Encoder[BookmarkLabel] = Encoder.encodeString.contramap(_.value)
  given Decoder[BookmarkLabel] = Decoder.decodeString.map(BookmarkLabel(_))
  given Schema[BookmarkLabel] = Schema.string[BookmarkLabel]
