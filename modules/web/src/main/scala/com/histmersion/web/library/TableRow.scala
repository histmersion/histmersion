package com.histmersion
package web.library

import com.histmersion.api.Wording
import com.histmersion.api.library.{Hit, Label}
import com.histmersion.web.{ArticleLink, SourceLink}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.catalogue.Year
import com.histmersion.api.personal.ItemKey
import com.histmersion.lint.{tolerated, LintRule}

/** One row of a Library table, as it reads: the item's key (a citekey or a slug), its title and the line under it (a
  * source's authors, an article's regions), its year, its status (a source) or kind (an article), its period, and its
  * own page. Pure.
  */
final case class TableRow(
    key: TableRow.Key,
    title: String,
    byline: String,
    year: Option[Year],
    status: Option[Source.Status],
    kind: String,
    period: String,
    page: String
)

object TableRow:
  /** The authors as the table shows them: the first three, then "et al.". */

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Key = ItemKey
  val Key: ItemKey.type = ItemKey
  def authors(names: List[String]): String =
    val named = names.map(_.trim).filter(_.nonEmpty)
    if named.size <= 3 then named.mkString(", ") else named.take(3).mkString(", ") + " et al."

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(h: Hit): TableRow =
    val s = h.source
    TableRow(
      Key(s.citekey.value),
      s.title.value,
      authors(s.authors.map(_.value)),
      s.year,
      Some(s.status),
      "",
      Year.period(s.periodStart, s.periodEnd),
      SourceLink.href(s.citekey)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def of(a: Article.Summary): TableRow =
    TableRow(
      Key(a.slug.value),
      a.title.value,
      a.regions.map(r => Label.display(Label.Text(r.value)).value).mkString(" · "),
      None,
      None,
      Wording.capitalize(Label.display(Label.Text(a.kind.value.trim)).value),
      Year.period(a.periodStart, a.periodEnd),
      ArticleLink.href(a.slug)
    )
