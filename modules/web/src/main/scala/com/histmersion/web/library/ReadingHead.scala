package com.histmersion
package web.library

import com.histmersion.api.Wording
import com.histmersion.api.library.{Label, SearchHit}
import com.histmersion.api.media.MediaRole
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Year

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
    summary: Option[String],
    created: Option[String] = None
)

object ReadingHead:
  private def line(parts: Option[String]*): String = parts.flatten.map(_.trim).filter(_.nonEmpty).mkString(" · ")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def regions(rs: List[String]): String = rs.map(r => Label.display(Label.Text(r)).value).mkString(" · ")
  private def text(s: Option[String]): Option[String] = s.map(_.trim).filter(_.nonEmpty)

  /** A source's type as a word (`Book`), none when it is not known. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def kindWord(kind: Source.Kinds.Wire): Option[String] =
    Option.when(kind.value != Source.Kinds.Unknown)(Wording.capitalize(kind.value))

  /** From a row of the table: no type or venue yet. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(h: SearchHit): ReadingHead =
    ReadingHead(
      line(h.year.map(_.value.toInt).map(_.toString)),
      h.title.value,
      h.authors.map(_.value).mkString(", "),
      Some(h.status),
      Year.period(h.periodStart, h.periodEnd),
      regions(h.regions.map(_.value)),
      text(h.summary.map(_.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(sw: Source.WithFindings): ReadingHead =
    val s = sw.source
    val kind = Source.Kinds.of(
      s.doi,
      s.venue,
      s.provenance,
      s.tags,
      s.topics,
      hasCover = sw.illustration.exists(_.role.value == MediaRole.Cover)
    )
    ReadingHead(
      line(kindWord(kind), s.venue.map(_.value), s.year.map(_.value.toInt).map(_.toString)),
      s.title.value,
      s.authors.map(_.value).mkString(", "),
      Some(s.status),
      Year.period(s.periodStart, s.periodEnd),
      regions(s.regions.map(_.value)),
      text(s.summary.map(_.value)),
      Creation.line(s.creator, s.createdAt)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def of(a: Article.Summary): ReadingHead =
    ReadingHead(
      line(Some("Article"), Some(Wording.capitalize(Label.display(Label.Text(a.kind.value)).value))),
      a.title.value,
      "",
      None,
      Year.period(a.periodStart, a.periodEnd),
      regions(a.regions.map(_.value)),
      text(a.summary.map(_.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def of(v: Article.View): ReadingHead =
    val a = v.article
    ReadingHead(
      line(Some("Article"), Some(Wording.capitalize(Label.display(Label.Text(a.kind.value)).value))),
      a.title.value,
      "",
      None,
      Year.period(a.periodStart, a.periodEnd),
      regions(a.regions.map(_.value)),
      text(a.summary.map(_.value)),
      Creation.line(a.creator, a.createdAt)
    )
