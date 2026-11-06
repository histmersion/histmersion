package com.histmersion.server

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{HtmlAtoms, Wording, YearFmt}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Label
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.research.Article

/** The HTML atoms of the server-rendered pages — PURE string builders, one shape per thing, so a source reference looks
  * and behaves the same in an article's prose, its "Sources cited" footer, a source's "Cites / Cited by" list and the
  * backlinks.
  *
  * This is the HUMAN surface. The Markdown in `core` ([[Markdown]], [[ArticleMarkdown]]) is the LLM surface (MCP, CLI)
  * and deliberately stays separate: what reads well to an agent — `[[citekey]]`, "an empty result is not evidence of
  * agreement" — is not what a reader wants on a page.
  *
  * Every value that came from the database is escaped here, never at the call site.
  */
object HtmlRender:

  type EscResult = EscResult.T
  object EscResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscS = EscS.T
  object EscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkResult = SourceLinkResult.T
  object SourceLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkCitekey = SourceLinkCitekey.T
  object SourceLinkCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkDoi = SourceLinkDoi.T
  object SourceLinkDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLinkLabel = SourceLinkLabel.T
  object SourceLinkLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceRefResult = SourceRefResult.T
  object SourceRefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceRefLabel = _root_.com.histmersion.server.HtmlRender.SourceLinkLabel
  val SourceRefLabel: _root_.com.histmersion.server.HtmlRender.SourceLinkLabel.type = _root_.com.histmersion.server.HtmlRender.SourceLinkLabel
  type ReadableResult = ReadableResult.T
  object ReadableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleRefResult = ArticleRefResult.T
  object ArticleRefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleRefLabel = ArticleRefLabel.T
  object ArticleRefLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DanglingResult = DanglingResult.T
  object DanglingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DanglingLabel = DanglingLabel.T
  object DanglingLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifierResult = IdentifierResult.T
  object IdentifierResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifierCitekey = IdentifierCitekey.T
  object IdentifierCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifierDoi = IdentifierDoi.T
  object IdentifierDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipResult = ChipResult.T
  object ChipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipValue = ChipValue.T
  object ChipValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsResult = ChipsResult.T
  object ChipsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipsValues = ChipsValues.T
  object ChipsValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusBadgeResult = StatusBadgeResult.T
  object StatusBadgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfidenceBadgeResult = ConfidenceBadgeResult.T
  object ConfidenceBadgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingResult = FindingResult.T
  object FindingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def esc(s: com.histmersion.server.HtmlRender.EscS): com.histmersion.server.HtmlRender.EscResult = HtmlAtoms.esc(s)

  // ── references ───────────────────────────────────────────────────────────────────────────────

  /** THE source anchor: `<a class="src-ref" data-ck data-doi href="/source/ck">label</a>`. The web UI's one delegated
    * hover handler previews anything carrying `data-ck`, and its click handler opens `/source/…` in a window — so every
    * source link on every page gets both for free.
    */
  def sourceLink(citekey: com.histmersion.server.HtmlRender.SourceLinkCitekey, doi: Option[com.histmersion.server.HtmlRender.SourceLinkDoi], label: com.histmersion.server.HtmlRender.SourceLinkLabel): com.histmersion.server.HtmlRender.SourceLinkResult =
    HtmlAtoms.sourceLink(citekey, doi, label)

  /** A source reference labelled with its IDENTIFIER — the DOI when it has one, else the citekey. */
  def sourceRef(r: com.histmersion.api.library.Source.Ref): com.histmersion.server.HtmlRender.SourceRefResult = sourceLink(r.citekey, r.doi, r.idLabel)

  def sourceRef(r: com.histmersion.api.library.Source.Ref, label: com.histmersion.server.HtmlRender.SourceRefLabel): com.histmersion.server.HtmlRender.SourceRefResult = sourceLink(r.citekey, r.doi, label)

  /** Where a READABLE label is wanted (prose, lists): "Cline 2014", or the title when the source has no authors — never
    * a bare year.
    */
  def readable(r: com.histmersion.api.library.Source.Ref): com.histmersion.server.HtmlRender.ReadableResult =
    if r.authors.exists(_.trim.nonEmpty) then r.shortCite else r.title

  /** THE article anchor: `<a class="art-ref" data-slug href="/article/slug">label</a>`. */
  def articleRef(r: Article.Ref): com.histmersion.server.HtmlRender.ArticleRefResult = articleRef(r, r.title)

  def articleRef(r: Article.Ref, label: com.histmersion.server.HtmlRender.ArticleRefLabel): com.histmersion.server.HtmlRender.ArticleRefResult = HtmlAtoms.articleLink(r.slug, label)

  /** A link nothing in the corpus answers to: text, marked, never a link to a 404. */
  def dangling(label: com.histmersion.server.HtmlRender.DanglingLabel): com.histmersion.server.HtmlRender.DanglingResult =
    s"""<span class="dangling" title="unresolved link">${esc(label)}</span>"""

  /** The identifier line of a source: its DOI as a resolver link, else our own citekey. */
  def identifier(r: com.histmersion.api.library.Source.Ref): com.histmersion.server.HtmlRender.IdentifierResult = identifier(r.citekey, r.doi)

  def identifier(citekey: com.histmersion.server.HtmlRender.IdentifierCitekey, doi: Option[com.histmersion.server.HtmlRender.IdentifierDoi]): com.histmersion.server.HtmlRender.IdentifierResult = doi.filter(_.nonEmpty) match
    case Some(d) => s"""<a class="doi" href="https://doi.org/${esc(d)}" rel="noopener">${esc(d)}</a>"""
    case None    => s"""<code class="citekey">${esc(citekey)}</code>"""

  // ── chips and badges ─────────────────────────────────────────────────────────────────────────

  /** A filter chip: readable label ([[Label.display]] — never an underscore), the raw value in the link, so clicking it
    * filters the web UI by exactly the stored value.
    */
  def chip(field: ChipField, value: com.histmersion.server.HtmlRender.ChipValue): com.histmersion.server.HtmlRender.ChipResult =
    s"""<a class="chip chip-${field.hashKey}" data-field="${field.hashKey}" data-value="${esc(value)}"""" +
      s""" href="/#${field.hashKey}=${esc(HtmlAtoms.segment(value))}">${esc(Label.display(value))}</a>"""

  def chips(field: ChipField, values: List[com.histmersion.server.HtmlRender.ChipsValues]): com.histmersion.server.HtmlRender.ChipsResult =
    values.map(_.trim).filter(_.nonEmpty).distinct.map(chip(field, _)).mkString(" ")

  /** A source's standing: the one status code — its mark (colour and shape, the stylesheet's `st-<status>`) and its
    * word.
    */
  def statusBadge(status: Source.Status): com.histmersion.server.HtmlRender.StatusBadgeResult =
    s"""<span class="status-badge st-${status.wire}" title="Status: ${Wording.status(
        status.transformInto[com.histmersion.api.library.Source.Status]
      )} (${status.wire})">${Wording.status(status.transformInto[com.histmersion.api.library.Source.Status])}</span>"""

  /** Icon + colour, the same palette as the map's dots ([[ConfidenceStyle]]). */
  def confidenceBadge(c: Confidence): com.histmersion.server.HtmlRender.ConfidenceBadgeResult =
    HtmlAtoms.confidenceBadge(c.transformInto[com.histmersion.api.library.Confidence])

  // ── findings ─────────────────────────────────────────────────────────────────────────────────

  def finding(f: Finding.Row): com.histmersion.server.HtmlRender.FindingResult =
    val where = (f.regionName.map(_.trim).filter(_.nonEmpty).map(Label.display), f.lat, f.lon) match
      // Locale.ROOT: JDK 25's CLDR renders `35,60` under a comma-decimal default locale
      case (r, Some(la), Some(lo)) =>
        r.fold("")(_ + " ") + String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", la, lo)
      case (r, _, _) => r.getOrElse("")
    val period = YearFmt.period(f.periodStart, f.periodEnd)
    val meta = List(
      f.confidence.map(confidenceBadge).getOrElse(""),
      esc(where),
      esc(f.periodName.filter(n => n.nonEmpty && period.nonEmpty).fold(period)(n => s"$period (${Label.display(n)})")),
      f.significance.filter(_.nonEmpty).fold("")(s => s"""<span class="significance">${esc(Label.display(s))}</span>""")
    ).filter(_.nonEmpty)
    s"""<li class="finding"><div class="claim">${esc(f.claim)}</div>""" +
      (if meta.isEmpty then "" else s"""<div class="finding-meta">${meta.mkString(" · ")}</div>""") + "</li>"
