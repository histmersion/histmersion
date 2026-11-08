package com.histmersion
package chimneyiron.api

import com.histmersion.chimneyiron.{CitekeyRule, NonBlankCounted}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Citekey = Citekey.T
object Citekey extends Newtype[String, CitekeyRule]

type Title = Title.T
object Title extends Newtype[String, NonBlankCounted]

type Tag = Tag.T
object Tag extends Newtype[String, NonBlankCounted]

type Year = Year.T
object Year extends Newtype[Int, Pure]

type Heading = Heading.T
object Heading extends Newtype[String, NonBlankCounted]

enum SourceStatusView derives CanEqual:
  case Current
  case Contested(by: Citekey)
  case Superseded(by: Citekey)

final case class PeriodView(start: Year, end: Year)

final case class SourceView(
    citekey: Citekey,
    title: Title,
    tags: List[Tag],
    period: Option[PeriodView],
    status: SourceStatusView,
    citedBy: List[Citekey],
    replication: Option[Citekey]
)

final case class NoteView(text: String)
final case class ExcerptView(source: Citekey, note: NoteView)
