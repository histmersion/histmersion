package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.EnrichSource.Topic
import com.histmersion.api.catalogue.Period

final case class Frame(index: Frame.Index, text: Frame.Text)

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
) derives CanEqual

object Loaded:
  type Note = Note.T
  object Note extends Newtype[String, Pure]

  extension (l: Loaded) def noted: Boolean = l.note.value.nonEmpty

final case class Classification(summary: Classification.Summary, period: Option[Period] = None)

object Classification:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]

final case class Notes(text: Notes.Text)

object Notes:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

object Limits:
  val Max = 3
  def Hint: String = "pick one"
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]

final case class Verdict(ratio: Verdict.Ratio, label: Verdict.Label, note: Verdict.Remark, reasons: List[Verdict.Reason])

object Verdict:
  final case class Reason(text: String)

  type Ratio = Limits.Ratio
  export Limits.Note as Remark
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    extension (l: Label) def clean: String = l.value.trim

enum Status:
  case Open, Closed

  def open: Boolean = this == Open

final case class Untouched(text: String) derives JsonCodec
