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
    title: com.histmersion.api.library.Title,
    label: Option[com.histmersion.api.personal.Bookmark.Label] = None,
    createdAt: Bookmark.CreatedAt = _root_.com.histmersion.api.personal.Bookmark.CreatedAt(""),
    updatedAt: Bookmark.UpdatedAt = _root_.com.histmersion.api.personal.Bookmark.UpdatedAt("")
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def name: com.histmersion.api.personal.Bookmark.Name = label.map(_.value).filter(_.trim.nonEmpty).getOrElse(title)

object Bookmark:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Edit(label: Option[Label] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema



  type Id = Id.T

  object Id extends RefinedType[String, Pure]:
    given Encoder[Id] = Encoder.encodeString.contramap(_.value)
    given Decoder[Id] = Decoder.decodeString.map(Id(_))
    given Schema[Id] = Schema.string[Id]

  type Label = Label.T

  object Label extends RefinedType[String, Pure]:
    type TidyResultPart1 = com.histmersion.api.personal.Bookmark.Problem
    val TidyResultPart1: _root_.com.histmersion.api.personal.Bookmark.Problem.type = _root_.com.histmersion.api.personal.Bookmark.Problem

    val MaxChars = 200

    /** A name as given, tidied (spaces collapsed): none when empty, refused when too long. */
    def tidy(raw: Option[Label]): Either[com.histmersion.api.personal.Bookmark.Problem, Option[Label]] =
      raw.map(_.value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty) match
        case Some(l) if l.length > MaxChars => Left(_root_.com.histmersion.api.personal.Bookmark.Problem(s"a bookmark's name is at most $MaxChars characters"))
        case other                          => Right(other.map(Label(_)))
    given Encoder[Label] = Encoder.encodeString.contramap(_.value)
    given Decoder[Label] = Decoder.decodeString.map(Label(_))
    given Schema[Label] = Schema.string[Label]

  final case class New(target: Target, label: Option[Label] = None, title: Option[com.histmersion.api.library.Title] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
