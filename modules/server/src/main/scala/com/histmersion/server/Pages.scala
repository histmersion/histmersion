package com.histmersion.server

import com.histmersion.core.*
// explicit: pins `Article` to the page helpers in HttpApi.scala over `core.Article` (see ArticlesApi)
import com.histmersion.server.Article
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.util.data.{DataHolder, MutableDataSet}

/** One entry of a source page's "Cites" / "Cited by" lists — edges AND article links, typed. */
enum Citation:
  /** A stored source, through a typed edge (`references`, `challenges`, …). */
  case ToSource(ref: SourceRef, kind: String)
  /** A compiled article: one citing this source in its prose (`kind = None`), or named by an edge. */
  case ToArticle(ref: ArticleRef, kind: Option[String])
  /** An edge to a work that is not in the corpus yet. */
  case Unresolved(id: String, kind: String)

/** Whether a source page shows the whole OCR'd paper. */
enum FullText:
  case Hidden
  case Missing
  case Shown(markdown: String)

/** Everything a `/source/{citekey}` page shows, typed. Built by [[PageBuilder]], rendered by
  * [[Pages.source]] — Markdown bodies stay Markdown until rendering, so the page model is testable
  * without flexmark in the way. */
final case class SourcePage(
    ref: SourceRef,
    venue: Option[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    topics: List[String],
    regions: List[String],
    tags: List[String],
    summary: Option[String],
    notes: Option[String],
    findings: List[FindingRow],
    cites: List[Citation],
    citedBy: List[Citation],
    fullText: FullText,
    query: Option[String] = None,
    /** The summary's and notes' formatting problems — the page says so above them. */
    formatting: List[FormatIssue] = Nil
)

/** One line of an article's "Sources cited" footer; `number` is its citation number in the prose
  * (first appearance), `None` for a source cited only outside it (e.g. under "Not used"). */
final case class CitedEntry(number: Option[Int], citekey: String, doi: Option[String], authors: List[String],
    year: Option[Int], title: Option[String], status: Option[SourceStatus])

/** Everything an `/article/{slug}` page shows, typed. */
final case class ArticlePage(
    article: ArticleRow,
    backlinks: List[Backlink],
    cited: List[CitedSource],
    formatting: List[FormatIssue] = Nil,
    /** The coverage of its recorded filter (#958): the generated Not used list and Method note. */
    coverage: Option[Coverage] = None
)

/** The ONE Markdown dialect the server reads: CommonMark plus GFM pipe tables, configured the way
  * GitHub and Obsidian read them (the delimiter row must match the header's column count; a short
  * row is padded, a long one cut). Both the pages ([[MarkdownHtml]]) and the formatting lint
  * ([[MarkdownLint]]) parse with this, so "the lint says it will not render" and "the page does not
  * render it" are the same fact. */
object MarkdownFlavor:
  val options: DataHolder =
    val o = new MutableDataSet()
    o.set(Parser.EXTENSIONS, java.util.List.of(TablesExtension.create()))
    o.set(TablesExtension.COLUMN_SPANS, java.lang.Boolean.FALSE)
    o.set(TablesExtension.APPEND_MISSING_COLUMNS, java.lang.Boolean.TRUE)
    o.set(TablesExtension.DISCARD_EXTRA_COLUMNS, java.lang.Boolean.TRUE)
    o.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, java.lang.Boolean.TRUE)
    o.toImmutable

  val parser: Parser = Parser.builder(options).build()

/** flexmark, configured once for every server page. */
object MarkdownHtml:
  private val renderer = HtmlRenderer.builder(MarkdownFlavor.options).build()

  def render(md: String): String = renderer.render(MarkdownFlavor.parser.parse(md))

/** PURE rendering of the typed page models to HTML. Wikilinks resolve through `links` (one lookup
  * for the whole page, done by [[PageBuilder]]) into the [[HtmlRender]] anchors. */
object Pages:
  import HtmlRender.*

  // ── prose ────────────────────────────────────────────────────────────────────────────────────

  /** Markdown with every `[[wikilink]]` replaced by its typed anchor (raw inline HTML, which flexmark
    * passes through). `number` appends the citation number to a source reference. */
  def linkProse(md: String, links: String => Option[LinkTarget],
      number: String => Option[Int] = _ => None): String =
    ArticleLinks.rewriteLinks(md) { (t, explicit, written) =>
      val own = explicit.filter(_.nonEmpty)
      links(t) match
        case Some(r: SourceRef) =>
          sourceRef(r, own.getOrElse(readable(r))) + number(r.citekey).fold("")(citeNumber)
        case Some(a: ArticleRef) => articleRef(a, own.getOrElse(a.title))
        case None                => dangling(own.getOrElse(Label.display(written)))
    }

  private def citeNumber(n: Int): String =
    s"""<sup class="cite-num"><a class="cite-jump" href="#cite-$n">[$n]</a></sup>"""

  /** A heading that starts the part of an article whose sources are NOT cited in its argument —
    * the wiki's `## Not used` convention (`**[[ck]]**: why`). Links there are rendered like any other
    * reference but get no citation number. */
  private val NotUsed = """(?im)^#{1,6}\s*not used\b.*$""".r

  /** Citation numbers by first appearance in `md` (the argument part of an article). */
  def numbering(md: String, links: String => Option[LinkTarget]): Map[String, Int] =
    ArticleLinks.targets(md).flatMap(t => links(t).collect { case r: SourceRef => r.citekey })
      .distinct.zipWithIndex.map((ck, i) => ck -> (i + 1)).toMap

  // ── the formatting notice ────────────────────────────────────────────────────────────────────

  /** "This article may not display correctly: 3 formatting problems", folded: it expands to one line
    * per problem — where, what goes wrong, the offending text. Unobtrusive on purpose (a reader came
    * for the article); nothing at all when the text is clean. */
  def formatNotice(issues: List[FormatIssue], what: String, slug: Option[String]): String =
    if issues.isEmpty then ""
    else
      val errs  = issues.count(_.severity == FormatSeverity.Error)
      val level = if errs > 0 then "has-errors" else "has-warnings"
      val items = issues.map { i =>
        val sev   = if i.severity == FormatSeverity.Error then "error" else "warning"
        val field = if i.field == FormatField.Body then "" else s"${esc(i.field.toString.toLowerCase)}, "
        val ex    = if i.excerpt.isEmpty then "" else s""" <code class="fi-excerpt">${esc(i.excerpt)}</code>"""
        s"""<li class="fi fi-$sev"><span class="fi-where">${field}line ${i.line}</span> """ +
          s"""<span class="fi-rule">${esc(Label.display(i.rule.wire.replace('-', '_')))}</span> — ${esc(i.message)}$ex</li>"""
      }
      val fixable = issues.count(_.fix.nonEmpty)
      val how = slug.fold(s"histmersion lint --source &lt;citekey&gt; --fix")(s => s"histmersion article lint ${esc(s)} --fix")
      val tip =
        if fixable == 0 then ""
        else
          val which = if fixable == issues.size then "all of them" else s"$fixable of them"
          s"""<p class="fi-tip"><code>$how</code> repairs $which without changing a word.</p>"""
      s"""<details class="format-notice $level"><summary>$what may not display correctly: """ +
        s"""${FormatMarkdown.problems(issues.size)}</summary><ul class="format-issues">${items.mkString}</ul>$tip</details>"""

  // ── source page ──────────────────────────────────────────────────────────────────────────────

  def source(p: SourcePage, links: String => Option[LinkTarget]): String =
    val r      = p.ref
    val period = YearFmt.period(p.periodStart, p.periodEnd)
    val byline = List(
      chips(ChipField.Author, r.authors),
      r.year.fold("n.d.")(_.toString),
      p.venue.filter(_.trim.nonEmpty).fold("")(esc),
      statusBadge(r.status),
      esc(period)
    ).filter(_.nonEmpty).mkString(" · ")
    val ident = r.doi.filter(_.nonEmpty).fold("Citekey")(_ => "DOI")
    val facets = List(
      "Topics"  -> chips(ChipField.Topic, p.topics),
      "Regions" -> chips(ChipField.Place, p.regions),
      "Tags"    -> chips(ChipField.Tag, p.tags)
    ).collect { case (l, c) if c.nonEmpty => s"""<p class="src-chips"><span class="lbl">$l</span> $c</p>""" }
    // the figure pipeline of plan §1.8 (legacy `figures/…` links, numbering, served paths, lazy
    // images) runs over every Markdown part of a source, as it did when the page was one document
    def html(md: String): String =
      val linked = AssetLinks.numberFigures(AssetLinks.fromFigures(linkProse(md, links)))
      AssetLinks.lazyImages(MarkdownHtml.render(AssetLinks.toWeb(linked, r.citekey)))
    val prose = (md: Option[String]) => md.map(_.trim).filter(_.nonEmpty).map(html)
    val findings =
      if p.findings.isEmpty then ""
      else s"""<h2>Findings (${p.findings.size})</h2><ul class="findings">${p.findings.map(finding).mkString}</ul>"""
    val body = p.fullText match
      case FullText.Hidden      => ""
      case FullText.Missing     => """<hr><p class="muted">No full text is stored for this source.</p>"""
      // normalised here as well as at import: the corpus was seeded before the figure-link rewrite
      case FullText.Shown(text) => "<hr>" + html(AssetLinks.normalizeBody(text, r.title))
    s"""<h1>${esc(r.title)}</h1>""" +
      s"""<p class="src-byline">$byline</p>""" +
      s"""<p class="src-ident"><span class="lbl">$ident</span> ${identifier(r)}</p>""" +
      facets.mkString +
      formatNotice(p.formatting, "These notes", None) +
      prose(p.summary).fold("")(h => s"""<div class="src-summary">$h</div>""") +
      prose(p.notes).fold("")(h => s"<h2>Notes</h2>$h") +
      findings +
      citations(p.cites, p.citedBy) +
      Article.toggle(r.citekey, p.fullText != FullText.Hidden, p.query) +
      body

  /** "Cites" and "Cited by", each omitted when empty — and nothing at all when both are. */
  def citations(cites: List[Citation], citedBy: List[Citation]): String =
    def block(title: String, cs: List[Citation]) =
      if cs.isEmpty then ""
      else s"""<h2>$title (${cs.size})</h2><ul class="citations">${cs.map(citation).mkString}</ul>"""
    block("Cites", cites) + block("Cited by", citedBy)

  private def kindLabel(kind: String): String = s"""<span class="edge-kind">${esc(Label.display(kind))}</span> """

  def citation(c: Citation): String = c match
    case Citation.ToSource(r, kind) =>
      s"""<li>${kindLabel(kind)}${sourceRef(r, readable(r))} <i>${esc(r.title)}</i> """ +
        s"""${statusBadge(r.status)} <span class="ident">${identifier(r)}</span></li>"""
    case Citation.ToArticle(a, kind) =>
      s"""<li>${kindLabel(kind.getOrElse("article"))}${articleRef(a)}</li>"""
    case Citation.Unresolved(id, kind) =>
      s"""<li>${kindLabel(kind)}${dangling(id)} <span class="muted">not in the corpus yet</span></li>"""

  // ── article page ─────────────────────────────────────────────────────────────────────────────

  def article(p: ArticlePage, links: String => Option[LinkTarget]): String =
    val a       = p.article
    val body0   = a.body.trim
    val heading = Articles.heading(body0)
    // the `# Title` line is rendered here, so the meta line can sit right under it
    val body1   = if heading.isEmpty then body0 else body0.linesIterator.dropWhile(_.trim.isEmpty).drop(1).mkString("\n")
    // item 23: a trailing hand-written "## Sources" list duplicates the generated footer
    val body    = if p.cited.isEmpty then body1 else ArticleMarkdown.stripSourcesSection(body1)
    val (argument, notUsed) = NotUsed.findFirstMatchIn(body) match
      case Some(m) => (body.take(m.start), body.drop(m.start))
      case None    => (body, "")
    val numbers = numbering(argument, links)
    // the generated sections (#958) follow the body; like a hand-written Not used, their links get no number
    val generated = p.coverage.fold("")(c => "\n\n" + ArticleCoverage.sections(c))
    val prose   = MarkdownHtml.render(linkProse(argument, links, numbers.get) + linkProse(notUsed + generated, links))
    val meta = List(
      esc(Label.display(a.kind.trim)),
      chips(ChipField.Place, a.regions),
      esc(YearFmt.period(a.periodStart, a.periodEnd)),
      chips(ChipField.Tag, a.tags)
    ).filter(_.nonEmpty)
    s"""<h1>${esc(heading.getOrElse(a.title))}</h1>""" +
      (if meta.isEmpty then "" else s"""<p class="article-meta-line">${meta.mkString(" · ")}</p>""") +
      formatNotice(p.formatting, "This article", Some(a.slug)) +
      prose +
      backlinks(p.backlinks) +
      sourcesCited(citedEntries(p.cited, numbers, links))

  /** The footer's entries: every numbered source (in number order), then the article's other cited
    * sources — including a stored citekey that is not in the corpus, shown as such. */
  def citedEntries(cited: List[CitedSource], numbers: Map[String, Int],
      links: String => Option[LinkTarget]): List[CitedEntry] =
    val byCk = cited.map(c => c.citekey -> c).toMap
    val numbered = numbers.toList.sortBy(_._2).flatMap { (ck, n) =>
      byCk.get(ck).map(c => CitedEntry(Some(n), ck, c.doi, c.authors, c.year, c.title, c.status))
        .orElse(links(ck).collect { case r: SourceRef =>
          CitedEntry(Some(n), ck, r.doi, r.authors, r.year, Some(r.title), Some(r.status)) })
    }
    val rest = cited.filterNot(c => numbers.contains(c.citekey)).sortBy(_.citekey)
      .map(c => CitedEntry(None, c.citekey, c.doi, c.authors, c.year, c.title, c.status))
    numbered ++ rest

  def sourcesCited(entries: List[CitedEntry]): String =
    if entries.isEmpty then ""
    else
      val items = entries.map { e =>
        val id = e.number.fold("")(n => s""" id="cite-$n"""")
        e.title match
          case None => s"""<li$id>${dangling(e.citekey)} <span class="muted">not in the corpus</span></li>"""
          case Some(t) =>
            val byline = List(SourceRef.shortAuthors(e.authors), e.year.fold("")(_.toString)).filter(_.nonEmpty).mkString(" ")
            val status = e.status.fold("")(s => " " + statusBadge(s))
            val ident  = identifier(e.citekey, e.doi)
            s"""<li$id>${sourceLink(e.citekey, e.doi, if byline.nonEmpty then byline else t)} <i>${esc(t)}</i>$status""" +
              s""" <span class="ident">$ident</span></li>"""
      }
      s"""<h2>Sources cited (${entries.size})</h2><ol class="sources-cited">${items.mkString}</ol>"""

  def backlinks(rows: List[Backlink]): String =
    if rows.isEmpty then ""
    else
      val items = rows.map { b =>
        val label = b.title.filter(_.trim.nonEmpty).getOrElse(Label.display(b.id))
        val link =
          if b.kind == ArticleLinks.TargetSource then sourceLink(b.id, None, label)
          else articleRef(ArticleRef(b.id, label), label)
        s"<li>$link</li>"
      }
      s"""<h2>Backlinks (${rows.size})</h2><ul class="backlinks">${items.mkString}</ul>"""

/** Assembles the typed page models from the handlers — the one impure step between the database
  * and [[Pages]]. Each page resolves its links with ONE [[Handlers.pageLinks]] call. */
final class PageBuilder(h: Handlers):

  /** `(title, inner HTML)` of `/source/{citekey}`, `?q=` terms highlighted. */
  def source(ck: String, full: Boolean, q: Option[String]): Option[(String, String)] =
    h.getSource(ck).toOption.map { sw =>
      val s        = sw.source
      val ref      = SourceRef(s.citekey, s.doi, s.authors, s.year, s.title, s.status)
      val rows     = h.neighbors(ck)
      val refs     = h.sourceRefs(rows.filter(_.target == NeighborTarget.Source).map(_.citekey))
        .map(r => r.citekey -> r).toMap
      val articles = h.articleRefs(rows.filter(_.target == NeighborTarget.Article).map(_.citekey))
        .map(a => a.slug -> a).toMap
      def entry(r: NeighborRow): Option[Citation] = r.target match
        case NeighborTarget.Source     => refs.get(r.citekey).map(Citation.ToSource(_, r.kind))
        case NeighborTarget.Article    => articles.get(r.citekey).map(a => Citation.ToArticle(a, Some(r.kind)))
        case NeighborTarget.Unresolved => Some(Citation.Unresolved(r.citekey, r.kind))
      val citingArticles = h.sourceArticles(ck).getOrElse(Nil)
        .map(b => Citation.ToArticle(ArticleRef(b.id, b.title.getOrElse(Label.display(b.id))), None))
      val term = q.map(_.trim).filter(_.nonEmpty)
      val page = SourcePage(
        ref = ref, venue = s.venue, periodStart = s.periodStart, periodEnd = s.periodEnd,
        topics = s.topics, regions = s.regions, tags = s.tags, summary = s.summary, notes = s.notes,
        findings = sw.findings,
        cites = rows.filter(_.direction == "out").flatMap(entry),
        citedBy = rows.filter(_.direction == "in").flatMap(entry) ++ citingArticles,
        fullText =
          if !full then FullText.Hidden
          else s.ocrMarkdown.filter(_.trim.nonEmpty).fold(FullText.Missing)(FullText.Shown(_)),
        query = term,
        formatting = h.sourceFormatting(s.summary, s.notes))
      val md    = List(s.summary, s.notes, if full then s.ocrMarkdown else None).flatten.mkString("\n\n")
      val links = h.pageLinks(ArticleLinks.targets(md))
      (s.title, highlighted(Pages.source(page, links), term))
    }

  /** `(title, inner HTML)` of `/article/{slug}`, `?q=` terms highlighted. */
  def article(slug: String, q: Option[String]): Option[(String, String)] =
    h.getArticle(slug).toOption.map { v =>
      val generated = v.coverage.fold(List.empty[String])(c => ArticleLinks.targets(ArticleCoverage.sections(c)))
      val links = h.pageLinks(ArticleLinks.targets(v.article.body) ++ v.sourcesCited.map(_.citekey) ++ generated)
      val html  = Pages.article(ArticlePage(v.article, v.backlinks, v.sourcesCited, v.formatting, v.coverage), links)
      (v.article.title, highlighted(html, q.map(_.trim).filter(_.nonEmpty)))
    }

  private def highlighted(html: String, term: Option[String]): String = term match
    case Some(t) =>
      val (out, n) = Highlight(html, t)
      Article.matchNav(n) + out
    case None => html
