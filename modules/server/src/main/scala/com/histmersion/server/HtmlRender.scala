package com.histmersion
package server

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{HtmlAtoms, Wording, YearFmt}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.research.Article
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.api.research.ReviewGoal

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
  type Html = Html.T
  object Html extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Query = Query.T
  object Query extends RefinedType[String, Pure]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]

  private def escText(s: String): String = HtmlAtoms.esc(s)

  private def display(s: String): String = com.histmersion.api.library.Label.display(com.histmersion.api.library.Label.Text(s)).value

  def esc(s: Text): Html = Html(escText(s.value))

  // ── references ───────────────────────────────────────────────────────────────────────────────

  /** THE source anchor: `<a class="src-ref" data-ck data-doi href="/source/ck">label</a>`. The web UI's one delegated
    * hover handler previews anything carrying `data-ck`, and its click handler opens `/source/…` in a window — so every
    * source link on every page gets both for free.
    */
  def sourceLink(citekey: Citekey, doi: Option[Doi], label: Label): Html =
    Html(
      HtmlAtoms.sourceLink(
        com.histmersion.api.library.Citekey(citekey.value),
        doi.map(d => com.histmersion.api.library.Doi(d.value)),
        label.value
      )
    )

  /** A source reference labelled with its IDENTIFIER — the DOI when it has one, else the citekey. */
  def sourceRef(r: Source.Ref): Html = Html(HtmlAtoms.sourceLink(r.citekey, r.doi, r.idLabel.value))

  def sourceRef(r: Source.Ref, label: Label): Html = Html(HtmlAtoms.sourceLink(r.citekey, r.doi, label.value))

  /** Where a READABLE label is wanted (prose, lists): "Cline 2014", or the title when the source has no authors — never
    * a bare year.
    */
  def readable(r: Source.Ref): Html =
    if r.authors.exists(_.value.trim.nonEmpty) then Html(r.shortCite.value) else Html(r.title.value)

  /** THE article anchor: `<a class="art-ref" data-slug href="/article/slug">label</a>`. */
  def articleRef(r: Article.Ref): Html = articleRef(r, Label(r.title.value))

  def articleRef(r: Article.Ref, label: Label): Html =
    Html(HtmlAtoms.articleLink(ReviewGoal.Slug(r.slug.value), label.value))

  /** A link nothing in the corpus answers to: text, marked, never a link to a 404. */
  def dangling(label: Label): Html =
    Html(s"""<span class="dangling" title="unresolved link">${escText(label.value)}</span>""")

  /** The identifier line of a source: its DOI as a resolver link, else our own citekey. */
  def identifier(r: Source.Ref): Html = Html(identifierText(r.citekey.value, r.doi.map(_.value)))

  def identifier(citekey: Citekey, doi: Option[Doi]): Html =
    Html(identifierText(citekey.value, doi.map(_.value)))

  private def identifierText(citekey: String, doi: Option[String]): String = doi.filter(_.nonEmpty) match
    case Some(d) => s"""<a class="doi" href="https://doi.org/${escText(d)}" rel="noopener">${escText(d)}</a>"""
    case None    => s"""<code class="citekey">${escText(citekey)}</code>"""

  // ── chips and badges ─────────────────────────────────────────────────────────────────────────

  /** A filter chip: readable label ([[Label.display]] — never an underscore), the raw value in the link, so clicking it
    * filters the web UI by exactly the stored value.
    */
  def chip(field: ChipField, value: Label): Html =
    Html(
      s"""<a class="chip chip-${field.hashKey}" data-field="${field.hashKey}" data-value="${escText(value.value)}"""" +
        s""" href="/#${field.hashKey}=${escText(HtmlAtoms.segment(value.value))}">${escText(display(value.value))}</a>"""
    )

  def chips(field: ChipField, values: List[Label]): Html =
    Html(values.map(_.value.trim).filter(_.nonEmpty).distinct.map(v => chip(field, Label(v)).value).mkString(" "))

  /** A source's standing: the one status code — its mark (colour and shape, the stylesheet's `st-<status>`) and its
    * word.
    */
  def statusBadge(status: Source.Status): Html =
    Html(
      s"""<span class="status-badge st-${status.wire}" title="Status: ${Wording.status(
          status.transformInto[Source.Status]
        )} (${status.wire})">${Wording.status(status.transformInto[Source.Status])}</span>"""
    )

  /** Icon + colour, the same palette as the map's dots ([[ConfidenceStyle]]). */
  def confidenceBadge(c: Confidence): Html =
    Html(HtmlAtoms.confidenceBadge(c.transformInto[Confidence]))

  // ── findings ─────────────────────────────────────────────────────────────────────────────────

  def finding(f: Finding.Row): Html =
    val where = (f.regionName.map(_.value.trim).filter(_.nonEmpty).map(display), f.lat, f.lon) match
      // Locale.ROOT: JDK 25's CLDR renders `35,60` under a comma-decimal default locale
      case (r, Some(la), Some(lo)) =>
        r.fold("")(_ + " ") + String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", la.value, lo.value)
      case (r, _, _) => r.getOrElse("")
    val period = YearFmt.period(f.periodStart, f.periodEnd)
    val meta = List(
      f.confidence.map(c => confidenceBadge(c).value).getOrElse(""),
      escText(where),
      escText(f.periodName.map(_.value).filter(n => n.nonEmpty && period.nonEmpty).fold(period)(n => s"$period (${display(n)})")),
      f.significance.map(_.value).filter(_.nonEmpty).fold("")(s => s"""<span class="significance">${escText(display(s))}</span>""")
    ).filter(_.nonEmpty)
    Html(
      s"""<li class="finding"><div class="claim">${escText(f.claim.value)}</div>""" +
        (if meta.isEmpty then "" else s"""<div class="finding-meta">${meta.mkString(" · ")}</div>""") + "</li>"
    )
