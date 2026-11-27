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
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class Note(
    id: Note.Id,
    target: Target,
    text: Note.Text,
    title: Title,
    createdAt: Note.At = Note.At(""),
    updatedAt: Note.At = Note.At("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Note:

  /** A moment of a note: when it was written, when it last changed. */
  type At = At.T
  object At extends Newtype[String, Pure]

  final case class Edit(text: Text, ifUpdatedAt: Option[At] = None) derives JsonCodec, KindlingsSchema, api.json.Schema

  type Id = Id.T

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Id extends Newtype[String, Pure]:
    given JsonValueCodec[Id] = JsonCodec.mapped[Id, String](_.value, b => Right(Id(b)))
    given Schema[Id] = Schema.string[Id]

  final case class New(target: Target, text: Text, title: Option[Title] = None)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  type Text = Text.T

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Text extends Newtype[String, Pure]:

    val MaxChars = 50000

    /** A note's text as given, with Unix line ends and no trailing space; refused when empty or too long. */
    def tidy(raw: Text): Either[Problem, Text] =
      val t = raw.value.replace("\r\n", "\n").stripTrailing
      if t.trim.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("text")))
      else if t.length > MaxChars then
        Left(
          Problem.Invalid.OutOfRange.here(
            Problem.TypeName.of(Text),
            Problem.Raw.of(t.length.toLong),
            Problem.Invalid.Bounds(None, Some(Problem.Raw.of(MaxChars.toLong)))
          )
        )
      else Right(Text(t))
    given JsonValueCodec[Text] = JsonCodec.mapped[Text, String](_.value, b => Right(Text(b)))
    given Schema[Text] = Schema.string[Text]
