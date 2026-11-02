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
    title: String,
    createdAt: String = "",
    updatedAt: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema

object Note:
  given Schema[Note] = summon[KindlingsSchema[Note]].schema

  final case class Edit(text: Text, ifUpdatedAt: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema

  object Edit:
    given Schema[Edit] = summon[KindlingsSchema[Edit]].schema

  type Id = Id.T

  object Id extends RefinedType[String, Match[PersonalIds.UuidRule]]:
    def parse(raw: String): Either[String, Id] =
      either(Option(raw).fold("")(_.trim.toLowerCase)).left.map(_ => s"not a note id: '$raw'")
    given Encoder[Id] = Encoder.encodeString.contramap(_.value)
    given Decoder[Id] = Decoder.decodeString.emap(parse)
    given Schema[Id] = Schema.string[Id]

  final case class New(target: Target, text: Text, title: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema

  object New:
    given Schema[New] = summon[KindlingsSchema[New]].schema

  type Text = Text.T

  object Text extends RefinedType[String, Pure]:
    val MaxChars = 50000

    /** A note's text as given, with Unix line ends and no trailing space; refused when empty or too long. */
    def tidy(raw: Text): Either[String, Text] =
      val t = raw.value.replace("\r\n", "\n").stripTrailing
      if t.trim.isEmpty then Left("a note needs some text")
      else if t.length > MaxChars then Left(s"a note is at most $MaxChars characters (this one: ${t.length})")
      else Right(Text(t))
    given Encoder[Text] = Encoder.encodeString.contramap(_.value)
    given Decoder[Text] = Decoder.decodeString.map(Text(_))
    given Schema[Text] = Schema.string[Text]
