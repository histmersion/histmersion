package com.histmersion
package chimneyiron.client

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import com.histmersion.lint.{tolerated, LintRule}

type Citekey = Citekey.T
object Citekey extends Newtype[String, Pure]

type Title = Title.T
object Title extends Newtype[String, Pure]

type Tag = Tag.T
object Tag extends Newtype[String, Pure]

type Year = Year.T
object Year extends Newtype[Int, Pure]

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
