package com.histmersion.web.library

import com.histmersion.api.{Wording, YearFmt}
import com.histmersion.api.library.{Label, SearchHit}
import com.histmersion.api.media.MediaRole
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** The top of the reading pane, as it reads: what the item is (a source's type, venue and year; an article's kind), its
  * title, its authors, its status, its period, its regions and its summary. A row of the table already holds most of
  * it, so the pane shows it at once and fills in the rest when the item is read. Pure.
  */
final case class ReadingHead(
    what: String,
    title: String,
    authors: String,
    status: Option[Source.Status],
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
    Option.when(kind != com.histmersion.api.library.Source.Kinds.Unknown)(Wording.capitalize(kind))

  /** From a row of the table: no type or venue yet. */
  def of(h: SearchHit): ReadingHead =
    ReadingHead(
      line(h.year.map(_.value.toInt).map(_.toString)),
      h.title.value,
      h.authors.map(_.value).mkString(", "),
      Some(h.status),
      YearFmt.period(h.periodStart, h.periodEnd),
      regions(h.regions),
      text(h.summary)
    )

  def of(sw: com.histmersion.api.library.Source.WithFindings): ReadingHead =
    val s = sw.source
    val kind = com.histmersion.api.library.Source.Kinds.of(
      s.doi.map(_.value),
      s.venue.map(_.value),
      s.provenance,
      s.tags,
      s.topics,
      hasCover = sw.illustration.exists(_.role == MediaRole.Cover)
    )
    ReadingHead(
      line(kindWord(kind), s.venue.map(_.value), s.year.map(_.value.toInt).map(_.toString)),
      s.title.value,
      s.authors.map(_.value).mkString(", "),
      Some(s.status),
      YearFmt.period(s.periodStart, s.periodEnd),
      regions(s.regions),
      text(s.summary)
    )

  def of(a: Article.Summary): ReadingHead =
    ReadingHead(
      line(Some("Article"), Some(Wording.capitalize(Label.display(a.kind)))),
      a.title,
      "",
      None,
      YearFmt.period(a.periodStart, a.periodEnd),
      regions(a.regions),
      text(a.summary)
    )

  def of(v: Article.View): ReadingHead =
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
