package com.histmersion
package server

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{Html, Wording, YearFmt}
import com.histmersion.api.Html.{html, Atoms}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.api.research.ReviewGoal

/** The HTML atoms of the server-rendered pages — PURE builders of [[Html]], one shape per thing, so a source reference
  * looks and behaves the same in an article's prose, its "Sources cited" footer, a source's "Cites / Cited by" list and
  * the backlinks.
  *
  * This is the HUMAN surface. The Markdown in `core` ([[Markdown]], [[ArticleMarkdown]]) is the LLM surface (MCP, CLI)
  * and deliberately stays separate: what reads well to an agent — `[[citekey]]`, "an empty result is not evidence of
  * agreement" — is not what a reader wants on a page.
  *
  * Every value that came from the database is a text the `html"…"` interpolator escapes; only an [[Html]] is spliced as
  * it is.
  */
object HtmlRender:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  private def display(s: String): String =
    com.histmersion.api.library.Label.display(com.histmersion.api.library.Label.Text(s)).value

  def esc(s: Text): Html = Html.text(s.value)

  // ── references ───────────────────────────────────────────────────────────────────────────────

  /** THE source anchor: `<a class="src-ref" data-ck data-doi href="/source/ck">label</a>`. The web UI's one delegated
    * hover handler previews anything carrying `data-ck`, and its click handler opens `/source/…` in a window — so every
    * source link on every page gets both for free.
    */
  def sourceLink(citekey: Citekey, doi: Option[Doi], label: Label): Html =
    Atoms.sourceLink(
      com.histmersion.api.library.Citekey(citekey.value),
      doi.map(d => com.histmersion.api.library.Doi(d.value)),
      label.value
    )

  /** A source reference labelled with its IDENTIFIER — the DOI when it has one, else the citekey. */
  def sourceRef(r: Source.Ref): Html = Atoms.sourceLink(r.citekey, r.doi, r.idLabel.value)

  def sourceRef(r: Source.Ref, label: Label): Html = Atoms.sourceLink(r.citekey, r.doi, label.value)

  /** Where a READABLE label is wanted (prose, lists): "Cline 2014", or the title when the source has no authors — never
    * a bare year. A label: the anchor it goes into escapes it.
    */
  def readable(r: Source.Ref): Label =
    if r.authors.exists(_.value.trim.nonEmpty) then Label(r.shortCite.value) else Label(r.title.value)

  /** THE article anchor: `<a class="art-ref" data-slug href="/article/slug">label</a>`. */
  def articleRef(r: Article.Ref): Html = articleRef(r, Label(r.title.value))

  def articleRef(r: Article.Ref, label: Label): Html =
    Atoms.articleLink(ReviewGoal.Slug(r.slug.value), label.value)

  /** A link nothing in the corpus answers to: text, marked, never a link to a 404. */
  def dangling(label: Label): Html =
    html"""<span class="dangling" title="unresolved link">${label.value}</span>"""

  /** The identifier line of a source: its DOI as a resolver link, else our own citekey. */
  def identifier(r: Source.Ref): Html = identifierOf(r.citekey.value, r.doi.map(_.value))

  def identifier(citekey: Citekey, doi: Option[Doi]): Html = identifierOf(citekey.value, doi.map(_.value))

  private def identifierOf(citekey: String, doi: Option[String]): Html = doi.filter(_.nonEmpty) match
    case Some(d) => html"""<a class="doi" href="https://doi.org/$d" rel="noopener">$d</a>"""
    case None    => html"""<code class="citekey">$citekey</code>"""

  // ── chips and badges ─────────────────────────────────────────────────────────────────────────

  /** A filter chip: readable label ([[Label.display]] — never an underscore), the raw value in the link, so clicking it
    * filters the web UI by exactly the stored value.
    */
  def chip(field: ChipField, value: Label): Html =
    val key = field.hashKey
    val target = s"/#${key.value}=${Atoms.segment(value.value)}"
    html"""<a class="chip chip-$key" data-field="$key" data-value="${value.value}"""" ++
      html""" href="$target">${display(value.value)}</a>"""

  def chips(field: ChipField, values: List[Label]): Html =
    Html.join(values.map(_.value.trim).filter(_.nonEmpty).distinct.map(v => chip(field, Label(v))), Html.text(" "))

  /** A source's standing: the one status code — its mark (colour and shape, the stylesheet's `st-<status>`) and its
    * word.
    */
  def statusBadge(status: Source.Status): Html =
    val word = Wording.status(status.transformInto[Source.Status])
    html"""<span class="status-badge st-${status.wire}" title="Status: $word (${status.wire})">$word</span>"""

  /** Icon + colour, the same palette as the map's dots ([[ConfidenceStyle]]). */
  def confidenceBadge(c: Confidence): Html =
    Atoms.confidenceBadge(c.transformInto[Confidence])

  // ── findings ─────────────────────────────────────────────────────────────────────────────────

  def finding(f: Finding.Row): Html =
    val where = (f.regionName.map(_.value.trim).filter(_.nonEmpty).map(display), f.lat, f.lon) match
      // Locale.ROOT: JDK 25's CLDR renders `35,60` under a comma-decimal default locale
      case (r, Some(la), Some(lo)) =>
        r.fold("")(_ + " ") + String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", la.value, lo.value)
      case (r, _, _) => r.getOrElse("")
    val period = YearFmt.period(f.periodStart, f.periodEnd)
    val meta = List(
      f.confidence.fold(Html.empty)(confidenceBadge),
      Html.text(where),
      Html.text(
        f.periodName
          .map(_.value)
          .filter(n => n.nonEmpty && period.nonEmpty)
          .fold(period)(n => s"$period (${display(n)})")
      ),
      f.significance
        .map(_.value)
        .filter(_.nonEmpty)
        .fold(Html.empty)(s => html"""<span class="significance">${display(s)}</span>""")
    ).filter(_.nonEmpty)
    html"""<li class="finding"><div class="claim">${f.claim.value}</div>""" ++
      (if meta.isEmpty then Html.empty
       else html"""<div class="finding-meta">${Html.join(meta, Html.text(" · "))}</div>""") ++
      html"</li>"
