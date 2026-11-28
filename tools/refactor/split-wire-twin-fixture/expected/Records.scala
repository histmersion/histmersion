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

enum Status:
  case Open, Closed

  def open: Boolean = this == Open

final case class Untouched(text: String) derives JsonCodec
