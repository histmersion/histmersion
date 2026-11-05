package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

final case class Note(
    id: com.histmersion.api.personal.Note.Id,
    target: Target,
    text: com.histmersion.api.personal.Note.Text,
    title: com.histmersion.api.personal.ReadMark.Title,
    createdAt: Note.CreatedAt = "",
    updatedAt: Note.UpdatedAt = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Note:


  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Edit(text: Text, ifUpdatedAt: Option[com.histmersion.api.personal.Note.UpdatedAt] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema



  type Id = Id.T

  object Id extends RefinedType[String, Pure]:
    given Encoder[Id] = Encoder.encodeString.contramap(_.value)
    given Decoder[Id] = Decoder.decodeString.map(Id(_))
    given Schema[Id] = Schema.string[Id]

  final case class New(target: Target, text: Text, title: Option[com.histmersion.api.personal.ReadMark.Title] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  type Text = Text.T

  object Text extends RefinedType[String, Pure]:
    type TidyResultPart1 = TidyResultPart1.T
    object TidyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val MaxChars = 50000

    /** A note's text as given, with Unix line ends and no trailing space; refused when empty or too long. */
    def tidy(raw: Text): Either[com.histmersion.api.personal.Note.Text.TidyResultPart1, Text] =
      val t = raw.value.replace("\r\n", "\n").stripTrailing
      if t.trim.isEmpty then Left("a note needs some text")
      else if t.length > MaxChars then Left(s"a note is at most $MaxChars characters (this one: ${t.length})")
      else Right(Text(t))
    given Encoder[Text] = Encoder.encodeString.contramap(_.value)
    given Decoder[Text] = Decoder.decodeString.map(Text(_))
    given Schema[Text] = Schema.string[Text]
