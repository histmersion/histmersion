package com.histmersion
package chimneyiron.api

import com.histmersion.chimneyiron.{CitekeyRule, NonBlankCounted}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import com.histmersion.lint.{tolerated, LintRule}

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

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
enum SourceStatusView:
  case Current
  case Contested(by: Citekey)
  case Superseded(by: Citekey)

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class PeriodView(start: Year, end: Year)

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class SourceView(
    citekey: Citekey,
    title: Title,
    tags: List[Tag],
    period: Option[PeriodView],
    status: SourceStatusView,
    citedBy: List[Citekey],
    replication: Option[Citekey]
)

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class NoteView(text: String)
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class ExcerptView(source: Citekey, note: NoteView)
