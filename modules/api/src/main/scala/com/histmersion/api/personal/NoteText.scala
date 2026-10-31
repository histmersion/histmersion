package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type NoteText = NoteText.T

object NoteText extends RefinedType[String, Pure]:
  val MaxChars = 50000

  /** A note's text as given, with Unix line ends and no trailing space; refused when empty or too long. */
  def tidy(raw: NoteText): Either[String, NoteText] =
    val t = raw.value.replace("\r\n", "\n").stripTrailing
    if t.trim.isEmpty then Left("a note needs some text")
    else if t.length > MaxChars then Left(s"a note is at most $MaxChars characters (this one: ${t.length})")
    else Right(NoteText(t))
  given Encoder[NoteText] = Encoder.encodeString.contramap(_.value)
  given Decoder[NoteText] = Decoder.decodeString.map(NoteText(_))
  given Schema[NoteText] = Schema.string[NoteText]
