package com.histmersion
package api.personal
import com.histmersion.api.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Title

final case class Bookmark(
    id: Bookmark.Id,
    target: Target,
    title: Title,
    label: Option[Bookmark.Label] = None,
    createdAt: Bookmark.CreatedAt = Bookmark.CreatedAt(""),
    updatedAt: Bookmark.UpdatedAt = Bookmark.UpdatedAt("")
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def name: Bookmark.Name =
    Bookmark.Name(label.map(_.value: String).filter(_.trim.nonEmpty).getOrElse(title.value: String))

object Bookmark:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]

  final case class Edit(label: Option[Label] = None) derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  type Id = Id.T

  object Id extends Newtype[String, Pure]:
    given JsonValueCodec[Id] = JsonCodec.mapped[Id, String](_.value, b => Right(Id(b)))
    given Schema[Id] = Schema.string[Id]

  type Label = Label.T

  object Label extends Newtype[String, Pure]:

    val MaxChars = 200

    /** A name as given, tidied (spaces collapsed): none when empty, refused when too long. */
    def tidy(raw: Option[Label]): Either[Problem, Option[Label]] =
      raw.map(_.value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty) match
        case Some(l) if l.length > MaxChars =>
          Left(
            Problem.Invalid.OutOfRange.here(
              Problem.TypeName.of(Label),
              Problem.Raw("name"),
              Problem.Invalid.Bounds(None, Some(Problem.Raw.of(MaxChars.toLong)))
            )
          )
        case other => Right(other.map(Label(_)))
    given JsonValueCodec[Label] = JsonCodec.mapped[Label, String](_.value, b => Right(Label(b)))
    given Schema[Label] = Schema.string[Label]

  final case class New(target: Target, label: Option[Label] = None, title: Option[Title] = None)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema
