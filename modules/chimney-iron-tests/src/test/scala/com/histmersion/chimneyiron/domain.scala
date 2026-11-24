package com.histmersion
package chimneyiron.domain

import com.histmersion.chimneyiron.{CitekeyRule, NonBlankCounted}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import com.histmersion.lint.{LintRule, tolerated}

type Citekey = Citekey.T
object Citekey extends Newtype[String, CitekeyRule]

type Title = Title.T
object Title extends Newtype[String, NonBlankCounted]

type Tag = Tag.T
object Tag extends Newtype[String, NonBlankCounted]

type Year = Year.T
object Year extends Newtype[Int, Pure]

type PageSize = PageSize.T
object PageSize extends Newtype[Int, Pure]

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
