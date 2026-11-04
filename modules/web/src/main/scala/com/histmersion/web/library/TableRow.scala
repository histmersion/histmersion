package com.histmersion.web.library

import com.histmersion.api.{Wording, YearFmt}
import com.histmersion.api.library.{Hit, Label}
import com.histmersion.web.{ArticleLink, SourceLink}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** One row of a Library table, as it reads: the item's key (a citekey or a slug), its title and the line under it (a
  * source's authors, an article's regions), its year, its status (a source) or kind (an article), its period, and its
  * own page. Pure.
  */
final case class TableRow(
    key: String,
    title: String,
    byline: String,
    year: Option[Int],
    status: Option[Source.Status],
    kind: String,
    period: String,
    page: String
)

object TableRow:

  /** The authors as the table shows them: the first three, then "et al.". */
  def authors(names: List[String]): String =
    val named = names.map(_.trim).filter(_.nonEmpty)
    if named.size <= 3 then named.mkString(", ") else named.take(3).mkString(", ") + " et al."

  def of(h: Hit): TableRow =
    val s = h.source
    TableRow(
      s.citekey,
      s.title,
      authors(s.authors),
      s.year,
      Some(s.status),
      "",
      YearFmt.period(s.periodStart, s.periodEnd),
      SourceLink.href(s.citekey)
    )

  def of(a: Article.Summary): TableRow =
    TableRow(
      a.slug,
      a.title,
      a.regions.map(Label.display).mkString(" · "),
      None,
      None,
      Wording.capitalize(Label.display(a.kind.trim)),
      YearFmt.period(a.periodStart, a.periodEnd),
      ArticleLink.href(a.slug)
    )
