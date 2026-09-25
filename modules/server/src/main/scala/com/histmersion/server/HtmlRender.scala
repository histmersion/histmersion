package com.histmersion.server

import com.histmersion.core.*

/** What a `[[wikilink]]` resolves to on an HTML page: a stored source or a compiled article. */
type LinkTarget = SourceRef | ArticleRef

/** A metadata dimension a chip filters the web UI by. `hashKey` is the web UI's URL-hash key, so a
  * chip is a link that works in the modal, on a standalone page and pasted anywhere. */
enum ChipField(val hashKey: String):
  case Topic  extends ChipField("topic")
  case Place  extends ChipField("place")
  case Tag    extends ChipField("tag")
  case Author extends ChipField("author")

/** The HTML atoms of the server-rendered pages — PURE string builders, one shape per thing, so a
  * source reference looks and behaves the same in an article's prose, its "Sources cited" footer, a
  * source's "Cites / Cited by" list and the backlinks.
  *
  * This is the HUMAN surface. The Markdown in `core` ([[Markdown]], [[ArticleMarkdown]]) is the LLM
  * surface (MCP, CLI) and deliberately stays separate: what reads well to an agent — `[[citekey]]`,
  * "an empty result is not evidence of agreement" — is not what a reader wants on a page.
  *
  * Every value that came from the database is escaped here, never at the call site. */
object HtmlRender:

  def esc(s: String): String = HtmlAtoms.esc(s)


  // ── references ───────────────────────────────────────────────────────────────────────────────

  /** THE source anchor: `<a class="src-ref" data-ck data-doi href="/source/ck">label</a>`. The web
    * UI's one delegated hover handler previews anything carrying `data-ck`, and its click handler
    * opens `/source/…` in a window — so every source link on every page gets both for free. */
  def sourceLink(citekey: String, doi: Option[String], label: String): String =
    HtmlAtoms.sourceLink(citekey, doi, label)

  /** A source reference labelled with its IDENTIFIER — the DOI when it has one, else the citekey. */
  def sourceRef(r: SourceRef): String = sourceLink(r.citekey, r.doi, r.idLabel)

  def sourceRef(r: SourceRef, label: String): String = sourceLink(r.citekey, r.doi, label)

  /** Where a READABLE label is wanted (prose, lists): "Cline 2014", or the title when the source has
    * no authors — never a bare year. */
  def readable(r: SourceRef): String =
    if r.authors.exists(_.trim.nonEmpty) then r.shortCite else r.title

  /** THE article anchor: `<a class="art-ref" data-slug href="/article/slug">label</a>`. */
  def articleRef(r: ArticleRef): String = articleRef(r, r.title)

  def articleRef(r: ArticleRef, label: String): String = HtmlAtoms.articleLink(r.slug, label)

  /** A link nothing in the corpus answers to: text, marked, never a link to a 404. */
  def dangling(label: String): String =
    s"""<span class="dangling" title="unresolved link">${esc(label)}</span>"""

  /** The identifier line of a source: its DOI as a resolver link, else our own citekey. */
  def identifier(r: SourceRef): String = identifier(r.citekey, r.doi)

  def identifier(citekey: String, doi: Option[String]): String = doi.filter(_.nonEmpty) match
    case Some(d) => s"""<a class="doi" href="https://doi.org/${esc(d)}" rel="noopener">${esc(d)}</a>"""
    case None    => s"""<code class="citekey">${esc(citekey)}</code>"""

  // ── chips and badges ─────────────────────────────────────────────────────────────────────────

  /** A filter chip: readable label ([[Label.display]] — never an underscore), the raw value in the
    * link, so clicking it filters the web UI by exactly the stored value. */
  def chip(field: ChipField, value: String): String =
    s"""<a class="chip chip-${field.hashKey}" data-field="${field.hashKey}" data-value="${esc(value)}"""" +
      s""" href="/#${field.hashKey}=${esc(HtmlAtoms.segment(value))}">${esc(Label.display(value))}</a>"""

  def chips(field: ChipField, values: List[String]): String =
    values.map(_.trim).filter(_.nonEmpty).distinct.map(chip(field, _)).mkString(" ")

  def statusBadge(status: SourceStatus): String =
    val wire = status.toString.toLowerCase
    s"""<span class="status-badge st-$wire">$wire</span>"""

  /** Icon + colour, the same palette as the map's dots ([[ConfidenceStyle]]). */
  def confidenceBadge(c: Confidence): String = HtmlAtoms.confidenceBadge(c)

  // ── findings ─────────────────────────────────────────────────────────────────────────────────

  def finding(f: FindingRow): String =
    val where = (f.regionName.map(_.trim).filter(_.nonEmpty).map(Label.display), f.lat, f.lon) match
      // Locale.ROOT: JDK 25's CLDR renders `35,60` under a comma-decimal default locale
      case (r, Some(la), Some(lo)) =>
        r.fold("")(_ + " ") + String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", la, lo)
      case (r, _, _)               => r.getOrElse("")
    val period = YearFmt.period(f.periodStart, f.periodEnd)
    val meta = List(
      f.confidence.map(confidenceBadge).getOrElse(""),
      esc(where),
      esc(f.periodName.filter(n => n.nonEmpty && period.nonEmpty).fold(period)(n => s"$period (${Label.display(n)})")),
      f.significance.filter(_.nonEmpty).fold("")(s => s"""<span class="significance">${esc(Label.display(s))}</span>""")
    ).filter(_.nonEmpty)
    s"""<li class="finding"><div class="claim">${esc(f.claim)}</div>""" +
      (if meta.isEmpty then "" else s"""<div class="finding-meta">${meta.mkString(" · ")}</div>""") + "</li>"
