package com.histmersion
package api.personal
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Title

final case class Note(
    id: Note.Id,
    target: Target,
    text: Note.Text,
    title: Title,
    createdAt: Note.At = Note.At(""),
    updatedAt: Note.At = Note.At("")
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object Note:

  /** A moment of a note: when it was written, when it last changed. */
  type At = At.T
  object At extends Newtype[String, Pure]

  final case class Edit(text: Text, ifUpdatedAt: Option[At] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  type Id = Id.T

  object Id extends Newtype[String, Pure]:
    given Encoder[Id] = Encoder.encodeString.contramap(_.value)
    given Decoder[Id] = Decoder.decodeString.map(Id(_))
    given Schema[Id] = Schema.string[Id]

  final case class New(target: Target, text: Text, title: Option[Title] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  type Text = Text.T

  object Text extends Newtype[String, Pure]:

    val MaxChars = 50000

    /** A note's text as given, with Unix line ends and no trailing space; refused when empty or too long. */
    def tidy(raw: Text): Either[Problem, Text] =
      val t = raw.value.replace("\r\n", "\n").stripTrailing
      if t.trim.isEmpty then Left(Problem.Unclassified(Legacy.Text("a note needs some text")))
      else if t.length > MaxChars then
        Left(Problem.Unclassified(Legacy.Text(s"a note is at most $MaxChars characters (this one: ${t.length})")))
      else Right(Text(t))
    given Encoder[Text] = Encoder.encodeString.contramap(_.value)
    given Decoder[Text] = Decoder.decodeString.map(Text(_))
    given Schema[Text] = Schema.string[Text]
