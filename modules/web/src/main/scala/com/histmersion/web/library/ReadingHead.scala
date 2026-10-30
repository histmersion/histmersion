package com.histmersion.web.library

import com.histmersion.api.{
  ArticleSummary,
  ArticleView,
  Label,
  SearchHit,
  SourceKinds,
  SourceWithFindings,
  Wording,
  YearFmt
}
import com.histmersion.values.{MediaRole, SourceStatus}

/** The top of the reading pane, as it reads: what the item is (a source's type, venue and year; an article's kind), its
  * title, its authors, its status, its period, its regions and its summary. A row of the table already holds most of
  * it, so the pane shows it at once and fills in the rest when the item is read. Pure.
  */
final case class ReadingHead(
    what: String,
    title: String,
    authors: String,
    status: Option[SourceStatus],
    period: String,
    regions: String,
    summary: Option[String]
)

object ReadingHead:

  private def line(parts: Option[String]*): String = parts.flatten.map(_.trim).filter(_.nonEmpty).mkString(" · ")
  private def regions(rs: List[String]): String = rs.map(Label.display).mkString(" · ")
  private def text(s: Option[String]): Option[String] = s.map(_.trim).filter(_.nonEmpty)

  /** A source's type as a word (`Book`), none when it is not known. */
  def kindWord(kind: String): Option[String] =
    Option.when(kind != SourceKinds.Unknown)(Wording.capitalize(kind))

  /** From a row of the table: no type or venue yet. */
  def of(h: SearchHit): ReadingHead =
    ReadingHead(
      line(h.year.map(_.toString)),
      h.title,
      h.authors.mkString(", "),
      Some(h.status),
      YearFmt.period(h.periodStart, h.periodEnd),
      regions(h.regions),
      text(h.summary)
    )

  def of(sw: SourceWithFindings): ReadingHead =
    val s = sw.source
    val kind = SourceKinds.of(
      s.doi,
      s.venue,
      s.provenance,
      s.tags,
      s.topics,
      hasCover = sw.illustration.exists(_.role == MediaRole.Cover)
    )
    ReadingHead(
      line(kindWord(kind), s.venue, s.year.map(_.toString)),
      s.title,
      s.authors.mkString(", "),
      Some(s.status),
      YearFmt.period(s.periodStart, s.periodEnd),
      regions(s.regions),
      text(s.summary)
    )

  def of(a: ArticleSummary): ReadingHead =
    ReadingHead(
      line(Some("Article"), Some(Wording.capitalize(Label.display(a.kind)))),
      a.title,
      "",
      None,
      YearFmt.period(a.periodStart, a.periodEnd),
      regions(a.regions),
      text(a.summary)
    )

  def of(v: ArticleView): ReadingHead =
    val a = v.article
    ReadingHead(
      line(Some("Article"), Some(Wording.capitalize(Label.display(a.kind)))),
      a.title,
      "",
      None,
      YearFmt.period(a.periodStart, a.periodEnd),
      regions(a.regions),
      text(a.summary)
    )
