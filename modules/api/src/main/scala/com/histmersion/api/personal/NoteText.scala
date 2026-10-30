package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

/** What a person wrote in a note, as Markdown. */
type NoteText = NoteText.T

object NoteText extends RefinedType[String, Pure]:
  /** The most characters a note holds. */
  val MaxChars = 50000
  given Encoder[NoteText] = Encoder.encodeString.contramap(_.value)
  given Decoder[NoteText] = Decoder.decodeString.map(NoteText(_))
  given Schema[NoteText] = Schema.string[NoteText]
