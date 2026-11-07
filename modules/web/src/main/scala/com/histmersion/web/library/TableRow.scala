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
    key: TableRow.Key,
    title: TableRow.Title,
    byline: TableRow.Byline,
    year: Option[TableRow.Year],
    status: Option[Source.Status],
    kind: TableRow.Kind,
    period: TableRow.Period,
    page: TableRow.Page
)

object TableRow:
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The authors as the table shows them: the first three, then "et al.". */

  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = com.histmersion.api.library.Title
  val Title: _root_.com.histmersion.api.library.Title.type = _root_.com.histmersion.api.library.Title
  type Byline = Byline.T
  object Byline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Period = Period.T
  object Period extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Page = Page.T
  object Page extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def authors(names: List[com.histmersion.api.library.AuthorName]): com.histmersion.web.library.TableRow.Authors =
    val named = names.map(_.trim).filter(_.nonEmpty)
    if named.size <= 3 then named.mkString(", ") else named.take(3).mkString(", ") + " et al."

  def of(h: Hit): TableRow =
    val s = h.source
    TableRow(
      s.citekey.value.toString,
      s.title.value,
      authors(s.authors.map(_.value)),
      s.year.map(_.value.toInt),
      Some(s.status),
      com.histmersion.web.library.TableRow.Kind(""),
      YearFmt.period(s.periodStart, s.periodEnd),
      SourceLink.href(s.citekey.value.toString)
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
