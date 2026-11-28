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

  given sttp.tapir.Schema[Classification] = hearth.kindlings.tapirschemaderivation.KindlingsSchema
    .derived[Classification]
    .schema
    .modify(_.summary)(_.description("3-6 sentences"))
    .modify(_.period)(_.description("null if none"))

final case class Notes(text: Notes.Text) derives JsonCodec

object Notes:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  given Schema[Notes] = KindlingsSchema.derived[Notes].schema.modify(_.text)(_.description("Markdown"))

object Limits:
  val Max = 3
  def Hint: String = "pick one"
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]

final case class Verdict(ratio: Verdict.Ratio, label: Verdict.Label, note: Verdict.Remark, reasons: List[Verdict.Reason])
    derives JsonCodec

object Verdict:
  final case class Reason(text: String) derives JsonCodec

  type Ratio = Limits.Ratio
  export Limits.Note as Remark
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    extension (l: Label) def clean: String = l.value.trim

  given Schema[Verdict] = KindlingsSchema
    .derived[Verdict]
    .schema
    .modify(_.ratio)(_.description("a " + "ratio"))
    .modify(_.label)(_.description(s"at most ${Limits.Max} labels"))
    .modify(_.note)(_.description(Limits.Hint))

enum Status derives JsonCodec:
  case Open, Closed

  def open: Boolean = this == Open

final case class Untouched(text: String) derives JsonCodec
