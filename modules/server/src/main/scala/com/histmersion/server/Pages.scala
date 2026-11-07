package com.histmersion.server

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{HtmlAtoms, Plural, Wording, YearFmt}
import com.histmersion.config.*
import com.histmersion.domain.*
// explicit: pins `Article` to the page helpers in HttpApi.scala over `core.Article` (see ArticlesApi)
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatMarkdown, FormatSeverity}
import com.histmersion.api.library.Label
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.research.{ArticleMarkdown, Articles, Backlink, CitedSource, ReviewBadge, ReviewGoal}
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** PURE rendering of the typed page models to HTML. Wikilinks resolve through `links` (one lookup for the whole page,
  * done by [[PageBuilder]]) into the [[HtmlRender]] anchors.
  */
object Pages:
  type Anchor = Anchor.T
  object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeText = TimeText.T
  object TimeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LinkProseNumberResult = LinkProseNumberResult.T
  object LinkProseNumberResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CiteNumberN = CiteNumberN.T
  object CiteNumberN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumberingResultPart2 = NumberingResultPart2.T
  object NumberingResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormatNoticeWhat = FormatNoticeWhat.T
  object FormatNoticeWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedEntriesNumbersPart2 = CitedEntriesNumbersPart2.T
  object CitedEntriesNumbersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import HtmlRender.*

  // ── prose ────────────────────────────────────────────────────────────────────────────────────

  /** Markdown with every `[[wikilink]]` replaced by its typed anchor (raw inline HTML, which flexmark passes through).
    * `number` appends the citation number to a source reference.
    */
  def linkProse(md: com.histmersion.server.FullText.Shown.Markdown, links: com.histmersion.domain.library.Citekey => Option[LinkTarget], number: com.histmersion.domain.library.Citekey => Option[com.histmersion.server.Pages.LinkProseNumberResult] = _ => None): com.histmersion.server.HtmlRender.Html =
    Article.Links.rewriteLinks(md) { (t, explicit, written) =>
      val own = explicit.filter(_.nonEmpty)
      links(t) match
        case Some(r: Source.Ref) =>
          sourceRef(r, own.getOrElse(readable(r))) + number(r.citekey).fold("")(citeNumber)
        case Some(a: Article.Ref) => articleRef(a, own.getOrElse(a.title))
        case None                 => dangling(own.getOrElse(Label.display(written)))
    }

  private def citeNumber(n: com.histmersion.server.Pages.CiteNumberN): com.histmersion.server.HtmlRender.Html =
    s"""<sup class="cite-num"><a class="cite-jump" href="#cite-$n">[$n]</a></sup>"""

  /** A heading that starts the part of an article whose sources are NOT cited in its argument — the wiki's
    * `## Not used` convention (`**[[ck]]**: why`). Links there are rendered like any other reference but get no
    * citation number.
    */
  private val NotUsed = """(?im)^#{1,6}\s*not used\b.*$""".r

  /** Citation numbers by first appearance in `md` (the argument part of an article). */
  def numbering(md: com.histmersion.server.FullText.Shown.Markdown, links: com.histmersion.domain.library.Citekey => Option[LinkTarget]): Map[com.histmersion.domain.library.Citekey, com.histmersion.server.Pages.NumberingResultPart2] =
    Article.Links
      .targets(md)
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
  def formatNotice(issues: List[FormatIssue], what: com.histmersion.server.Pages.FormatNoticeWhat, slug: Option[com.histmersion.domain.research.Slug]): com.histmersion.server.HtmlRender.Html =
    if issues.isEmpty then ""
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val level = if errs > 0 then "has-errors" else "has-warnings"
      val items = issues.map { i =>
        val sev = if i.severity == FormatSeverity.Error then "error" else "warning"
        val field = if i.field == FormatField.Body then "" else s"${esc(i.field.toString.toLowerCase)}, "
        val ex = if i.excerpt.isEmpty then "" else s""" <code class="fi-excerpt">${esc(i.excerpt)}</code>"""
        s"""<li class="fi fi-$sev"><span class="fi-where">${field}line ${i.line}</span> """ +
          s"""<span class="fi-rule">${esc(Label.display(i.rule.wire.replace('-', '_')))}</span> — ${esc(
              i.message
            )}$ex</li>"""
      }
      val fixable = issues.count(_.fix.nonEmpty)
      val how =
        slug.fold(s"histmersion lint --source &lt;citekey&gt; --fix")(s => s"histmersion article lint ${esc(s)} --fix")
      val tip =
        if fixable == 0 then ""
        else
          val which = if fixable == issues.size then "all of them" else s"$fixable of them"
          s"""<p class="fi-tip"><code>$how</code> repairs $which without changing a word.</p>"""
      s"""<details class="format-notice $level"><summary>$what may not display correctly: """ +
        s"""${FormatMarkdown.problems(
            issues.size
          )}</summary><ul class="format-issues">${items.mkString}</ul>$tip</details>"""

  // ── source page ──────────────────────────────────────────────────────────────────────────────

  def source(p: SourcePage, links: com.histmersion.domain.library.Citekey => Option[LinkTarget]): com.histmersion.server.HtmlRender.Html =
    val r = p.ref
    val period = YearFmt.period(p.periodStart, p.periodEnd)
    val byline = List(
      chips(ChipField.Author, r.authors),
      r.year.fold("n.d.")(_.toString),
      p.venue.filter(_.trim.nonEmpty).fold("")(esc),
      statusBadge(r.status.transformInto[com.histmersion.api.library.Source.Status]),
      esc(period)
    ).filter(_.nonEmpty).mkString(" · ")
    val ident = r.doi.filter(_.nonEmpty).fold("Citekey")(_ => "DOI")
    val facets = List(
      "Topics" -> chips(ChipField.Topic, p.topics),
      "Regions" -> chips(ChipField.Place, p.regions),
      "Tags" -> chips(ChipField.Tag, p.tags)
    ).collect { case (l, c) if c.nonEmpty => s"""<p class="src-chips"><span class="lbl">$l</span> $c</p>""" }
    // the figure pipeline of plan §1.8 (legacy `figures/…` links, numbering, served paths, lazy
    // images) runs over every Markdown part of a source, as it did when the page was one document
    def html(md: com.histmersion.server.FullText.Shown.Markdown): com.histmersion.server.HtmlRender.Html =
      val linked = AssetLinks.numberFigures(AssetLinks.fromFigures(linkProse(md, links)))
      AssetLinks.lazyImages(MarkdownHtml.render(AssetLinks.toWeb(linked, r.citekey)))
    val prose = (md: Option[String]) => md.map(_.trim).filter(_.nonEmpty).map(html)
    val findings =
      if p.findings.isEmpty then ""
      else
        s"""<h2 id="findings">Findings (${p.findings.size})</h2><ul class="findings">${p.findings
            .map(finding)
            .mkString}</ul>"""
    val body = p.fullText match
      case FullText.Hidden  => ""
      case FullText.Missing => """<hr><p class="muted">No full text is stored for this source.</p>"""
      // normalised here as well as at import: the corpus was seeded before the figure-link rewrite
      case FullText.Shown(text) =>
        s"""<hr><div class="reading src-body">${html(AssetLinks.normalizeBody(text, r.title.value))}</div>"""
    s"""<h1>${esc(r.title)}</h1>""" +
      s"""<p class="src-byline">$byline</p>""" +
      s"""<p class="src-ident"><span class="lbl">$ident</span> ${identifier(r)}</p>""" +
      facets.mkString +
      formatNotice(p.formatting, "These notes", None) +
      prose(p.summary).fold("")(h => s"""<div class="src-summary reading">$h</div>""") +
      prose(p.notes).fold("")(h => s"""<h2>Notes</h2><div class="reading">$h</div>""") +
      findings +
      citations(p.cites, p.citedBy) +
      ArticleHtml.toggle(r.citekey, p.fullText != FullText.Hidden, p.query) +
      body

  /** "Cites" and "Cited by", each omitted when empty — and nothing at all when both are. */
  def citations(cites: List[Citation], citedBy: List[Citation]): com.histmersion.server.HtmlRender.Html =
    def block(anchor: com.histmersion.server.Pages.Anchor, title: com.histmersion.domain.Title, cs: List[Citation]) =
      if cs.isEmpty then ""
      else s"""<h2 id="$anchor">$title (${cs.size})</h2><ul class="citations">${cs.map(citation).mkString}</ul>"""
    block("cites", "Cites", cites) + block("cited-by", "Cited by", citedBy)

  private def kindLabel(kind: com.histmersion.domain.library.Edge.Kind.Wire): com.histmersion.server.HtmlRender.Html = s"""<span class="edge-kind">${esc(Label.display(kind))}</span> """

  def citation(c: Citation): com.histmersion.server.HtmlRender.Html = c match
    case Citation.ToSource(r, kind) =>
      s"""<li>${kindLabel(kind)}${sourceRef(r, readable(r))} <i>${esc(r.title)}</i> """ +
        s"""${statusBadge(
            r.status.transformInto[com.histmersion.api.library.Source.Status]
          )} <span class="ident">${identifier(r)}</span></li>"""
    case Citation.ToArticle(a, kind) =>
      s"""<li>${kindLabel(kind.getOrElse("article"))}${articleRef(a)}</li>"""
    case Citation.Unresolved(id, kind) =>
      s"""<li>${kindLabel(kind)}${dangling(id)} <span class="muted">not in the corpus yet</span></li>"""

  // ── article page ─────────────────────────────────────────────────────────────────────────────

  def article(p: ArticlePage, links: com.histmersion.domain.research.Slug => Option[LinkTarget]): com.histmersion.server.HtmlRender.Html =
    val a = p.article
    val body0 = a.body.trim
    val heading = Articles.heading(body0)
    // the `# Title` line is rendered here, so the meta line can sit right under it
    val body1 = if heading.isEmpty then body0 else body0.linesIterator.dropWhile(_.trim.isEmpty).drop(1).mkString("\n")
    // item 23: a trailing hand-written "## Sources" list duplicates the generated footer
    val body = if p.cited.isEmpty then body1 else ArticleMarkdown.stripSourcesSection(body1)
    val (argument, notUsed) = NotUsed.findFirstMatchIn(body) match
      case Some(m) => (body.take(m.start), body.drop(m.start))
      case None    => (body, "")
    val numbers = numbering(argument, links)
    // the generated sections (#958) follow the body; like a hand-written Not used, their links get no number
    val generated = p.coverage.fold("")(c => "\n\n" + Article.Coverage.sections(c))
    val prose = MarkdownHtml.render(linkProse(argument, links, numbers.get) + linkProse(notUsed + generated, links))
    val meta = List(
      esc(Label.display(a.kind.trim)),
      chips(ChipField.Place, a.regions),
      esc(YearFmt.period(a.periodStart, a.periodEnd)),
      chips(ChipField.Tag, a.tags)
    ).filter(_.nonEmpty)
    s"""<h1>${esc(heading.getOrElse(a.title))}</h1>""" +
      (if meta.isEmpty then "" else s"""<p class="article-meta-line">${meta.mkString(" · ")}</p>""") +
      p.review.fold("")(reviewLine) +
      formatNotice(p.formatting, "This article", Some(a.slug)) +
      s"""<div class="reading article-body">$prose</div>""" +
      backlinks(p.backlinks) +
      sourcesCited(citedEntries(p.cited, numbers, links)) +
      p.review.fold("")(reviewHistory)

  // ── review ───────────────────────────────────────────────────────────────────────────────────

  /** An article's review state as a badge, its meaning on hover. */
  def reviewBadge(b: ReviewBadge): com.histmersion.server.HtmlRender.Html =
    s"""<span class="job-state review-state state-${b.state.wire}" title="${esc(b.detail)}">${esc(
        Wording.capitalize(b.label)
      )}</span>"""

  /** A research goal's stage as the goals list draws it, linked to the goal's page (in the web UI a plain click opens
    * its window).
    */
  def goalBadgeLink(g: ReviewGoal): com.histmersion.server.HtmlRender.Html =
    val meaning = Goal.Stages.meaning(g.status).fold("")(m => s""" title="${esc(s"${g.status}: $m")}"""")
    s"""<a class="goal-ref" href="/research/goals/${esc(HtmlAtoms.segment(g.slug))}" title="${esc(g.slug)}">""" +
      s"""<span class="job-state goal-status state-${esc(g.status)}"$meaning>${esc(
          Wording.capitalize(g.status)
        )}</span> """ +
      s"""${esc(if g.title.trim.nonEmpty then g.title else g.slug)}</a>"""

  /** `2026-10-20T09:00:00Z` as `2026-10-20 09:00 UTC`; anything else as it is. */
  def readableUtc(t: com.histmersion.server.Pages.TimeText): com.histmersion.server.HtmlRender.Text = t match
    case s"${day}T${hh}:${mm}:${_}Z" => s"$day $hh:$mm UTC"
    case other                       => other

  /** Under the title: the review badge, and the research goals that link the article. */
  def reviewLine(r: Article.ReviewView): com.histmersion.server.HtmlRender.Html =
    val goals = if r.goals.isEmpty then ""
    else
      s""" <span class="muted">· ${if r.goals.size == 1 then "goal" else "goals"}:</span> """ +
        r.goals.map(goalBadgeLink).mkString(""" <span class="muted">·</span> """)
    s"""<p class="article-review-line"><span class="muted">review:</span> ${reviewBadge(r.badge)}$goals</p>"""

  /** Every finding the review panel raised, run by run and round by round, with what became of it; collapsed, its
    * summary saying how many there were and how many are open.
    */
  def reviewHistory(r: Article.ReviewView): com.histmersion.server.HtmlRender.Html =
    if r.rounds.isEmpty then ""
    else
      val all = r.rounds.flatMap(_.findings)
      val runs = r.rounds.map(_.run).distinct.size
      val open = all.count(_.outcome == "open")
      val summary =
        s"Review history: ${Plural.count(all.size.toLong, "finding")} over ${Plural.count(runs.toLong, "run")}" +
          (if open > 0 then s", $open still open" else ", none open")
      val rounds = r.rounds.map { rd =>
        val when = rd.startedAt.fold("")(t => s""" <span class="muted">· run started ${esc(readableUtc(t))}</span>""")
        val items = rd.findings.map { f =>
          val facts =
            List(Article.ReviewView.reviewerName(f.reviewer), f.kind, f.where).map(_.trim).filter(_.nonEmpty).map(esc)
          s"""<li class="review-finding outcome-${esc(f.outcome)}"><span class="review-sev sev-${esc(
              f.severity
            )}">${esc(f.severity)}</span> """ +
            s"""<b>${esc(f.id)}</b> <span class="muted">${facts.mkString(" · ")}</span> — ${esc(f.problem)}""" +
            s"""<div class="review-outcome">${esc(Article.ReviewView.outcomeLine(f))}</div></li>"""
        }
        s"""<h3>Run ${rd.runNumber}, round ${rd.round}$when</h3><ul class="review-findings">${items.mkString}</ul>"""
      }
      s"""<details class="review-history"><summary>${esc(summary)}</summary>${rounds.mkString}</details>"""

  /** The footer's entries: every numbered source (in number order), then the article's other cited sources — including
    * a stored citekey that is not in the corpus, shown as such.
    */
  def citedEntries(
      cited: List[CitedSource],
      numbers: Map[com.histmersion.domain.library.Citekey, com.histmersion.server.Pages.CitedEntriesNumbersPart2],
      links: com.histmersion.domain.library.Citekey => Option[LinkTarget]
  ): List[CitedEntry] =
    val byCk = cited.map(c => c.citekey -> c).toMap
    val numbered = numbers.toList.sortBy(_._2).flatMap { (ck, n) =>
      byCk
        .get(ck)
        .map(c =>
          CitedEntry(
            Some(n),
            ck,
            c.doi,
            c.authors,
            c.year,
            c.title,
            c.status.transformInto[Option[com.histmersion.domain.library.Source.Status]]
          )
        )
        .orElse(links(ck).collect { case r: Source.Ref =>
          CitedEntry(
            Some(n),
            ck,
            r.doi,
            r.authors,
            r.year,
            Some(r.title),
            Some(r.status.transformInto[com.histmersion.domain.library.Source.Status])
          )
        })
    }
    val rest = cited
      .filterNot(c => numbers.contains(c.citekey))
      .sortBy(_.citekey)
      .map(c =>
        CitedEntry(
          None,
          c.citekey,
          c.doi,
          c.authors,
          c.year,
          c.title,
          c.status.transformInto[Option[com.histmersion.domain.library.Source.Status]]
        )
      )
    numbered ++ rest

  def sourcesCited(entries: List[CitedEntry]): com.histmersion.server.HtmlRender.Html =
    if entries.isEmpty then ""
    else
      val items = entries.map { e =>
        val id = e.number.fold("")(n => s""" id="cite-$n"""")
        e.title match
          case None    => s"""<li$id>${dangling(e.citekey)} <span class="muted">not in the corpus</span></li>"""
          case Some(t) =>
            val byline =
              List(Source.Ref.shortAuthors(e.authors), e.year.fold("")(_.toString)).filter(_.nonEmpty).mkString(" ")
            val status = e.status.fold("")(s => " " + statusBadge(s.transformInto[com.histmersion.api.library.Source.Status]))
            val ident = identifier(e.citekey, e.doi)
            s"""<li$id>${sourceLink(e.citekey, e.doi, if byline.nonEmpty then byline else t)} <i>${esc(
                t
              )}</i>$status""" +
              s""" <span class="ident">$ident</span></li>"""
      }
      s"""<h2 id="sources-cited">Sources cited (${entries.size})</h2><ol class="sources-cited">${items.mkString}</ol>"""

  def backlinks(rows: List[Backlink]): com.histmersion.server.HtmlRender.Html =
    if rows.isEmpty then ""
    else
      val items = rows.map { b =>
        val label = b.title.filter(_.trim.nonEmpty).getOrElse(Label.display(b.id))
        val link =
          if b.kind == Article.Links.TargetSource then sourceLink(b.id, None, label)
          else articleRef(Article.Ref(b.id, label), label)
        s"<li>$link</li>"
      }
      s"""<h2>Backlinks (${rows.size})</h2><ul class="backlinks">${items.mkString}</ul>"""
