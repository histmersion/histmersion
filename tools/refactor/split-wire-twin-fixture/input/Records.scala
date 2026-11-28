package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.EnrichSource.Topic
import com.histmersion.api.catalogue.Period

final case class Frame(index: Frame.Index, text: Frame.Text) derives JsonCodec

object Frame:
  type Index = Index.T
  object Index extends Newtype[Int, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

final case class Loaded(
    citekey: Citekey,
    topics: List[Topic],
    period: Option[Period],
    windows: List[Frame],
    status: Status = Status.Open,
    note: Loaded.Note = Loaded.Note("")
) derives CanEqual, JsonCodec

object Loaded:
  type Note = Note.T
  object Note extends Newtype[String, Pure]

  extension (l: Loaded) def noted: Boolean = l.note.value.nonEmpty

final case class Classification(summary: Classification.Summary, period: Option[Period] = None)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object Classification:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]

  given Schema[Classification] = KindlingsSchema
    .derived[Classification]
    .schema
    .modify(_.summary)(_.description("3-6 sentences"))
    .modify(_.period)(_.description("null if none"))

enum Status derives JsonCodec:
  case Open, Closed

  def open: Boolean = this == Open

final case class Untouched(text: String) derives JsonCodec
