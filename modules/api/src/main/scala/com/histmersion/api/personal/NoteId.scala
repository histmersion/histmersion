package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import sttp.tapir.Schema

type NoteId = NoteId.T

object NoteId extends RefinedType[String, Match[PersonalIds.UuidRule]]:
  def parse(raw: String): Either[String, NoteId] =
    either(Option(raw).fold("")(_.trim.toLowerCase)).left.map(_ => s"not a note id: '$raw'")
  given Encoder[NoteId] = Encoder.encodeString.contramap(_.value)
  given Decoder[NoteId] = Decoder.decodeString.emap(parse)
  given Schema[NoteId] = Schema.string[NoteId]
