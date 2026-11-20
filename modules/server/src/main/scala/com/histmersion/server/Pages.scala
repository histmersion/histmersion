package com.histmersion
package server

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{Plural, Wording, YearFmt}
import com.histmersion.api.Html.Atoms
import com.histmersion.config.*
import com.histmersion.domain.*
// explicit: pins `Article` to the page helpers in HttpApi.scala over `core.Article` (see ArticlesApi)
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatMarkdown, FormatSeverity}
import com.histmersion.api.library.Label
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.research.{ArticleMarkdown, Articles, Backlink, CitedSource, ReviewBadge, ReviewGoal}
import com.histmersion.api.automation.Goal
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.checks.FormatReport
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.library.Edge

/** PURE rendering of the typed page models to HTML. Wikilinks resolve through `links` (one lookup for the whole page,
  * done by [[PageBuilder]]) into the [[HtmlRender]] anchors.
  */
object Pages:
  type Anchor = Anchor.T
  object Anchor extends Newtype[String, Pure]

  private def esc(s: String): String = HtmlRender.esc(HtmlRender.Text(s)).value

  private def display(s: String): String = Label.display(Label.Text(s)).value

  private def sourceRef(r: Source.Ref, label: String): String = HtmlRender.sourceRef(r, HtmlRender.Label(label)).value

  private def readable(r: Source.Ref): String = HtmlRender.readable(r).value

  private def articleRef(a: Article.Ref, label: String): String =
    HtmlRender.articleRef(a, HtmlRender.Label(label)).value

  private def dangling(label: String): String = HtmlRender.dangling(HtmlRender.Label(label)).value

  private def chips(field: ChipField, values: List[String]): String =
    HtmlRender.chips(field, values.map(HtmlRender.Label(_))).value

  // ── prose ────────────────────────────────────────────────────────────────────────────────────

  /** Markdown with every `[[wikilink]]` replaced by its typed anchor (raw inline HTML, which flexmark passes through).
    * `number` appends the citation number to a source reference.
    */
  def linkProse(
      md: FullText.Shown.Markdown,
      links: WikiLinks.Occurrence.Target => Option[LinkTarget],
      number: Citekey => Option[Int] = _ => None
  ): HtmlRender.Html =
    HtmlRender.Html(
      Article.Links
        .rewriteLinks(WikiLinks.Body(md.value)) { (t, explicit, written) =>
          val own = explicit.map(_.value).filter(_.nonEmpty)
          links(t) match
            case Some(r: Source.Ref) =>
              sourceRef(r, own.getOrElse(readable(r))) + number(r.citekey).fold("")(citeNumber)
            case Some(a: Article.Ref) => articleRef(a, own.getOrElse(a.title.value))
            case None                 => dangling(own.getOrElse(display(written.value)))
        }
        .value
    )

  private def citeNumber(n: Int): String =
    s"""<sup class="cite-num"><a class="cite-jump" href="#cite-$n">[$n]</a></sup>"""

  /** A heading that starts the part of an article whose sources are NOT cited in its argument — the wiki's
    * `## Not used` convention (`**[[ck]]**: why`). Links there are rendered like any other reference but get no
    * citation number.
    */
  private val NotUsed = """(?im)^#{1,6}\s*not used\b.*$""".r

  /** Citation numbers by first appearance in `md` (the argument part of an article). */
  def numbering(
      md: FullText.Shown.Markdown,
      links: WikiLinks.Occurrence.Target => Option[LinkTarget]
  ): Map[Citekey, Int] =
    Article.Links
      .targets(WikiLinks.Body(md.value))
      .flatMap(t => links(t).collect { case r: Source.Ref => r.citekey })
      .distinct
      .zipWithIndex
      .map((ck, i) => ck -> (i + 1))
      .toMap

  // ── the formatting notice ────────────────────────────────────────────────────────────────────

  /** "This article may not display correctly: 3 formatting problems", folded: it expands to one line per problem —
    * where, what goes wrong, the offending text. Unobtrusive on purpose (a reader came for the article); nothing at all
    * when the text is clean.
    */
  def formatNotice(issues: List[FormatIssue], what: String, slug: Option[Article.Slug]): HtmlRender.Html =
    if issues.isEmpty then HtmlRender.Html("")
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val level = if errs > 0 then "has-errors" else "has-warnings"
      val items = issues.map { i =>
        val sev = if i.severity == FormatSeverity.Error then "error" else "warning"
        val field = if i.field == FormatField.Body then "" else s"${esc(i.field.toString.toLowerCase)}, "
        val ex = if i.excerpt.value.isEmpty then "" else s""" <code class="fi-excerpt">${esc(i.excerpt.value)}</code>"""
        s"""<li class="fi fi-$sev"><span class="fi-where">${field}line ${i.line}</span> """ +
          s"""<span class="fi-rule">${esc(display(i.rule.wire.value.replace('-', '_')))}</span> — ${esc(
              i.message.value
            )}$ex</li>"""
      }
      val fixable = issues.count(_.fix.nonEmpty)
      val how =
        slug.fold(s"histmersion lint --source &lt;citekey&gt; --fix")(s =>
          s"histmersion article lint ${esc(s.value)} --fix"
        )
      val tip =
        if fixable == 0 then ""
        else
          val which = if fixable == issues.size then "all of them" else s"$fixable of them"
          s"""<p class="fi-tip"><code>$how</code> repairs $which without changing a word.</p>"""
      HtmlRender.Html(
        s"""<details class="format-notice $level"><summary>$what may not display correctly: """ +
          s"""${FormatMarkdown.problems(
              FormatReport.IssueCount(issues.size)
            )}</summary><ul class="format-issues">${items.mkString}</ul>$tip</details>"""
      )

  // ── source page ──────────────────────────────────────────────────────────────────────────────

  def source(p: SourcePage, links: WikiLinks.Occurrence.Target => Option[LinkTarget]): HtmlRender.Html =
    val r = p.ref
    val period = YearFmt.period(
      p.periodStart.map(y => Year(y.value)),
      p.periodEnd.map(y => Year(y.value))
    )
    val byline = List(
      chips(ChipField.Author, r.authors.map(_.value)),
      r.year.fold("n.d.")(_.toString),
      p.venue.filter(_.value.trim.nonEmpty).fold("")(v => esc(v.value)),
      HtmlRender.statusBadge(r.status).value,
      esc(period)
    ).filter(_.nonEmpty).mkString(" · ")
    val ident = r.doi.filter(_.value.nonEmpty).fold("Citekey")(_ => "DOI")
    val facets = List(
      "Topics" -> chips(ChipField.Topic, p.topics.map(_.value)),
      "Regions" -> chips(ChipField.Place, p.regions.map(_.value)),
      "Tags" -> chips(ChipField.Tag, p.tags.map(_.value))
    ).collect { case (l, c) if c.nonEmpty => s"""<p class="src-chips"><span class="lbl">$l</span> $c</p>""" }
    // the figure pipeline of plan §1.8 (legacy `figures/…` links, numbering, served paths, lazy
    // images) runs over every Markdown part of a source, as it did when the page was one document
    def rendered(md: String): String =
      val linked = AssetLinks.numberFigures(
        AssetLinks.fromFigures(AssetLinks.Body(linkProse(FullText.Shown.Markdown(md), links).value))
      )
      AssetLinks
        .lazyImages(
          AssetLinks.Html(
            MarkdownHtml.render(FullText.Shown.Markdown(AssetLinks.toWeb(linked, r.citekey).value)).value
          )
        )
        .value
    val prose = (md: Option[String]) => md.map(_.trim).filter(_.nonEmpty).map(rendered)
    val findings =
      if p.findings.isEmpty then ""
      else
        s"""<h2 id="findings">Findings (${p.findings.size})</h2><ul class="findings">${p.findings
            .map(f => HtmlRender.finding(f).value)
            .mkString}</ul>"""
    val body = p.fullText match
      case FullText.Hidden  => ""
      case FullText.Missing => """<hr><p class="muted">No full text is stored for this source.</p>"""
      // normalised here as well as at import: the corpus was seeded before the figure-link rewrite
      case FullText.Shown(text) =>
        s"""<hr><div class="reading src-body">${rendered(
            AssetLinks
              .normalizeBody(AssetLinks.NormalizeBodyMd(text.value), AssetLinks.NormalizeBodyTitle(r.title.value))
              .value
          )}</div>"""
    HtmlRender.Html(
      s"""<h1>${esc(r.title.value)}</h1>""" +
        s"""<p class="src-byline">$byline</p>""" +
        s"""<p class="src-ident"><span class="lbl">$ident</span> ${HtmlRender.identifier(r).value}</p>""" +
        facets.mkString +
        formatNotice(p.formatting, "These notes", None).value +
        prose(p.summary.map(_.value)).fold("")(h => s"""<div class="src-summary reading">$h</div>""") +
        prose(p.notes.map(_.value)).fold("")(h => s"""<h2>Notes</h2><div class="reading">$h</div>""") +
        findings +
        citations(p.cites, p.citedBy).value +
        ArticleHtml
          .toggle(
            domain.library.Citekey.assume(r.citekey.value),
            p.fullText != FullText.Hidden,
            p.query.map(q => HtmlRender.Query(q.value))
          )
          .value +
        body
    )

  /** "Cites" and "Cited by", each omitted when empty — and nothing at all when both are. */
  def citations(cites: List[Citation], citedBy: List[Citation]): HtmlRender.Html =
    def block(anchor: Anchor, title: String, cs: List[Citation]) =
      if cs.isEmpty then ""
      else
        s"""<h2 id="$anchor">$title (${cs.size})</h2><ul class="citations">${cs
            .map(c => citation(c).value)
            .mkString}</ul>"""
    HtmlRender.Html(
      block(Anchor("cites"), "Cites", cites) +
        block(Anchor("cited-by"), "Cited by", citedBy)
    )

  private def kindLabel(kind: Edge.Kind.Wire): String =
    s"""<span class="edge-kind">${esc(display(kind.value))}</span> """

  def citation(c: Citation): HtmlRender.Html = c match
    case Citation.ToSource(r, kind) =>
      HtmlRender.Html(
        s"""<li>${kindLabel(kind)}${sourceRef(r, readable(r))} <i>${esc(r.title.value)}</i> """ +
          s"""${HtmlRender.statusBadge(r.status).value} <span class="ident">${HtmlRender
              .identifier(r)
              .value}</span></li>"""
      )
    case Citation.ToArticle(a, kind) =>
      HtmlRender.Html(
        s"""<li>${kindLabel(kind.getOrElse(Edge.Kind.Wire("article")))}${HtmlRender.articleRef(a).value}</li>"""
      )
    case Citation.Unresolved(id, kind) =>
      HtmlRender.Html(
        s"""<li>${kindLabel(kind)}${dangling(id.value)} <span class="muted">not in the corpus yet</span></li>"""
      )

  // ── article page ─────────────────────────────────────────────────────────────────────────────

  def article(p: ArticlePage, links: WikiLinks.Occurrence.Target => Option[LinkTarget]): HtmlRender.Html =
    val a = p.article
    val body0 = a.body.value.trim
    val heading = Articles.heading(Article.Body(body0))
    // the `# Title` line is rendered here, so the meta line can sit right under it
    val body1 = if heading.isEmpty then body0 else body0.linesIterator.dropWhile(_.trim.isEmpty).drop(1).mkString("\n")
    // item 23: a trailing hand-written "## Sources" list duplicates the generated footer
    val body = if p.cited.isEmpty then body1 else ArticleMarkdown.stripSourcesSection(Article.Body(body1)).value
    val (argument, notUsed) = NotUsed.findFirstMatchIn(body) match
      case Some(m) => (body.take(m.start), body.drop(m.start))
      case None    => (body, "")
    val numbers = numbering(FullText.Shown.Markdown(argument), links)
    // the generated sections (#958) follow the body; like a hand-written Not used, their links get no number
    val generated = p.coverage.fold("")(c => "\n\n" + Article.Coverage.sections(c).value)
    val prose = MarkdownHtml
      .render(
        FullText.Shown.Markdown(
          linkProse(FullText.Shown.Markdown(argument), links, numbers.get).value +
            linkProse(FullText.Shown.Markdown(notUsed + generated), links).value
        )
      )
      .value
    val meta = List(
      esc(display(a.kind.value.trim)),
      chips(ChipField.Place, a.regions.map(_.value)),
      esc(YearFmt.period(a.periodStart, a.periodEnd)),
      chips(ChipField.Tag, a.tags.map(_.value))
    ).filter(_.nonEmpty)
    HtmlRender.Html(
      s"""<h1>${esc(heading.map(_.value).getOrElse(a.title.value))}</h1>""" +
        (if meta.isEmpty then "" else s"""<p class="article-meta-line">${meta.mkString(" · ")}</p>""") +
        p.review.fold("")(r => reviewLine(r).value) +
        formatNotice(p.formatting, "This article", Some(a.slug)).value +
        s"""<div class="reading article-body">$prose</div>""" +
        backlinks(p.backlinks).value +
        sourcesCited(citedEntries(p.cited, numbers, links)).value +
        p.review.fold("")(r => reviewHistory(r).value)
    )

  // ── review ───────────────────────────────────────────────────────────────────────────────────

  /** An article's review state as a badge, its meaning on hover. */
  def reviewBadge(b: ReviewBadge): HtmlRender.Html =
    HtmlRender.Html(
      s"""<span class="job-state review-state state-${b.state.wire}" title="${esc(b.detail.value)}">${esc(
          Wording.capitalize(b.label.value)
        )}</span>"""
    )

  /** A research goal's stage as the goals list draws it, linked to the goal's page (in the web UI a plain click opens
    * its window).
    */
  def goalBadgeLink(g: ReviewGoal): HtmlRender.Html =
    val status = g.status.value
    val meaning =
      Goal.Stages.meaning(Goal.Stage(status)).fold("")(m => s""" title="${esc(s"$status: ${m.value}")}"""")
    HtmlRender.Html(
      s"""<a class="goal-ref" href="/research/goals/${esc(Atoms.segment(g.slug.value))}" title="${esc(
          g.slug.value
        )}">""" +
        s"""<span class="job-state goal-status state-${esc(status)}"$meaning>${esc(
            Wording.capitalize(status)
          )}</span> """ +
        s"""${esc(if g.title.value.trim.nonEmpty then g.title.value else g.slug.value)}</a>"""
    )

  /** `2026-10-20T09:00:00Z` as `2026-10-20 09:00 UTC`; anything else as it is. */
  def readableUtc(t: String): String = t match
    case s"${day}T${hh}:${mm}:${_}Z" => s"$day $hh:$mm UTC"
    case other                       => other

  /** Under the title: the review badge, and the research goals that link the article. */
  def reviewLine(r: Article.ReviewView): HtmlRender.Html =
    val goals = if r.goals.isEmpty then ""
    else
      s""" <span class="muted">· ${if r.goals.size == 1 then "goal" else "goals"}:</span> """ +
        r.goals.map(g => goalBadgeLink(g).value).mkString(""" <span class="muted">·</span> """)
    HtmlRender.Html(
      s"""<p class="article-review-line"><span class="muted">review:</span> ${reviewBadge(r.badge).value}$goals</p>"""
    )

  /** Every finding the review panel raised, run by run and round by round, with what became of it; collapsed, its
    * summary saying how many there were and how many are open.
    */
  def reviewHistory(r: Article.ReviewView): HtmlRender.Html =
    if r.rounds.isEmpty then HtmlRender.Html("")
    else
      val all = r.rounds.flatMap(_.findings)
      val runs = r.rounds.map(_.run).distinct.size
      val open = all.count(_.outcome.value == "open")
      val summary =
        s"Review history: ${Plural.count(Plural.CountN(all.size.toLong), Plural.CountSingular("finding"))} over ${Plural.count(Plural.CountN(runs.toLong), Plural.CountSingular("run"))}" +
          (if open > 0 then s", $open still open" else ", none open")
      val rounds = r.rounds.map { rd =>
        val when =
          rd.startedAt.fold("")(t => s""" <span class="muted">· run started ${esc(readableUtc(t.value))}</span>""")
        val items = rd.findings.map { f =>
          val facts =
            List(Article.ReviewView.reviewerName(f.reviewer), f.kind.value, f.where.value)
              .map(_.trim)
              .filter(_.nonEmpty)
              .map(esc)
          s"""<li class="review-finding outcome-${esc(f.outcome.value)}"><span class="review-sev sev-${esc(
              f.severity.value
            )}">${esc(f.severity.value)}</span> """ +
            s"""<b>${esc(f.id.value)}</b> <span class="muted">${facts.mkString(" · ")}</span> — ${esc(
                f.problem.value
              )}""" +
            s"""<div class="review-outcome">${esc(Article.ReviewView.outcomeLine(f))}</div></li>"""
        }
        s"""<h3>Run ${rd.runNumber}, round ${rd.round}$when</h3><ul class="review-findings">${items.mkString}</ul>"""
      }
      HtmlRender.Html(
        s"""<details class="review-history"><summary>${esc(summary)}</summary>${rounds.mkString}</details>"""
      )

  /** The footer's entries: every numbered source (in number order), then the article's other cited sources — including
    * a stored citekey that is not in the corpus, shown as such.
    */
  def citedEntries(
      cited: List[CitedSource],
      numbers: Map[Citekey, Int],
      links: WikiLinks.Occurrence.Target => Option[LinkTarget]
  ): List[CitedEntry] =
    val byCk = cited.map(c => c.citekey -> c).toMap
    val numbered = numbers.toList.sortBy(_._2).flatMap { (ck, n) =>
      byCk
        .get(ck)
        .map(c =>
          CitedEntry(
            Some(CitedEntry.Number(n)),
            CitedEntry.Citekey.assume(c.citekey.value),
            c.doi.map(d => CitedEntry.Doi.assume(d.value)),
            c.authors.map(a => CitedEntry.Authors(a.value)),
            c.year.map(y => CitedEntry.Year(y.value.toInt)),
            c.title.map(t => CitedEntry.Title(t.value)),
            c.status.transformInto[Option[domain.library.Source.Status]]
          )
        )
        .orElse(links(WikiLinks.Occurrence.Target(ck.value)).collect { case r: Source.Ref =>
          CitedEntry(
            Some(CitedEntry.Number(n)),
            CitedEntry.Citekey.assume(ck.value),
            r.doi.map(d => CitedEntry.Doi.assume(d.value)),
            r.authors.map(a => CitedEntry.Authors(a.value)),
            r.year.map(y => CitedEntry.Year(y.value.toInt)),
            Some(CitedEntry.Title(r.title.value)),
            Some(r.status.transformInto[domain.library.Source.Status])
          )
        })
    }
    val rest = cited
      .filterNot(c => numbers.contains(c.citekey))
      .sortBy(_.citekey.value)
      .map(c =>
        CitedEntry(
          None,
          CitedEntry.Citekey.assume(c.citekey.value),
          c.doi.map(d => CitedEntry.Doi.assume(d.value)),
          c.authors.map(a => CitedEntry.Authors(a.value)),
          c.year.map(y => CitedEntry.Year(y.value.toInt)),
          c.title.map(t => CitedEntry.Title(t.value)),
          c.status.transformInto[Option[domain.library.Source.Status]]
        )
      )
    numbered ++ rest

  def sourcesCited(entries: List[CitedEntry]): HtmlRender.Html =
    if entries.isEmpty then HtmlRender.Html("")
    else
      val items = entries.map { e =>
        val id = e.number.fold("")(n => s""" id="cite-$n"""")
        e.title match
          case None    => s"""<li$id>${dangling(e.citekey.value)} <span class="muted">not in the corpus</span></li>"""
          case Some(t) =>
            val byline =
              List(
                Source.Ref.shortAuthors(e.authors.map(a => com.histmersion.api.library.AuthorName(a.value))).value,
                e.year.fold("")(_.toString)
              ).filter(_.nonEmpty).mkString(" ")
            val status =
              e.status.fold("")(s => " " + HtmlRender.statusBadge(s.transformInto[Source.Status]).value)
            val ident = HtmlRender.identifier(e.citekey, e.doi).value
            s"""<li$id>${HtmlRender
                .sourceLink(e.citekey, e.doi, HtmlRender.Label(if byline.nonEmpty then byline else t.value))
                .value} <i>${esc(
                t.value
              )}</i>$status""" +
              s""" <span class="ident">$ident</span></li>"""
      }
      HtmlRender.Html(
        s"""<h2 id="sources-cited">Sources cited (${entries.size})</h2><ol class="sources-cited">${items.mkString}</ol>"""
      )

  def backlinks(rows: List[Backlink]): HtmlRender.Html =
    if rows.isEmpty then HtmlRender.Html("")
    else
      val items = rows.map { b =>
        val label = b.title.map(_.value).filter(_.trim.nonEmpty).getOrElse(display(b.id.value))
        val link =
          if b.kind.value == Article.Links.TargetSource then Atoms.sourceLink(Citekey(b.id.value), None, label)
          else Atoms.articleLink(ReviewGoal.Slug(b.id.value), label)
        s"<li>${link.markup}</li>"
      }
      HtmlRender.Html(s"""<h2>Backlinks (${rows.size})</h2><ul class="backlinks">${items.mkString}</ul>""")
