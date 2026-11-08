package com.histmersion
package chimneyiron.domain

import com.histmersion.chimneyiron.{CitekeyRule, NonBlankCounted}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Citekey = Citekey.T
object Citekey extends RefinedType[String, CitekeyRule]

type Title = Title.T
object Title extends RefinedType[String, NonBlankCounted]

type Tag = Tag.T
object Tag extends RefinedType[String, NonBlankCounted]

type Year = Year.T
object Year extends RefinedType[Int, Pure]

type PageSize = PageSize.T
object PageSize extends RefinedType[Int, Pure]

enum SourceStatus:
  case Current
  case Contested(by: Citekey)
  case Superseded(by: Citekey)

final case class Period(start: Year, end: Year)

final case class Source(
    citekey: Citekey,
    title: Title,
    tags: List[Tag],
    period: Option[Period],
    status: SourceStatus,
    citedBy: Vector[Citekey],
    replication: Option[Citekey]
)

final case class Note(text: String)
final case class Excerpt(source: Citekey, note: Note)
