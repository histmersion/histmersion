package com.histmersion
package server

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{Html, Plural, Wording, YearFmt}
import com.histmersion.api.Html.{html, Atoms}
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
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@movesTo(FeaturePlace.ResearchArticles)
object Pages:
  type Anchor = Anchor.T
  object Anchor extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def display(s: String): String = Label.display(Label.Text(s)).value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sourceRef(r: Source.Ref, label: String): Html = HtmlRender.sourceRef(r, HtmlRender.Label(label))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def readable(r: Source.Ref): String = HtmlRender.readable(r).value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def articleRef(a: Article.Ref, label: String): Html = HtmlRender.articleRef(a, HtmlRender.Label(label))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def dangling(label: String): Html = HtmlRender.dangling(HtmlRender.Label(label))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def chips(field: ChipField, values: List[String]): Html =
    HtmlRender.chips(field, values.map(HtmlRender.Label(_)))

  private val dot = Html.text(" · ")

  // ── prose ────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def linkProse(
      md: FullText.Shown.Markdown,
      links: WikiLinks.Occurrence.Target => Option[LinkTarget],
      number: Citekey => Option[Int] = _ => None
  ): FullText.Shown.Markdown =
    FullText.Shown.Markdown(
      Article.Links
        .rewriteLinks(WikiLinks.Body(md.value)) { (t, explicit, written) =>
          val own = explicit.map(_.value).filter(_.nonEmpty)
          val anchor = links(t) match
            case Some(r: Source.Ref) =>
              sourceRef(r, own.getOrElse(readable(r))) ++ number(r.citekey).fold(Html.empty)(citeNumber)
            case Some(a: Article.Ref) => articleRef(a, own.getOrElse(a.title.value))
            case None                 => dangling(own.getOrElse(display(written.value)))
          anchor.markup
        }
        .value
    )

  private def citeNumber(n: Int): Html =
    html"""<sup class="cite-num"><a class="cite-jump" href="#cite-$n">[$n]</a></sup>"""

  /** A heading that starts the part of an article whose sources are NOT cited in its argument — the wiki's
    * `## Not used` convention (`**[[ck]]**: why`). Links there are rendered like any other reference but get no
    * citation number.
    */
  private val NotUsed = """(?im)^#{1,6}\s*not used\b.*$""".r

  /** Citation numbers by first appearance in `md` (the argument part of an article). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def formatNotice(issues: List[FormatIssue], what: String, slug: Option[Article.Slug]): Html =
    if issues.isEmpty then Html.empty
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val level = if errs > 0 then "has-errors" else "has-warnings"
      val items = issues.map { i =>
        val sev = if i.severity == FormatSeverity.Error then "error" else "warning"
        val field = if i.field == FormatField.Body then Html.empty else html"${i.field.toString.toLowerCase}, "
        val ex =
          if i.excerpt.value.isEmpty then Html.empty
          else html""" <code class="fi-excerpt">${i.excerpt.value}</code>"""
        val rule = display(i.rule.wire.value.replace('-', '_'))
        html"""<li class="fi fi-$sev"><span class="fi-where">${field}line ${i.line}</span> """ ++
          html"""<span class="fi-rule">$rule</span> — ${i.message.value}$ex</li>"""
      }
      val fixable = issues.count(_.fix.nonEmpty)
      val how =
        slug.fold(html"histmersion lint --source &lt;citekey&gt; --fix")(s =>
          html"histmersion article lint ${s.value} --fix"
        )
      val tip =
        if fixable == 0 then Html.empty
        else
          val which = if fixable == issues.size then "all of them" else s"$fixable of them"
          html"""<p class="fi-tip"><code>$how</code> repairs $which without changing a word.</p>"""
      val problems = FormatMarkdown.problems(FormatReport.IssueCount(issues.size))
      html"""<details class="format-notice $level"><summary>$what may not display correctly: """ ++
        html"""$problems</summary><ul class="format-issues">${Html.concat(items)}</ul>$tip</details>"""

  // ── source page ──────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def source(p: SourcePage, links: WikiLinks.Occurrence.Target => Option[LinkTarget]): Html =
    val r = p.ref
    val period = YearFmt.period(
      p.periodStart.map(y => Year(y.value)),
      p.periodEnd.map(y => Year(y.value))
    )
    val byline = Html.join(
      List(
        chips(ChipField.Author, r.authors.map(_.value)),
        Html.text(r.year.fold("n.d.")(_.toString)),
        p.venue.filter(_.value.trim.nonEmpty).fold(Html.empty)(v => Html.text(v.value)),
        HtmlRender.statusBadge(r.status),
        Html.text(period)
      ).filter(_.nonEmpty),
      dot
    )
    val ident = r.doi.filter(_.value.nonEmpty).fold("Citekey")(_ => "DOI")
    val facets = List(
      "Topics" -> chips(ChipField.Topic, p.topics.map(_.value)),
      "Regions" -> chips(ChipField.Place, p.regions.map(_.value)),
      "Tags" -> chips(ChipField.Tag, p.tags.map(_.value))
    ).collect { case (l, c) if c.nonEmpty => html"""<p class="src-chips"><span class="lbl">$l</span> $c</p>""" }
    // the figure pipeline of plan §1.8 (legacy `figures/…` links, numbering, served paths, lazy
    // images) runs over every Markdown part of a source, as it did when the page was one document
    def rendered(md: String): Html =
      val linked = AssetLinks.numberFigures(
        AssetLinks.fromFigures(AssetLinks.Body(linkProse(FullText.Shown.Markdown(md), links).value))
      )
      Html.lazyImages(MarkdownHtml.render(FullText.Shown.Markdown(AssetLinks.toWeb(linked, r.citekey).value)))
    val prose = (md: Option[String]) => md.map(_.trim).filter(_.nonEmpty).map(rendered)
    val findings =
      if p.findings.isEmpty then Html.empty
      else
        html"""<h2 id="findings">Findings (${p.findings.size})</h2>""" ++
          html"""<ul class="findings">${Html.concat(p.findings.map(HtmlRender.finding))}</ul>"""
    val body = p.fullText match
      case FullText.Hidden  => Html.empty
      case FullText.Missing => html"""<hr><p class="muted">No full text is stored for this source.</p>"""
      // normalised here as well as at import: the corpus was seeded before the figure-link rewrite
      case FullText.Shown(text) =>
        val normalised =
          AssetLinks.normalizeBody(AssetLinks.NormalizeBodyMd(text.value), AssetLinks.NormalizeBodyTitle(r.title.value))
        html"""<hr><div class="reading src-body">${rendered(normalised.value)}</div>"""
    Html.concat(
      List(
        html"""<h1>${r.title.value}</h1>""",
        html"""<p class="src-byline">$byline</p>""",
        html"""<p class="src-ident"><span class="lbl">$ident</span> ${HtmlRender.identifier(r)}</p>""",
        Html.concat(facets),
        formatNotice(p.formatting, "These notes", None),
        prose(p.summary.map(_.value)).fold(Html.empty)(h => html"""<div class="src-summary reading">$h</div>"""),
        prose(p.notes.map(_.value)).fold(Html.empty)(h => html"""<h2>Notes</h2><div class="reading">$h</div>"""),
        findings,
        citations(p.cites, p.citedBy),
        ArticleHtml.toggle(
          domain.library.Citekey.assume(r.citekey.value),
          p.fullText != FullText.Hidden,
          p.query.map(q => HtmlRender.Query(q.value))
        ),
        body
      )
    )

  /** "Cites" and "Cited by", each omitted when empty — and nothing at all when both are. */
  def citations(cites: List[Citation], citedBy: List[Citation]): Html =
    def block(anchor: Anchor, title: String, cs: List[Citation]) =
      if cs.isEmpty then Html.empty
      else
        html"""<h2 id="$anchor">$title (${cs.size})</h2>""" ++
          html"""<ul class="citations">${Html.concat(cs.map(citation))}</ul>"""
    block(Anchor("cites"), "Cites", cites) ++ block(Anchor("cited-by"), "Cited by", citedBy)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def kindLabel(kind: Edge.Kind.Wire): Html =
    html"""<span class="edge-kind">${display(kind.value)}</span> """

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citation(c: Citation): Html = c match
    case Citation.ToSource(r, kind) =>
      html"""<li>${kindLabel(kind)}${sourceRef(r, readable(r))} <i>${r.title.value}</i> """ ++
        html"""${HtmlRender.statusBadge(r.status)} <span class="ident">${HtmlRender.identifier(r)}</span></li>"""
    case Citation.ToArticle(a, kind) =>
      html"""<li>${kindLabel(kind.getOrElse(Edge.Kind.Wire("article")))}${HtmlRender.articleRef(a)}</li>"""
    case Citation.Unresolved(id, kind) =>
      html"""<li>${kindLabel(kind)}${dangling(id.value)} <span class="muted">not in the corpus yet</span></li>"""

  // ── article page ─────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def article(p: ArticlePage, links: WikiLinks.Occurrence.Target => Option[LinkTarget]): Html =
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
    val prose = MarkdownHtml.render(
      FullText.Shown.Markdown(
        linkProse(FullText.Shown.Markdown(argument), links, numbers.get).value +
          linkProse(FullText.Shown.Markdown(notUsed + generated), links).value
      )
    )
    val meta = List(
      Html.text(display(a.kind.value.trim)),
      chips(ChipField.Place, a.regions.map(_.value)),
      Html.text(YearFmt.period(a.periodStart, a.periodEnd)),
      chips(ChipField.Tag, a.tags.map(_.value))
    ).filter(_.nonEmpty)
    Html.concat(
      List(
        html"""<h1>${heading.map(_.value).getOrElse(a.title.value)}</h1>""",
        if meta.isEmpty then Html.empty else html"""<p class="article-meta-line">${Html.join(meta, dot)}</p>""",
        p.review.fold(Html.empty)(reviewLine),
        formatNotice(p.formatting, "This article", Some(a.slug)),
        html"""<div class="reading article-body">$prose</div>""",
        backlinks(p.backlinks),
        sourcesCited(citedEntries(p.cited, numbers, links)),
        p.review.fold(Html.empty)(reviewHistory)
      )
    )

  // ── review ───────────────────────────────────────────────────────────────────────────────────

  /** An article's review state as a badge, its meaning on hover. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def reviewBadge(b: ReviewBadge): Html =
    html"""<span class="job-state review-state state-${b.state.wire}" title="${b.detail.value}">""" ++
      html"""${Wording.capitalize(b.label.value)}</span>"""

  /** A research goal's stage as the goals list draws it, linked to the goal's page (in the web UI a plain click opens
    * its window).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def goalBadgeLink(g: ReviewGoal): Html =
    val status = g.status.value
    val meaning =
      Goal.Stages.meaning(Goal.Stage(status)).fold(Html.empty)(m => html""" title="${s"$status: ${m.value}"}"""")
    val shown = if g.title.value.trim.nonEmpty then g.title.value else g.slug.value
    html"""<a class="goal-ref" href="/research/goals/${Atoms.segment(g.slug.value)}" title="${g.slug.value}">""" ++
      html"""<span class="job-state goal-status state-$status"$meaning>${Wording.capitalize(status)}</span> """ ++
      html"""$shown</a>"""

  /** `2026-10-20T09:00:00Z` as `2026-10-20 09:00 UTC`; anything else as it is. */
  def readableUtc(t: String): String = t match
    case s"${day}T${hh}:${mm}:${_}Z" => s"$day $hh:$mm UTC"
    case other                       => other

  /** Under the title: the review badge, and the research goals that link the article. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def reviewLine(r: Article.ReviewView): Html =
    val goals =
      if r.goals.isEmpty then Html.empty
      else
        html""" <span class="muted">· ${if r.goals.size == 1 then "goal" else "goals"}:</span> """ ++
          Html.join(r.goals.map(goalBadgeLink), html""" <span class="muted">·</span> """)
    html"""<p class="article-review-line"><span class="muted">review:</span> ${reviewBadge(r.badge)}$goals</p>"""

  /** Every finding the review panel raised, run by run and round by round, with what became of it; collapsed, its
    * summary saying how many there were and how many are open.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G5TextBranch, "counts the review findings whose outcome is open: the same String vocabulary")
  def reviewHistory(r: Article.ReviewView): Html =
    if r.rounds.isEmpty then Html.empty
    else
      val all = r.rounds.flatMap(_.findings)
      val runs = r.rounds.map(_.run).distinct.size
      val open = all.count(_.outcome.value == "open")
      val summary =
        s"Review history: ${Plural.count(Plural.CountN(all.size.toLong), Plural.CountSingular("finding"))} over ${Plural.count(Plural.CountN(runs.toLong), Plural.CountSingular("run"))}" +
          (if open > 0 then s", $open still open" else ", none open")
      val rounds = r.rounds.map { rd =>
        val when =
          rd.startedAt.fold(Html.empty)(t =>
            html""" <span class="muted">· run started ${readableUtc(t.value)}</span>"""
          )
        val items = rd.findings.map { f =>
          val facts =
            List(Article.ReviewView.reviewerName(f.reviewer), f.kind.value, f.where.value)
              .map(_.trim)
              .filter(_.nonEmpty)
          html"""<li class="review-finding outcome-${f.outcome.value}">""" ++
            html"""<span class="review-sev sev-${f.severity.value}">${f.severity.value}</span> """ ++
            html"""<b>${f.id.value}</b> <span class="muted">${Html.join(facts.map(Html.text), dot)}</span> — """ ++
            html"""${f.problem.value}<div class="review-outcome">${Article.ReviewView.outcomeLine(f)}</div></li>"""
        }
        html"""<h3>Run ${rd.runNumber}, round ${rd.round}$when</h3>""" ++
          html"""<ul class="review-findings">${Html.concat(items)}</ul>"""
      }
      html"""<details class="review-history"><summary>$summary</summary>${Html.concat(rounds)}</details>"""

  /** The footer's entries: every numbered source (in number order), then the article's other cited sources — including
    * a stored citekey that is not in the corpus, shown as such.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def sourcesCited(entries: List[CitedEntry]): Html =
    if entries.isEmpty then Html.empty
    else
      val items = entries.map { e =>
        val id = e.number.fold(Html.empty)(n => html""" id="cite-$n"""")
        e.title match
          case None => html"""<li$id>${dangling(e.citekey.value)} <span class="muted">not in the corpus</span></li>"""
          case Some(t) =>
            val byline =
              List(
                Source.Ref.shortAuthors(e.authors.map(a => com.histmersion.api.library.AuthorName(a.value))).value,
                e.year.fold("")(_.toString)
              ).filter(_.nonEmpty).mkString(" ")
            val status =
              e.status.fold(Html.empty)(s => Html.text(" ") ++ HtmlRender.statusBadge(s.transformInto[Source.Status]))
            val link =
              HtmlRender.sourceLink(e.citekey, e.doi, HtmlRender.Label(if byline.nonEmpty then byline else t.value))
            html"""<li$id>$link <i>${t.value}</i>$status""" ++
              html""" <span class="ident">${HtmlRender.identifier(e.citekey, e.doi)}</span></li>"""
      }
      html"""<h2 id="sources-cited">Sources cited (${entries.size})</h2>""" ++
        html"""<ol class="sources-cited">${Html.concat(items)}</ol>"""

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def backlinks(rows: List[Backlink]): Html =
    if rows.isEmpty then Html.empty
    else
      val items = rows.map { b =>
        val label = b.title.map(_.value).filter(_.trim.nonEmpty).getOrElse(display(b.id.value))
        val link =
          if b.kind.value == Article.Links.TargetSource then Atoms.sourceLink(Citekey(b.id.value), None, label)
          else Atoms.articleLink(ReviewGoal.Slug(b.id.value), label)
        html"<li>$link</li>"
      }
      html"""<h2>Backlinks (${rows.size})</h2><ul class="backlinks">${Html.concat(items)}</ul>"""
