package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

final case class Bookmark(
    id: com.histmersion.api.personal.Bookmark.Id,
    target: Target,
    title: String,
    label: Option[com.histmersion.api.personal.Bookmark.Label] = None,
    createdAt: String = "",
    updatedAt: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def name: String = label.map(_.value).filter(_.trim.nonEmpty).getOrElse(title)

object Bookmark:
  given Schema[Bookmark] = summon[KindlingsSchema[Bookmark]].schema

  final case class Edit(label: Option[Label] = None) derives KindlingsCodecAsObject, KindlingsSchema

  object Edit:
    given Schema[Edit] = summon[KindlingsSchema[Edit]].schema

  type Id = Id.T

  object Id extends RefinedType[String, Pure]:
    given Encoder[Id] = Encoder.encodeString.contramap(_.value)
    given Decoder[Id] = Decoder.decodeString.map(Id(_))
    given Schema[Id] = Schema.string[Id]

  type Label = Label.T

  object Label extends RefinedType[String, Pure]:
    val MaxChars = 200

    /** A name as given, tidied (spaces collapsed): none when empty, refused when too long. */
    def tidy(raw: Option[Label]): Either[String, Option[Label]] =
      raw.map(_.value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty) match
        case Some(l) if l.length > MaxChars => Left(s"a bookmark's name is at most $MaxChars characters")
        case other                          => Right(other.map(Label(_)))
    given Encoder[Label] = Encoder.encodeString.contramap(_.value)
    given Decoder[Label] = Decoder.decodeString.map(Label(_))
    given Schema[Label] = Schema.string[Label]

  final case class New(target: Target, label: Option[Label] = None, title: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema

  object New:
    given Schema[New] = summon[KindlingsSchema[New]].schema
