package com.histmersion.server

import scala.annotation.tailrec
import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.vladsch.flexmark.ast.{
  BulletList,
  Code,
  Emphasis,
  FencedCodeBlock,
  Heading,
  HtmlBlock,
  HtmlInline,
  Image,
  IndentedCodeBlock,
  Link,
  LinkRef,
  ListItem,
  OrderedList,
  OrderedListItem,
  Paragraph,
  StrongEmphasis,
  Text
}
import com.vladsch.flexmark.ext.tables.TableBlock
import com.vladsch.flexmark.util.ast.{Document, Node}

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatMarkdown, FormatRule, FormatSeverity}
import com.histmersion.api.library.{Finding, Label}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** The FORMATTING lint: what in an agent's Markdown will not display as it was meant (pure).
  *
  * The text is parsed with [[MarkdownFlavor]] — the parser the pages render with — after the same wikilink rewrite the
  * page does (every `[[x]]` becomes inline HTML before flexmark sees it; here it is masked with letters of the same
  * length, so offsets stay those of the stored text). Most checks read the resulting AST: a run of pipe rows with no
  * `TableBlock` over it IS a table that will show as text; a `**` left in a `Text` node IS bold that did not pair. The
  * rest are line checks for what the AST cannot see (frontmatter, chatter, placeholders, malformed wikilinks).
  *
  * Each problem carries the edits that repair it when the repair never touches wording (insert a blank line, unescape
  * `\[\[`, strip a pasted frontmatter block); [[fix]] applies them.
  */
object MarkdownLint:

  /** What the check knows beyond the text.
    *
    * @param article
    *   an article body: its `# Title` is expected, and the page builds the "Sources cited" list itself
    * @param resolves
    *   does a `[[target]]` resolve to a source or an article? `None` = do not check (the corpus-wide lint leaves that
    *   to `dangling-article-links`)
    * @param isCitekey
    *   is this token a stored citekey? `None` = do not look for bare citekeys
    */
  type FixLoopResultPart1 = FixLoopResultPart1.T
  object FixLoopResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopResultPart2 = FixLoopResultPart2.T
  object FixLoopResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopT = FixLoopT.T
  object FixLoopT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopRound = FixLoopRound.T
  object FixLoopRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopDone = FixLoopDone.T
  object FixLoopDone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixLoopNonOverlappingReach = FixLoopNonOverlappingReach.T
  object FixLoopNonOverlappingReach extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IssuesText = _root_.com.histmersion.server.MarkdownLint.CheckText
  val IssuesText: _root_.com.histmersion.server.MarkdownLint.CheckText.type = _root_.com.histmersion.server.MarkdownLint.CheckText
  type LookupsResult = LookupsResult.T
  object LookupsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupsTexts = LookupsTexts.T
  object LookupsTexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolvingLinksArg1 = ResolvingLinksArg1.T
  object ResolvingLinksArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolvingArticle = _root_.com.histmersion.server.MarkdownLint.Context.Article
  val ResolvingArticle: _root_.com.histmersion.server.MarkdownLint.Context.Article.type = _root_.com.histmersion.server.MarkdownLint.Context.Article
  type OfArticleBody = OfArticleBody.T
  object OfArticleBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfArticleSummary = OfArticleSummary.T
  object OfArticleSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfArticleLinksArg1 = OfArticleLinksArg1.T
  object OfArticleLinksArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfSourceSummary = OfSourceSummary.T
  object OfSourceSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfSourceNotes = OfSourceNotes.T
  object OfSourceNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfSourceLinksArg1 = OfSourceLinksArg1.T
  object OfSourceLinksArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalResult = RefusalResult.T
  object RefusalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixResultPart1 = FixResultPart1.T
  object FixResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixResultPart2 = FixResultPart2.T
  object FixResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixText = FixText.T
  object FixText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckText = CheckText.T
  object CheckText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownTags = KnownTags.T
  object KnownTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VoidTags = VoidTags.T
  object VoidTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockTags = BlockTags.T
  object BlockTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaKeys = MetaKeys.T
  object MetaKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Context(
      article: Context.Article = false,
      resolves: Option[Context.ResolvesArg1 => Context.ResolvesResult] = None,
      isCitekey: Option[Context.IsCitekeyArg1 => Context.IsCitekeyResult] = None
  )

  object Context:
    type ResolvesArg1 = ResolvesArg1.T
    object ResolvesArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolvesResult = ResolvesResult.T
    object ResolvesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsCitekeyArg1 = IsCitekeyArg1.T
    object IsCitekeyArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsCitekeyResult = IsCitekeyResult.T
    object IsCitekeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Article = Article.T
    object Article extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One text replacement on the STORED text: `[start, end)` becomes `replacement`. */
  final case class Edit(start: Edit.Start, end: Edit.End, replacement: Edit.Replacement)

  object Edit:
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Replacement = Replacement.T
    object Replacement extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** An issue and the edits that repair it (empty = not mechanically fixable). */
  final case class Finding(issue: FormatIssue, edits: List[Edit])

  def issues(text: com.histmersion.server.MarkdownLint.IssuesText, field: FormatField, ctx: Context = Context()): List[FormatIssue] =
    check(text, field, ctx).map(_.issue)

  // ── one record's texts, resolved against the corpus ──────────────────────────────────────────

  /** What to look up before checking `texts`: every `[[target]]`, and every token shaped like a citekey (a bare one is
    * only a problem when a source by that name exists). One `pageLinks` call answers both.
    */
  def lookups(texts: List[com.histmersion.server.MarkdownLint.LookupsTexts]): List[com.histmersion.server.MarkdownLint.LookupsResult] =
    texts.flatMap(t => Article.Links.targets(t) ++ CitekeyTok.findAllMatchIn(t).map(_.group(1))).distinct

  private def resolving(links: com.histmersion.server.MarkdownLint.ResolvingLinksArg1 => Option[LinkTarget], article: com.histmersion.server.MarkdownLint.ResolvingArticle): Context =
    Context(
      article = article,
      resolves = Some(t => links(t).nonEmpty),
      isCitekey = Some(t => links(t).exists(_.isInstanceOf[Source.Ref]))
    )

  /** An article's body (checked as an article: its `# Title` is expected, a `Sources cited` heading is not) and its
    * summary.
    */
  def ofArticle(body: com.histmersion.server.MarkdownLint.OfArticleBody, summary: Option[com.histmersion.server.MarkdownLint.OfArticleSummary], links: com.histmersion.server.MarkdownLint.OfArticleLinksArg1 => Option[LinkTarget]): List[FormatIssue] =
    issues(body, FormatField.Body, resolving(links, article = true)) ++
      summary.toList.flatMap(issues(_, FormatField.Summary, resolving(links, article = false)))

  /** A source's summary and notes — the two texts an agent writes about one source. */
  def ofSource(summary: Option[com.histmersion.server.MarkdownLint.OfSourceSummary], notes: Option[com.histmersion.server.MarkdownLint.OfSourceNotes], links: com.histmersion.server.MarkdownLint.OfSourceLinksArg1 => Option[LinkTarget]): List[FormatIssue] =
    summary.toList.flatMap(issues(_, FormatField.Summary, resolving(links, article = false))) ++
      notes.toList.flatMap(issues(_, FormatField.Notes, resolving(links, article = false)))

  /** The `--strict` gate: the ERRORS among `issues`, as the refusal message, or `None` to write. */
  def refusal(issues: List[FormatIssue]): Option[com.histmersion.server.MarkdownLint.RefusalResult] =
    val errs = issues.filter(_.severity == FormatSeverity.Error)
    Option.when(errs.nonEmpty)(
      s"refused (strict): ${Plural.count(errs.size, "formatting error")} — " +
        errs.take(5).map(FormatMarkdown.line).mkString("; ") +
        (if errs.sizeIs > 5 then s"; … and ${errs.size - 5} more" else "")
    )

  /** Apply every safe repair, re-checking after each round (one repair can uncover another: an unwrapped ```markdown
    * fence exposes the body inside it). At most three rounds; returns the new text and one line per applied repair.
    * Wording is never changed.
    */
  def fix(text: com.histmersion.server.MarkdownLint.FixText, field: FormatField, ctx: Context = Context()): (com.histmersion.server.MarkdownLint.FixResultPart1, List[com.histmersion.server.MarkdownLint.FixResultPart2]) =
    @annotation.tailrec
    def loop(t: com.histmersion.server.MarkdownLint.FixLoopT, round: com.histmersion.server.MarkdownLint.FixLoopRound, done: List[com.histmersion.server.MarkdownLint.FixLoopDone]): (com.histmersion.server.MarkdownLint.FixLoopResultPart1, List[com.histmersion.server.MarkdownLint.FixLoopResultPart2]) =
      val fixable = check(t, field, ctx).filter(_.edits.nonEmpty)
      if fixable.isEmpty || round >= 3 then (t, done)
      else
        // non-overlapping, earliest first; an edit that overlaps an earlier one waits for the next round
        @tailrec def nonOverlapping(rest: List[Finding], acc: List[Finding], reach: com.histmersion.server.MarkdownLint.FixLoopNonOverlappingReach): List[Finding] =
          rest match
            case Nil       => acc
            case f :: tail =>
              val lo = f.edits.map(_.start).min
              if lo < reach then nonOverlapping(tail, acc, reach)
              else nonOverlapping(tail, f :: acc, f.edits.map(_.end).max.max(lo + 1))
        val chosen = nonOverlapping(fixable.sortBy(f => f.edits.map(_.start).min), Nil, -1)
        val edits = chosen.flatMap(_.edits).sortBy(e => (-e.start, -e.end))
        val next = edits.foldLeft(t)((s, e) => s.substring(0, e.start) + e.replacement + s.substring(e.end))
        val lines =
          chosen.reverse.map(f => s"${f.issue.rule.wire} at line ${f.issue.line}: ${f.issue.fix.getOrElse("repaired")}")
        if next == t then (t, done) else loop(next, round + 1, done ++ lines)
    loop(text, 0, Nil)

  // ── the check ────────────────────────────────────────────────────────────────────────────────

  def check(text: com.histmersion.server.MarkdownLint.CheckText, field: FormatField, ctx: Context = Context()): List[Finding] =
    if text.trim.isEmpty then Nil
    else
      val s = Scan(text, field, ctx)
      s.run()
      s.findings.toList
        .sortBy(f => (f.issue.line, f.issue.column, f.issue.rule.ordinal))
        .distinctBy(f => (f.issue.rule, f.issue.line, f.issue.column))

  /** HTML elements a browser knows. Anything else (`<etal>`, `<insert>`) is dropped by it. */
  private val KnownTags: Set[com.histmersion.server.MarkdownLint.KnownTags] = Set(
    "a",
    "abbr",
    "b",
    "bdi",
    "bdo",
    "blockquote",
    "br",
    "caption",
    "cite",
    "code",
    "col",
    "colgroup",
    "dd",
    "del",
    "details",
    "dfn",
    "div",
    "dl",
    "dt",
    "em",
    "figcaption",
    "figure",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "hr",
    "i",
    "img",
    "ins",
    "kbd",
    "li",
    "mark",
    "ol",
    "p",
    "pre",
    "q",
    "s",
    "samp",
    "section",
    "small",
    "span",
    "strong",
    "sub",
    "summary",
    "sup",
    "table",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "time",
    "tr",
    "u",
    "ul",
    "var",
    "wbr",
    "figure",
    "aside",
    "nav",
    "header",
    "footer",
    "article",
    "main",
    "center",
    "font",
    "tt",
    "big",
    "strike"
  )
  private val VoidTags: Set[com.histmersion.server.MarkdownLint.VoidTags] = Set("br", "hr", "img", "wbr", "col")

  /** An unclosed one of these swallows everything after it, not just the rest of a paragraph. */
  private val BlockTags: Set[com.histmersion.server.MarkdownLint.BlockTags] = Set(
    "div",
    "details",
    "table",
    "blockquote",
    "pre",
    "ul",
    "ol",
    "section",
    "figure",
    "aside",
    "nav",
    "header",
    "footer",
    "article",
    "main",
    "center"
  )

  /** Keys a pasted frontmatter block uses — the article's and the source's own metadata fields. */
  private val MetaKeys: Set[com.histmersion.server.MarkdownLint.MetaKeys] = Set(
    "title",
    "slug",
    "type",
    "kind",
    "summary",
    "tags",
    "regions",
    "period",
    "period_start",
    "period_end",
    "periodstart",
    "periodend",
    "citekey",
    "doi",
    "authors",
    "author",
    "year",
    "status",
    "date",
    "topics",
    "venue",
    "aliases",
    "created",
    "updated",
    "source",
    "sources",
    "description",
    "lang",
    "language",
    "cssclass",
    "cssclasses",
    "publish"
  )

  private val ChatterLine =
    ("""(?i)^\s*(?:[>*_#-]+\s*)?(?:here(?:'s| is| are) (?:the|your|an?|my|a revised|the revised|the updated)\b""" +
      """|sure[!,.]|certainly[!,.]|absolutely[!,.]|of course[!,.]\s|great question""" +
      """|as an ai\b|as a(?:n ai)? language model|i hope this helps|let me know if|feel free to""" +
      """|i(?:'ve| have) (?:rewritten|revised|updated|added|written|expanded|drafted|incorporated)\b""" +
      """|below is (?:the|a|an|your)\b|(?:the )?(?:article|summary|draft) (?:is )?(?:below|above)[.:])""").r
  private val Placeholders =
    """\b(?:TODO|FIXME|TBD|XXX)\b|\[(?:citation needed|citation|cite|ref|source)\??\]|<insert[^>\n]*>|\[insert [^\]\n]*\]|\b[Ll]orem ipsum\b|\[\[(?:citekey|slug|todo|TODO)\]\]|\{\{[^}\n]+\}\}""".r
  private val FenceLine = """^ {0,3}(`{3,}|~{3,})\s*([\w-]*)\s*$""".r
  private val DelimRow = """^\s*\|?\s*:?-+:?\s*(?:\|\s*:?-+:?\s*)*\|?\s*$""".r
  private val UrlText = """https?://[^\s<>()\[\]"'`]+""".r
  private val CitekeyTok = """(?<![\w/\[#.\-@])([a-z][a-z-]*\d{4}[a-z]?(?:_[a-z0-9]+)+)(?![\w\]-])""".r
  private val WikiPathUrl =
    """^(?:\.{0,2}/)?(?:[\w.-]+/)*([\w.-]+)\.md(?:#.*)?$|^/(?:article|source)/([a-z0-9_.-]+)/?$""".r
  private val MethodLine = """(?is)^\s*[_*]{1,2}\s*method\b.*""".r
  private val LocalPath = """^(?:file:|/(?:Users|home|tmp|var|private|Volumes|mnt)/|[A-Za-z]:\\|\.\.?/|figures/)""".r

  /** One pass over one text. Mutable on purpose: a builder of findings, local to one `check`. */
  final private class Scan(text: Scan.Text, field: FormatField, ctx: Context):
    val findings = mutable.ListBuffer.empty[Finding]

    // offsets → (line, column), both 1-based
    private val lineStarts: com.histmersion.server.MarkdownLint.Scan.LineStarts =
      (0 +: text.indices.filter(text.charAt(_) == '\n').map(_ + 1)).toArray
    private def lineOf(off: com.histmersion.server.MarkdownLint.Scan.LineOfOff): com.histmersion.server.MarkdownLint.Scan.LineOfResult =
      val i = java.util.Arrays.binarySearch(lineStarts, off)
      if i >= 0 then i else -i - 2
    private def lineText(l: com.histmersion.server.MarkdownLint.Scan.LineTextL): com.histmersion.server.MarkdownLint.Scan.LineTextResult =
      val st = lineStarts(l)
      val en = if l + 1 < lineStarts.length then lineStarts(l + 1) - 1 else text.length
      text.substring(st, en)
    private val nLines = lineStarts.length

    private def excerptAt(off: com.histmersion.server.MarkdownLint.Scan.ExcerptAtOff, len: com.histmersion.server.MarkdownLint.Scan.ExcerptAtLen): com.histmersion.server.MarkdownLint.Scan.ExcerptAtResult =
      val l = lineOf(off)
      val ln = lineText(l)
      val col = off - lineStarts(l)
      val from = math.max(0, col - 20)
      val to = math.min(ln.length, math.max(col + len, col) + 40)
      val cut = ln.substring(from, to).trim
      val show = if cut.length > 80 then cut.take(79) + "…" else cut
      (if from > 0 then "…" else "") + show + (if to < ln.length && cut.length <= 80 then "…" else "")

    def add(
        rule: FormatRule,
        off: com.histmersion.server.MarkdownLint.Scan.AddOff,
        len: com.histmersion.server.MarkdownLint.Scan.AddLen,
        message: com.histmersion.server.MarkdownLint.Scan.AddMessage,
        edits: List[Edit] = Nil,
        fix: Option[com.histmersion.server.MarkdownLint.Scan.AddFix] = None,
        severity: Option[FormatSeverity] = None
    ): Unit =
      val o = math.max(0, math.min(off, text.length))
      val l = lineOf(o)
      findings += Finding(
        FormatIssue(
          rule,
          severity.getOrElse(rule.severity),
          field,
          l + 1,
          o - lineStarts(l) + 1,
          excerptAt(o, len),
          message,
          if edits.nonEmpty then fix.orElse(Some("repair")) else None
        ),
        edits
      )

    // ── the masked text and its AST ──────────────────────────────────────────────────────────────
    // every wikilink the renderer rewrites is replaced by `W`s of the same length (as the page puts
    // an anchor there before flexmark runs), so `**[[ck]]**` is bold and `[[a_b|c]]` no table cell
    private val links = Article.Links.occurrences(text)
    private val masked =
      val b = new java.lang.StringBuilder(text)
      links.filter(o => o.target.nonEmpty && !o.embed).foreach(o => (o.start until o.end).foreach(b.setCharAt(_, 'W')))
      b.toString
    private val doc: Document = MarkdownFlavor.parser.parse(masked)
    private val nodes: List[Node] = doc.getDescendants.asScala.toList

    /** Offsets inside code (fenced, indented, inline) — where nothing is Markdown. */
    private val code: java.util.BitSet =
      val b = new java.util.BitSet(text.length + 1)
      nodes.foreach {
        case n @ (_: FencedCodeBlock | _: IndentedCodeBlock | _: Code) => b.set(n.getStartOffset, n.getEndOffset)
        case _                                                         => ()
      }
      b

    /** Offsets the renderer shows as LITERAL text (a `Text` node outside code). */
    private val literal: java.util.BitSet =
      val b = new java.util.BitSet(text.length + 1)
      nodes.foreach {
        case t: Text if !insideCode(t) => b.set(t.getStartOffset, t.getEndOffset)
        case _                         => ()
      }
      b
    private def insideCode(n: Node): com.histmersion.server.MarkdownLint.Scan.InsideCodeResult =
      var p = n.getParent
      var in = false
      while p != null && !in do
        in = p.isInstanceOf[Code] || p.isInstanceOf[FencedCodeBlock] || p.isInstanceOf[IndentedCodeBlock]
        p = p.getParent
      in
    private def isLiteral(i: com.histmersion.server.MarkdownLint.Scan.IsLiteralI): com.histmersion.server.MarkdownLint.Scan.IsLiteralResult = i >= 0 && i < text.length && literal.get(i) && !escaped(i)
    private def escaped(i: com.histmersion.server.MarkdownLint.Scan.EscapedI): com.histmersion.server.MarkdownLint.Scan.EscapedResult =
      var k = i - 1; var n = 0
      while k >= 0 && text.charAt(k) == '\\' do { n += 1; k -= 1 }
      n % 2 == 1
    private def lineInCode(l: com.histmersion.server.MarkdownLint.Scan.LineInCodeL): com.histmersion.server.MarkdownLint.Scan.LineInCodeResult =
      val st = lineStarts(l)
      val ln = lineText(l)
      ln.trim.nonEmpty && code.get(st + ln.indexWhere(!_.isWhitespace).max(0))

    def run(): Unit =
      wrapperAndFrontmatter()
      fences()
      indentedCode()
      tables()
      inlineLiterals()
      starredForms()
      linkNodes()
      htmlTags()
      headings()
      lists()
      lineChecks()
      wikilinks()

    // ── the document as a whole ────────────────────────────────────────────────────────────────

    private var wrapped: Option[com.histmersion.server.MarkdownLint.Scan.Wrapped] = None // the offset of a whole-text fence, reported once
    private var frontmatterEnd: Option[com.histmersion.server.MarkdownLint.Scan.FrontmatterEnd] = None // where a reported frontmatter block ends

    private def wrapperAndFrontmatter(): Unit =
      val first = (0 until nLines).find(l => lineText(l).trim.nonEmpty)
      first.foreach { f =>
        val l0 = lineText(f)
        // ```markdown … ``` around everything
        FenceLine.findFirstMatchIn(l0).foreach { m =>
          val info = m.group(2).toLowerCase
          val last = (nLines - 1 to f + 1 by -1).find(l => lineText(l).trim.nonEmpty)
          val closes = last.exists(l =>
            lineText(l).trim.startsWith(m.group(1).take(1) * 3) &&
              lineText(l).trim.forall(c => c == m.group(1).head)
          )
          val inner = (f + 1 until last.filter(_ => closes).getOrElse(nLines)).map(lineText).mkString("\n")
          val looksMd = inner.linesIterator.exists(l => l.startsWith("#") || l.contains("[[") || l.startsWith("- "))
          if (info == "markdown" || info == "md" || (info.isEmpty && closes)) && looksMd then
            wrapped = Some(lineStarts(f))
            val openEdit = Edit(lineStarts(f), math.min(text.length, lineStarts(f) + l0.length + 1), "")
            val closeEdit = last
              .filter(_ => closes)
              .map(l => Edit(lineStarts(l), math.min(text.length, lineStarts(l) + lineText(l).length + 1), ""))
            add(
              FormatRule.WrappedInFence,
              lineStarts(f),
              l0.length,
              "the whole text is inside a code fence, so the page shows it as one grey block of raw Markdown",
              openEdit :: closeEdit.toList,
              Some("remove the wrapping fence lines")
            )
        }
        // a YAML block: `---` … `---` at the very top
        if l0.trim == "---" then
          val close =
            (f + 1 until math.min(nLines, f + 60)).find(l => lineText(l).trim == "---" || lineText(l).trim == "...")
          close.foreach { c =>
            val inner = (f + 1 until c).map(lineText).filter(_.trim.nonEmpty)
            val keys =
              inner.flatMap(l => """^([A-Za-z_][\w-]*)\s*:""".r.findFirstMatchIn(l).map(_.group(1).toLowerCase))
            if inner.nonEmpty && keys.size * 2 >= inner.count(l =>
                !l.startsWith(" ") && !l.trim.startsWith("- ")
              ) && keys.nonEmpty
            then
              val end = lineStarts(c) + lineText(c).length
              val after = text.indexWhere(ch => ch != '\n' && ch != '\r', end) match {
                case -1 => text.length; case i => i
              }
              val safe = keys.forall(MetaKeys.contains)
              frontmatterEnd = Some(end)
              add(
                FormatRule.Frontmatter,
                lineStarts(f),
                3,
                s"a YAML frontmatter block (${keys.take(4).mkString(", ")}${if keys.size > 4 then ", …" else ""}) " +
                  "pasted into the text: the page shows a rule and the keys as a heading — the record's fields hold this metadata",
                if safe then List(Edit(lineStarts(f), after, "")) else Nil,
                Some("remove the frontmatter block (its keys are the record's own fields)")
              )
          }
        else
          // bare `key: value` header lines at the top (two or more metadata keys in a row)
          val run = (f until nLines).takeWhile { l =>
            """^([A-Za-z_][\w-]*)\s*:\s""".r
              .findFirstMatchIn(lineText(l) + " ")
              .map(_.group(1).toLowerCase)
              .exists(MetaKeys.contains)
          }
          if run.size >= 2 then
            val last = run.last
            val end = lineStarts(last) + lineText(last).length
            val after = text.indexWhere(ch => ch != '\n' && ch != '\r', end) match {
              case -1 => text.length; case i => i
            }
            add(
              FormatRule.Frontmatter,
              lineStarts(f),
              lineText(f).length,
              s"${run.size} metadata lines (`${lineText(f).takeWhile(_ != ':')}: …`) pasted at the top: they show as a paragraph of text",
              List(Edit(lineStarts(f), after, "")),
              Some("remove the metadata lines (they are the record's own fields)")
            )
      }

    private def fences(): Unit = nodes.foreach {
      case f: FencedCodeBlock if f.getClosingMarker.length == 0 && !wrapped.contains(f.getStartOffset) =>
        val l = lineOf(f.getStartOffset)
        val opening = f.getOpeningMarker.toString
        val shorter = (l + 1 until nLines)
          .map(lineText)
          .exists(t => FenceLine.findFirstMatchIn(t).exists(m => m.group(2).isEmpty && m.group(1).head == opening.head))
        val why =
          if shorter then s"its closing fence is shorter than the opening `$opening`, so it does not close it"
          else s"`$opening` is never closed"
        add(
          FormatRule.UnclosedFence,
          f.getStartOffset,
          opening.length,
          s"a code fence: $why — everything to the end of the text renders as code"
        )
      case _ => ()
    }

    private def indentedCode(): Unit = nodes.foreach {
      case c: IndentedCodeBlock =>
        val topLevel = c.getParent.isInstanceOf[Document]
        val l0 = lineOf(c.getStartOffset)
        val l1 = lineOf(math.max(c.getStartOffset, c.getEndOffset - 1))
        val edits =
          if !topLevel then Nil
          else
            (l0 to l1).toList
              .map { l =>
                val t = lineText(l)
                val n = t.takeWhile(ch => ch == ' ' || ch == '\t').length
                Edit(lineStarts(l), lineStarts(l) + n, "")
              }
              .filter(e => e.end > e.start)
        add(
          FormatRule.IndentedCode,
          c.getStartOffset,
          20,
          "text indented by four or more spaces after a blank line renders as a grey code block, not prose",
          edits,
          Some("remove the indentation")
        )
      case _ => ()
    }

    // ── tables ─────────────────────────────────────────────────────────────────────────────────

    /** Cells of one pipe row, split the way GFM splits it (an escaped `\|` and a pipe in a code span do not split;
      * wikilinks are already masked).
      */
    private def cells(line: com.histmersion.server.MarkdownLint.Scan.CellsLine): com.histmersion.server.MarkdownLint.Scan.CellsResult =
      var t = line.trim
      if t.startsWith("|") then t = t.drop(1)
      if t.endsWith("|") && !t.endsWith("\\|") then t = t.dropRight(1)
      var n = 1; var i = 0; var inCode = false
      while i < t.length do
        val ch = t.charAt(i)
        if ch == '`' then inCode = !inCode
        else if ch == '\\' then i += 1
        else if ch == '|' && !inCode then n += 1
        i += 1
      n

    private def maskedLine(l: com.histmersion.server.MarkdownLint.Scan.MaskedLineL): com.histmersion.server.MarkdownLint.Scan.MaskedLineResult =
      val st = lineStarts(l)
      masked.substring(st, st + lineText(l).length)

    private def pipeRow(l: com.histmersion.server.MarkdownLint.Scan.PipeRowL): com.histmersion.server.MarkdownLint.Scan.PipeRowResult =
      val t = maskedLine(l).trim
      !lineInCode(l) && (t
        .startsWith("|") || (t.count(_ == '|') >= 2 && DelimRow.findFirstIn(t).isEmpty && t.contains(" | ")) ||
        (t.contains("|") && DelimRow.matches(t) && t.contains("-")))

    private def tables(): Unit =
      val rendered: List[(com.histmersion.server.MarkdownLint.Scan.TablesRenderedPart1, com.histmersion.server.MarkdownLint.Scan.TablesRenderedPart2)] = nodes.collect { case t: TableBlock =>
        (lineOf(t.getStartOffset), lineOf(math.max(t.getStartOffset, t.getEndOffset - 1)))
      }
      def coveredBy(l: com.histmersion.server.MarkdownLint.Scan.TablesCoveredByL) = rendered.find((a, b) => l >= a && l <= b)
      // runs of pipe rows
      var l = 0
      while l < nLines do
        if pipeRow(l) then
          val start = l
          while l < nLines && pipeRow(l) do l += 1
          val end = l - 1
          coveredBy(start) match
            case Some((a, b))        => renderedTable(a, b)
            case None if end > start => notRendered(start, end)
            case None                => ()
        else l += 1

    private def notRendered(start: com.histmersion.server.MarkdownLint.Scan.NotRenderedStart, end: com.histmersion.server.MarkdownLint.Scan.NotRenderedEnd): Unit =
      val header = maskedLine(start)
      val second = maskedLine(start + 1)
      val prev = if start > 0 then Some(lineText(start - 1)) else None
      val glued = prev.exists(p => p.trim.nonEmpty && !lineInCode(start - 1))
      val off = lineStarts(start)
      if !DelimRow.matches(second.trim) then
        add(
          FormatRule.TableNotRendered,
          off,
          header.length,
          s"${end - start + 1} rows of pipes with no `|---|` delimiter row under the first one — " +
            "the renderer shows them as text; add a delimiter row with one `---` per column"
        )
      else if cells(header) != cells(second) then
        add(
          FormatRule.TableNotRendered,
          off,
          header.length,
          s"the header has ${cells(header)} cells but the delimiter row has ${cells(second)} — " +
            "the renderer shows the table as text; give the delimiter row one `---` per header cell"
        )
      else if glued then
        add(
          FormatRule.TableNotRendered,
          off,
          header.length,
          "no blank line between the paragraph above and the table — the renderer folds the table into the paragraph as text",
          List(Edit(off, off, "\n")),
          Some("insert a blank line before the table")
        )
      else
        add(
          FormatRule.TableNotRendered,
          off,
          header.length,
          "these rows of pipes do not parse as a table — they show as text"
        )

    private def renderedTable(a: com.histmersion.server.MarkdownLint.Scan.RenderedTableA, b: com.histmersion.server.MarkdownLint.Scan.RenderedTableB): Unit =
      val headerCells = cells(maskedLine(a))
      (a + 2 to b).foreach { r =>
        val n = cells(maskedLine(r))
        if n != headerCells then
          val what =
            if n > headerCells then
              s"has $n cells, the header $headerCells — the extra ones are dropped" +
                (if lineText(r).contains("|") then " (an unescaped `|` inside a cell? write `\\|`)" else "")
            else s"has $n cells, the header $headerCells — the missing ones render blank"
          add(FormatRule.TableColumns, lineStarts(r), lineText(r).length, s"this table row $what")
      }
      // a row split across lines: the text after the table that is not a row of its own
      if b + 1 < nLines then
        val next = lineText(b + 1)
        val lastCells = cells(maskedLine(b))
        if next.trim.nonEmpty && !next.contains("|") && !lineInCode(b + 1) && lastCells < headerCells then
          add(
            FormatRule.TableColumns,
            lineStarts(b + 1),
            next.length,
            "the table row above is split across lines: this continuation renders as a paragraph under the table, " +
              "and the row is short of cells",
            severity = Some(FormatSeverity.Error)
          )

    // ── inline markup the renderer left literal ────────────────────────────────────────────────

    private def inlineLiterals(): Unit =
      val reported = mutable.Set.empty[Int] // lines with an emphasis problem already reported
      var i = 0
      while i < text.length do
        val ch = text.charAt(i)
        if isLiteral(i) then
          ch match
            case '*' | '_' if i + 1 < text.length && text.charAt(i + 1) == ch && isLiteral(i + 1) =>
              val run = text.indexWhere(_ != ch, i) match { case -1 => text.length - i; case j => j - i }
              val l = lineOf(i)
              if !reported(l) && !(ch == '_' && intraword(i, run)) then
                reported += l
                emphasisProblem(i, ch.toString * math.min(run, 3), l)
              i += run - 1
            case '*' if strayStar(i) =>
              val l = lineOf(i)
              if !reported(l) then
                reported += l
                add(
                  FormatRule.Emphasis,
                  i,
                  1,
                  "a stray `*` next to bold/italic text: the asterisks do not balance (`***a**`, or a starred form " +
                    "inside bold), so one shows outside the bold — for a bold starred form write `**\\*a**`"
                )
            case '_' if !intraword(i, 1) && underscoreFlank(i) =>
              val l = lineOf(i)
              if !reported(l) && !inUrl(i) then
                reported += l
                add(
                  FormatRule.Emphasis,
                  i,
                  1,
                  "an `_` that opens or closes italics but has no partner — it shows literally"
                )
            case '`' =>
              val ln = lineText(lineOf(i))
              val curly = ln.drop(i - lineStarts(lineOf(i)) + 1).exists(c => c == '’' || c == '‘' || c == '´')
              add(
                FormatRule.CodeSpan,
                i,
                1,
                if curly then "a code span opened with ` but closed with a curly quote — the backtick shows literally"
                else "an unpaired backtick — it shows literally and the code span never starts"
              )
            case '~' if i + 1 < text.length && text.charAt(i + 1) == '~' && isLiteral(i + 1) =>
              val close = text.indexOf("~~", i + 2)
              if close > i + 2 && lineOf(close) == lineOf(i) then
                add(
                  FormatRule.Strikethrough,
                  i,
                  close - i + 2,
                  "`~~…~~` — the renderer has no strikethrough, so the tildes show around the text"
                )
                i = close + 1
            case '\\' if i + 1 < text.length && "ntr".contains(text.charAt(i + 1)) && !escaped(i) =>
              val c = text.charAt(i + 1)
              val (rep, what) = c match
                case 'n' => ("\n", "newline")
                case 't' => (" ", "tab")
                case _   => ("", "carriage return")
              add(
                FormatRule.LiteralEscape,
                i,
                2,
                s"a literal `\\$c` (an escaped $what from JSON) — it shows as backslash-$c",
                List(Edit(i, i + 2, rep)),
                Some(s"replace `\\$c` with a real $what")
              )
              i += 1
            case _ => ()
        i += 1

    /** Asterisks the writer meant literally — the starred reconstructions of historical linguistics, `*deiwos`, `*Manu`
      * beside `*Yemo`, `pe-re-*82` — that the renderer paired into italics. The tell: a real `*italic*` opens after a
      * space or bracket and closes before one; a pair that opens straight after a letter or `/`, or closes straight
      * before a letter, is two reconstruction markers.
      */
    private def starredForms(): Unit =
      val seen = mutable.Set.empty[Int]
      nodes.foreach {
        case e @ (_: Emphasis | _: StrongEmphasis) if !insideCode(e) =>
          val st = e.getStartOffset; val en = e.getEndOffset
          val mark = if e.isInstanceOf[StrongEmphasis] then 2 else 1
          if text.charAt(st) == '*' && en - st > 2 * mark then
            val before = if st > 0 then text.charAt(st - 1) else ' '
            val after = if en < text.length then text.charAt(en) else ' '
            val inner = text.substring(st + mark, en - mark)
            val opensOddly = before.isLetterOrDigit || before == '/' || before == '-'
            val closesOddly = after.isLetterOrDigit
            // `*a/e/o*RC`, `single-*l*`, `*eídōlon*/*sávarṇā*` are real italics of a form; what gives a
            // starred pair away is a run that crosses words (`(*Manu/*Yemo) and …`) or a span that
            // ends on the slash between two forms (`*Manu/*Yemo`)
            val runsOn = inner.exists(_.isWhitespace) && (opensOddly || closesOddly)
            val between = closesOddly && !inner.last.isLetterOrDigit
            val l = lineOf(st)
            if (runsOn || between) && inner.headOption.exists(_.isLetterOrDigit) && seen.add(l) then
              add(
                FormatRule.Emphasis,
                st,
                en - st,
                "asterisks meant literally (a starred form like *deiwos) pair up into italics here, so the text in " +
                  "between turns italic and the asterisks vanish — write them as `\\*`"
              )
        case _ => ()
      }

    /** `foo__bar` / `snake_case`: an underscore with a letter or digit on both sides never emphasises. */
    private def intraword(i: com.histmersion.server.MarkdownLint.Scan.IntrawordI, run: com.histmersion.server.MarkdownLint.Scan.IntrawordRun): com.histmersion.server.MarkdownLint.Scan.IntrawordResult =
      i > 0 && i + run < text.length && text.charAt(i - 1).isLetterOrDigit && text.charAt(i + run).isLetterOrDigit

    /** A lone `_` at the edge of a word — where italics would open (`_word`) or close (`word_`). */
    private def underscoreFlank(i: com.histmersion.server.MarkdownLint.Scan.UnderscoreFlankI): com.histmersion.server.MarkdownLint.Scan.UnderscoreFlankResult =
      val before = if i > 0 then text.charAt(i - 1) else ' '
      val after = if i + 1 < text.length then text.charAt(i + 1) else ' '
      val opens = !before.isLetterOrDigit && before != '_' && before != '\\' && after.isLetterOrDigit
      val closes = (before.isLetterOrDigit || ".,;:!?)".contains(before)) && !after.isLetterOrDigit && after != '_'
      opens || closes

    private def inUrl(i: com.histmersion.server.MarkdownLint.Scan.InUrlI): com.histmersion.server.MarkdownLint.Scan.InUrlResult =
      val l = lineOf(i)
      val ln = lineText(l)
      val c = i - lineStarts(l)
      UrlText.findAllMatchIn(ln).exists(m => c >= m.start && c < m.end)

    /** A literal `*` touching an emphasis node: what is left of `***a**` or `**a***`. */
    private def strayStar(i: com.histmersion.server.MarkdownLint.Scan.StrayStarI): com.histmersion.server.MarkdownLint.Scan.StrayStarResult =
      nodes.exists {
        case e @ (_: Emphasis | _: StrongEmphasis) => e.getStartOffset == i + 1 || e.getEndOffset == i
        case _                                     => false
      }

    private def emphasisProblem(i: com.histmersion.server.MarkdownLint.Scan.EmphasisProblemI, mark: com.histmersion.server.MarkdownLint.Scan.EmphasisProblemMark, l: com.histmersion.server.MarkdownLint.Scan.EmphasisProblemL): Unit =
      val ln = lineText(l)
      val col = i - lineStarts(l)
      val q = java.util.regex.Pattern.quote(mark.take(2))
      // `** text**` / `**text **`: a pair with a space just inside — the one shape `--fix` repairs
      val spaced = s"""$q([ \\t]+)([^*_\\n]*?[^ \\t*_])([ \\t]*)$q|$q([^ \\t*_][^*_\\n]*?)([ \\t]+)$q""".r
      spaced.findAllMatchIn(ln).find(m => m.start <= col && col < m.end) match
        case Some(m) =>
          val inner = Option(m.group(2)).getOrElse(m.group(4))
          val st = lineStarts(l) + m.start
          add(
            FormatRule.Emphasis,
            st,
            m.end - m.start,
            s"`${mark.take(2)}` with a space just inside it does not make bold/italic text — it shows literally",
            List(Edit(st, lineStarts(l) + m.end, s"${mark.take(2)}$inner${mark.take(2)}")),
            Some("remove the spaces inside the markers")
          )
        case None =>
          val count = java.util.regex.Pattern.compile(q).matcher(ln).results().count()
          val why =
            if count % 2 == 1 then s"this line has an odd number of `${mark.take(2)}`"
            else s"`${mark.take(2)}` cannot pair here (next to punctuation or space on the wrong side)"
          add(FormatRule.Emphasis, i, mark.length, s"$why — the markers show literally instead of bold")

    // ── links and images ───────────────────────────────────────────────────────────────────────

    private def linkNodes(): Unit = nodes.foreach {
      case r: LinkRef if !insideCode(r) =>
        val ref = r.getReference.toString
        if ref.startsWith("^") then
          val label = ref.drop(1)
          val isDef = r.getNext match
            case t: Text => t.getChars.toString.startsWith(":")
            case _       => false
          val other =
            if isDef then masked.contains(s"[^$label]") && masked.indexOf(s"[^$label]") != r.getStartOffset
            else masked.contains(s"[^$label]:")
          val pairing =
            if isDef && !other then " (and nothing in the text refers to it)"
            else if !isDef && !other then " (and its note is never defined)"
            else ""
          add(
            FormatRule.Footnote,
            r.getStartOffset,
            r.getEndOffset - r.getStartOffset,
            s"a footnote ${if isDef then "definition" else "marker"} `[^$label]`$pairing — the renderer has no footnotes, " +
              "so it shows literally; put the note in the sentence"
          )
        else
          r.getNext match
            case t: Text if !r.isDefined =>
              val after = t.getChars.toString
              val sp = """^(\s+)\((?:https?://|www\.|/|#|\.{1,2}/)[^)\s]*\)""".r
              val open = """^\((?:https?://|www\.|/|#|\.{1,2}/)[^)\s]*(?:\s|$)""".r
              sp.findFirstMatchIn(after) match
                case Some(m) =>
                  add(
                    FormatRule.LinkSyntax,
                    r.getStartOffset,
                    r.getEndOffset - r.getStartOffset + m.end,
                    "a space between `]` and `(` — this is not a link; the brackets and the URL show as text",
                    List(Edit(t.getStartOffset, t.getStartOffset + m.group(1).length, "")),
                    Some("remove the space between ] and (")
                  )
                case None =>
                  if open.findFirstIn(after).isDefined || (after.startsWith("(") && !after.contains(")")) then
                    add(
                      FormatRule.LinkSyntax,
                      r.getStartOffset,
                      r.getEndOffset - r.getStartOffset + 20,
                      "a link whose `(target)` is not closed (or contains a space) — the brackets and URL show as text"
                    )
            case _ => ()
      case lk: Link if !insideCode(lk) =>
        val url = lk.getUrl.toString
        if lk.getText.toString.trim.isEmpty && !lk.hasChildren then
          add(
            FormatRule.LinkEmpty,
            lk.getStartOffset,
            lk.getEndOffset - lk.getStartOffset,
            "a link with no text — nothing to click shows on the page"
          )
        else if url.trim.isEmpty then
          add(
            FormatRule.LinkEmpty,
            lk.getStartOffset,
            lk.getEndOffset - lk.getStartOffset,
            "a link with an empty `()` target — it goes nowhere"
          )
        else
          WikiPathUrl.findFirstMatchIn(url).foreach { m =>
            val target = Option(m.group(1)).orElse(Option(m.group(2))).map(_.toLowerCase).getOrElse("")
            val label = lk.getText.toString.trim
            val wiki =
              if label.isEmpty || label.toLowerCase == target || Label.display(target) == label.toLowerCase
              then s"[[$target]]"
              else s"[[$target|${label.replace("|", "-").replace("]", ")").replace("[", "(")}]]"
            val ok = Article.Links.normalize(target).nonEmpty
            add(
              FormatRule.WikiPathLink,
              lk.getStartOffset,
              lk.getEndOffset - lk.getStartOffset,
              s"a Markdown link to a wiki page (`$url`) — it does not reach the link graph or the cited list, and the path breaks off the server; write $wiki",
              if ok then List(Edit(lk.getStartOffset, lk.getEndOffset, wiki)) else Nil,
              Some(s"write $wiki")
            )
          }
      case im: Image if !insideCode(im) =>
        val url = im.getUrl.toString.trim
        if url.isEmpty then
          add(
            FormatRule.Image,
            im.getStartOffset,
            im.getEndOffset - im.getStartOffset,
            "an image with no source — it renders as a broken image"
          )
        else if LocalPath.findFirstIn(url).isDefined && !(field == FormatField.Notes && url.contains("figures/")) then
          add(
            FormatRule.Image,
            im.getStartOffset,
            im.getEndOffset - im.getStartOffset,
            s"an image at a local path (`${url.take(40)}`) — the server cannot serve it, so it renders broken; attach it as an asset"
          )
      case _ => ()
    }

    // ── raw HTML ───────────────────────────────────────────────────────────────────────────────

    private val Tag = """<(/?)([A-Za-z][A-Za-z0-9:-]*)\b[^<>]*?(/?)>""".r

    private def htmlTags(): Unit =
      val open = mutable.ListBuffer.empty[(String, Int)]
      val unknown = mutable.Set.empty[String] // each unknown element once: `<jats:p>` comes in dozens
      nodes.foreach {
        case h @ (_: HtmlInline | _: HtmlBlock) => // comments are their own node classes
          Tag.findAllMatchIn(h.getChars.toString).foreach { m =>
            val name = m.group(2).toLowerCase
            val off = h.getStartOffset + m.start
            if name == "insert" then () // a placeholder, reported as one
            else if !KnownTags.contains(name) then
              if m.group(1).isEmpty && unknown.add(name) then
                add(
                  FormatRule.HtmlTag,
                  off,
                  m.matched.length,
                  s"`<$name>` is not an HTML element (XML from a publisher's abstract?) — the browser drops the tag and keeps its text; write the text without it"
                )
            else if m.group(1).nonEmpty then
              open.lastIndexWhere(_._1 == name) match
                case -1 => add(FormatRule.HtmlTag, off, m.matched.length, s"`</$name>` closes nothing")
                case k  => open.remove(k)
            else if m.group(3).isEmpty && !VoidTags.contains(name) then open += ((name, off))
          }
        case _ => ()
      }
      open.foreach { (name, off) =>
        val block = BlockTags.contains(name)
        add(
          FormatRule.HtmlTag,
          off,
          name.length + 2,
          if block then s"`<$name>` is never closed — everything after it is inside it"
          else s"`<$name>` is never closed — the rest of the paragraph takes its style",
          severity = Some(if block then FormatSeverity.Error else FormatSeverity.Warning)
        )
      }

    // ── headings ───────────────────────────────────────────────────────────────────────────────

    private def headings(): Unit =
      val hs = nodes.collect { case h: Heading => h }
      // `##Heading`: a paragraph line that wanted to be a heading
      nodes.foreach {
        case p: Paragraph if !insideCode(p) =>
          val l0 = lineOf(p.getStartOffset)
          val l1 = lineOf(math.max(p.getStartOffset, p.getEndOffset - 1))
          (l0 to l1).foreach { l =>
            val t = lineText(l)
            """^( {0,3})(#{1,6})([^\s#])""".r.findFirstMatchIn(t).foreach { m =>
              val hashes = m.group(2)
              val rest = t.drop(m.end - 1)
              val looksHeading = hashes.length >= 2 || (rest.headOption.exists(_.isUpper) && rest.contains(' '))
              if looksHeading && !rest.headOption.exists(_.isDigit) then
                val at = lineStarts(l) + m.group(1).length + hashes.length
                add(
                  FormatRule.HeadingSyntax,
                  lineStarts(l),
                  t.length,
                  s"`$hashes` with no space after it is not a heading — it shows as `$hashes${rest.takeWhile(!_.isWhitespace)}` text",
                  List(Edit(at, at, " ")),
                  Some(s"insert a space after `$hashes`")
                )
            }
          }
        case _ => ()
      }
      hs.foreach { h =>
        val txt = h.getText.toString
        if h.isSetextHeading then
          val l = lineOf(math.max(h.getStartOffset, h.getEndOffset - 1))
          val under = lineText(l).trim
          if frontmatterEnd.exists(_ >= lineStarts(l)) then () // the closing `---` of a pasted frontmatter block
          else if under.startsWith("-") then
            add(
              FormatRule.SetextHeading,
              lineStarts(l),
              lineText(l).length,
              s"`$under` right under a line of text turns that text (\"${txt.take(40)}\") into a heading instead of drawing a rule",
              List(Edit(lineStarts(l), lineStarts(l), "\n")),
              Some("insert a blank line before the rule")
            )
          else
            add(
              FormatRule.SetextHeading,
              lineStarts(l),
              lineText(l).length,
              s"`$under` under a line of text makes it a top-level heading — write `# ${txt.take(30)}` if that is meant",
              severity = Some(FormatSeverity.Warning)
            )
        else if h.isAtxHeading && txt.endsWith("#") && !txt.endsWith("\\#") then
          val st = h.getText.getStartOffset
          val trimmed = txt.reverse.dropWhile(_ == '#').reverse.stripTrailing
          add(
            FormatRule.HeadingSyntax,
            h.getStartOffset,
            h.getEndOffset - h.getStartOffset,
            "a `#` stuck to the end of the heading text shows as part of the heading",
            List(Edit(st, h.getText.getEndOffset, trimmed)),
            Some("remove the trailing #")
          )
        if txt.trim.isEmpty then
          add(
            FormatRule.HeadingLevel,
            h.getStartOffset,
            h.getEndOffset - h.getStartOffset,
            "an empty heading",
            severity = Some(FormatSeverity.Warning)
          )
        if h.getAncestorOfType(classOf[ListItem]) != null then
          add(
            FormatRule.HeadingLevel,
            h.getStartOffset,
            h.getEndOffset - h.getStartOffset,
            "a heading inside a list item — it renders inside the bullet, not as a section"
          )
      }
      // levels, H1s, duplicates — over the top-level structure
      val top = hs.filter(h => h.getParent.isInstanceOf[Document] && !h.isSetextHeading)
      val h1s = top.filter(_.getLevel == 1)
      val extraH1 = if ctx.article then h1s.drop(1) else h1s.drop(1)
      extraH1.foreach { h =>
        add(
          FormatRule.HeadingLevel,
          h.getStartOffset,
          h.getEndOffset - h.getStartOffset,
          if ctx.article then
            "a second `#` title: the page shows the first as the title and this one as a second, equally large title — use `##`"
          else "a second `#` heading — use `##` for sections"
        )
      }
      top.sliding(2).foreach {
        case List(a, b) if b.getLevel > a.getLevel + 1 =>
          add(
            FormatRule.HeadingLevel,
            b.getStartOffset,
            b.getEndOffset - b.getStartOffset,
            s"jumps from a level-${a.getLevel} heading to level ${b.getLevel} — a level is skipped"
          )
        case _ => ()
      }
      val seen = mutable.Map.empty[(Int, String), Heading]
      top.foreach { h =>
        val key = (h.getLevel, h.getText.toString.trim.toLowerCase.replaceAll("""\s+""", " "))
        if key._2.nonEmpty then
          seen.get(key) match
            case Some(first) =>
              add(
                FormatRule.DuplicateHeading,
                h.getStartOffset,
                h.getEndOffset - h.getStartOffset,
                s"`${"#" * h.getLevel} ${h.getText.toString.trim}` already appears at line ${lineOf(first.getStartOffset) + 1} — the section is written twice"
              )
            case None => seen(key) = h
        if ctx.article && field == FormatField.Body && """^sources\s+cited\b""".r.findFirstIn(key._2).isDefined then
          add(
            FormatRule.SourcesCitedSection,
            h.getStartOffset,
            h.getEndOffset - h.getStartOffset,
            "the page generates \"Sources cited\" from the [[citekey]] links — a hand-written one duplicates it and goes stale"
          )
      }

    // ── lists ──────────────────────────────────────────────────────────────────────────────────

    private val Bullet = """^( {0,3})([-*+])\s+\S""".r
    private val Ordered = """^( {0,3})(\d{1,3})[.)]\s+\S""".r

    private def lists(): Unit =
      // list items the parser kept inside a paragraph: `Para\n2. two\n3. three`, `Para\n+ x` …
      nodes.foreach {
        case p: Paragraph if !insideCode(p) =>
          val l0 = lineOf(p.getStartOffset)
          val l1 = lineOf(math.max(p.getStartOffset, p.getEndOffset - 1))
          val items = (l0 + 1 to l1).filter { l =>
            val t = maskedLine(l)
            Bullet.findFirstIn(t).isDefined || Ordered.findFirstIn(t).isDefined
          }
          val firstItem = items.headOption
          firstItem.foreach { l =>
            val numbered = Ordered.findFirstIn(maskedLine(l)).isDefined
            // a wrapped line that happens to start with a year and a full stop ("…in\n1200. Later") is
            // not a list: a numbered run needs two items
            if !numbered || items.size >= 2 then
              add(
                FormatRule.ListGlued,
                lineStarts(l),
                lineText(l).length,
                "list items right under a paragraph line with no blank line between — the renderer runs them into the paragraph as one line of text" +
                  (if numbered then " (a numbered list may only interrupt a paragraph when it starts at 1)" else ""),
                List(Edit(lineStarts(l), lineStarts(l), "\n")),
                Some("insert a blank line before the list")
              )
          }
        case _ => ()
      }
      // a bullet that changes marker starts a new list
      nodes.foreach {
        case b: BulletList =>
          b.getNext match
            case n: BulletList if n.getOpeningMarker != b.getOpeningMarker =>
              add(
                FormatRule.ListMarkers,
                n.getStartOffset,
                2,
                s"the bullet changes from `${b.getOpeningMarker}` to `${n.getOpeningMarker}` — the renderer starts a second list here, with a gap"
              )
            case _ => ()
        case o: OrderedList =>
          val nums = o.getChildren.asScala.toList.collect { case it: OrderedListItem =>
            it.getOpeningMarker.toString.takeWhile(_.isDigit).toIntOption.getOrElse(0)
          }
          val lazyOnes = nums.distinct.size == 1
          val expected = nums.indices.map(_ + nums.headOption.getOrElse(1)).toList
          if !lazyOnes && nums != expected then
            val bad = nums.zip(expected).indexWhere((a, b) => a != b)
            val it = o.getChildren.asScala.toList.collect { case it: OrderedListItem => it }(bad)
            add(
              FormatRule.ListMarkers,
              it.getStartOffset,
              it.getOpeningMarker.length,
              s"the list is numbered ${nums.take(6).mkString(", ")}${
                  if nums.size > 6 then ", …" else ""
                } but renders as ${expected.take(6).mkString(", ")}${
                  if nums.size > 6 then ", …" else ""
                } — the written numbers are ignored"
            )
        case _ => ()
      }

    // ── line checks: chatter, placeholders, math, entities, bare URLs and citekeys ──────────────

    private def lineChecks(): Unit =
      var dollars = List.empty[Int]
      (0 until nLines).foreach { l =>
        val t = lineText(l)
        val mt = maskedLine(l)
        val st = lineStarts(l)
        if t.trim.nonEmpty && !lineInCode(l) then
          ChatterLine.findFirstMatchIn(t).foreach { m =>
            add(
              FormatRule.AgentChatter,
              st,
              t.length,
              "a line addressed to whoever asked for the text, not part of it — delete it"
            )
          }
          Placeholders.findAllMatchIn(t).filterNot(m => code.get(st + m.start)).foreach { m =>
            add(
              FormatRule.Placeholder,
              st + m.start,
              m.end - m.start,
              s"placeholder `${m.matched.take(40)}` left in the text — fill it or move the gap to an *Unresolved* / *Open questions* section"
            )
          }
          """\$\$""".r
            .findAllMatchIn(t)
            .filterNot(m => code.get(st + m.start))
            .foreach(m => dollars = dollars :+ (st + m.start))
          """\\\((.+?)\\\)""".r
            .findAllMatchIn(t)
            .filterNot(m => code.get(st + m.start))
            .filter(m => m.group(1).exists(c => c == '\\' || c == '^' || c == '_' || c == '='))
            .foreach { m =>
              add(
                FormatRule.Math,
                st + m.start,
                m.end - m.start,
                "`\\(…\\)` math — the renderer does not typeset it; the backslashes vanish and the TeX shows raw"
              )
            }
          """&amp;(amp|lt|gt|quot|apos|nbsp|#\d+|#x[0-9a-fA-F]+);""".r
            .findAllMatchIn(t)
            .filterNot(m => code.get(st + m.start))
            .foreach { m =>
              add(
                FormatRule.HtmlEntity,
                st + m.start,
                m.end - m.start,
                s"`${m.matched}` is escaped twice — the page shows `&${m.group(1)};` instead of the character",
                List(Edit(st + m.start, st + m.end, s"&${m.group(1)};")),
                Some(s"write `&${m.group(1)};`")
              )
            }
          UrlText.findAllMatchIn(mt).foreach { m =>
            val o = st + m.start
            val url = m.matched.reverse.dropWhile(c => ".,;:!?".contains(c)).reverse
            val prev = if m.start > 0 then mt.charAt(m.start - 1) else ' '
            if isLiteral(o) && prev != '<' && prev != '(' && prev != '[' && prev != '"' && prev != '\'' then
              add(
                FormatRule.BareUrl,
                o,
                url.length,
                "a bare URL — the renderer does not make it a link, so it cannot be clicked",
                List(Edit(o, o + url.length, s"<$url>")),
                Some("wrap it in <…>")
              )
          }
          """<(https?://[^>\s]+)(?=\s|$)""".r.findAllMatchIn(mt).filter(m => isLiteral(st + m.start)).foreach { m =>
            add(
              FormatRule.LinkSyntax,
              st + m.start,
              m.end - m.start,
              "`<url` without its closing `>` — it is not a link; the bracket and URL show as text",
              List(Edit(st + m.end, st + m.end, ">")),
              Some("close it with >")
            )
          }
          // the `_Method:_` line lists the filter's citekeys on purpose — the writing guide's convention
          if !MethodLine.matches(t) then
            ctx.isCitekey.foreach { known =>
              val bare = CitekeyTok.findAllMatchIn(mt).filter(m => isLiteral(st + m.start) && known(m.group(1))).toList
              bare.headOption.foreach { m =>
                val names = bare.map(_.group(1)).distinct
                add(
                  FormatRule.BareCitekey,
                  st + m.start,
                  m.end - m.start,
                  s"${names.take(3).map(n => s"`$n`").mkString(", ")}${
                      if names.size > 3 then s" (+${names.size - 3} more)" else ""
                    } " +
                    s"${
                        if names.size == 1 then "is a citekey" else "are citekeys"
                      } written as plain text — not a link and not counted as cited; " +
                    s"write [[${names.head}]]"
                )
              }
            }
      }
      if dollars.nonEmpty then
        add(
          FormatRule.Math,
          dollars.head,
          2,
          if dollars.size % 2 == 1 then
            s"${dollars.size} `$$$$` math delimiter(s) — an odd number, and the renderer does not typeset math anyway"
          else "`$$…$$` math — the renderer does not typeset it; the TeX shows raw"
        )

    // ── wikilinks ──────────────────────────────────────────────────────────────────────────────

    private def wikilinks(): Unit =
      val unresolved = mutable.Set.empty[String]
      links.filterNot(o => code.get(o.start)).foreach { o =>
        val raw = text.substring(o.start, o.end)
        if o.embed then
          add(
            FormatRule.Wikilink,
            o.start,
            raw.length,
            "an Obsidian embed `![[…]]` — the page shows it as literal text; link it with [[…]] or attach the file as an asset"
          )
        else
          o.target match
            case None =>
              val inner = o.raw.takeWhile(_ != '|').trim
              add(
                FormatRule.Wikilink,
                o.start,
                raw.length,
                if inner.startsWith("#") || inner.isEmpty then
                  s"`$raw` links to nothing (an anchor alone) — it shows literally with its brackets"
                else if inner.contains('/') then
                  s"`$raw` names a file path, not a page — it shows literally with its brackets"
                else s"`$raw` is not a slug or citekey (lower case, no spaces) — it shows literally with its brackets"
              )
            case Some(t) =>
              if o.escaped then
                val fixed = raw.replace("\\[", "[").replace("\\]", "]")
                add(
                  FormatRule.Wikilink,
                  o.start,
                  raw.length,
                  "an escaped wikilink `\\[\\[…\\]\\]` — the page un-escapes it, but every other reader (Obsidian, the exported wiki) shows the brackets as text",
                  List(Edit(o.start, o.end, fixed)),
                  Some("remove the backslashes"),
                  Some(FormatSeverity.Warning)
                )
              val name = o.raw.takeWhile(c => c != '|' && c != '#')
              if name != name.trim && !o.escaped then
                val inner = name.trim + o.raw.drop(name.length)
                add(
                  FormatRule.Wikilink,
                  o.start,
                  raw.length,
                  s"spaces inside `$raw` — the page trims them, other readers look for a page named \"$name\"",
                  List(Edit(o.start, o.end, s"[[$inner]]")),
                  Some(s"write [[${name.trim}]]"),
                  Some(FormatSeverity.Warning)
                )
              ctx.resolves.foreach { r =>
                if !r(t) && unresolved.add(t) then
                  add(
                    FormatRule.WikilinkUnresolved,
                    o.start,
                    raw.length,
                    s"[[$t]] names no source and no article — it renders as plain text; check the citekey (`histmersion search`) or the slug"
                  )
              }
      }
      // one bracket short: `[[x]` and `[x]]` (the full form was matched above, so these are leftovers)
      val inLink = (i: Int) => links.exists(o => i >= o.start && i < o.end)
      """(?<![\[\\!])\[\[([a-z0-9][a-z0-9_.-]*)\](?!\])""".r
        .findAllMatchIn(text)
        .filter(m => !inLink(m.start) && !code.get(m.start))
        .foreach { m =>
          add(
            FormatRule.Wikilink,
            m.start,
            m.end - m.start,
            s"`${m.matched}` is one `]` short — it shows as text instead of a link",
            List(Edit(m.end, m.end, "]")),
            Some(s"write [[${m.group(1)}]]")
          )
        }
      """(?<![\[\\])\[([a-z][a-z0-9_.-]*(?:_|\d)[a-z0-9_.-]*)\]\](?!\])""".r
        .findAllMatchIn(text)
        .filter(m => !inLink(m.start) && !code.get(m.start))
        .foreach { m =>
          add(
            FormatRule.Wikilink,
            m.start,
            m.end - m.start,
            s"`${m.matched}` is one `[` short — it shows as text instead of a link",
            List(Edit(m.start, m.start, "[")),
            Some(s"write [[${m.group(1)}]]")
          )
        }

  object Scan:
    type TablesRenderedPart1 = TablesRenderedPart1.T
    object TablesRenderedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TablesRenderedPart2 = TablesRenderedPart2.T
    object TablesRenderedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TablesCoveredByL = TablesCoveredByL.T
    object TablesCoveredByL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type LineStarts = LineStarts.T
    object LineStarts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type LineOfResult = LineOfResult.T
    object LineOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LineOfOff = LineOfOff.T
    object LineOfOff extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LineTextResult = LineTextResult.T
    object LineTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LineTextL = LineTextL.T
    object LineTextL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExcerptAtResult = ExcerptAtResult.T
    object ExcerptAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExcerptAtOff = ExcerptAtOff.T
    object ExcerptAtOff extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExcerptAtLen = ExcerptAtLen.T
    object ExcerptAtLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddOff = AddOff.T
    object AddOff extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddLen = AddLen.T
    object AddLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddMessage = AddMessage.T
    object AddMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddFix = AddFix.T
    object AddFix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InsideCodeResult = InsideCodeResult.T
    object InsideCodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsLiteralResult = IsLiteralResult.T
    object IsLiteralResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsLiteralI = IsLiteralI.T
    object IsLiteralI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EscapedResult = EscapedResult.T
    object EscapedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EscapedI = EscapedI.T
    object EscapedI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LineInCodeResult = LineInCodeResult.T
    object LineInCodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LineInCodeL = LineInCodeL.T
    object LineInCodeL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wrapped = Wrapped.T
    object Wrapped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FrontmatterEnd = FrontmatterEnd.T
    object FrontmatterEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CellsResult = CellsResult.T
    object CellsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CellsLine = CellsLine.T
    object CellsLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaskedLineResult = MaskedLineResult.T
    object MaskedLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaskedLineL = MaskedLineL.T
    object MaskedLineL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PipeRowResult = PipeRowResult.T
    object PipeRowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PipeRowL = PipeRowL.T
    object PipeRowL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotRenderedStart = NotRenderedStart.T
    object NotRenderedStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotRenderedEnd = NotRenderedEnd.T
    object NotRenderedEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenderedTableA = RenderedTableA.T
    object RenderedTableA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenderedTableB = RenderedTableB.T
    object RenderedTableB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntrawordResult = IntrawordResult.T
    object IntrawordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntrawordI = IntrawordI.T
    object IntrawordI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntrawordRun = IntrawordRun.T
    object IntrawordRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnderscoreFlankResult = UnderscoreFlankResult.T
    object UnderscoreFlankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnderscoreFlankI = UnderscoreFlankI.T
    object UnderscoreFlankI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InUrlResult = InUrlResult.T
    object InUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InUrlI = InUrlI.T
    object InUrlI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrayStarResult = StrayStarResult.T
    object StrayStarResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrayStarI = StrayStarI.T
    object StrayStarI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EmphasisProblemI = EmphasisProblemI.T
    object EmphasisProblemI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EmphasisProblemMark = EmphasisProblemMark.T
    object EmphasisProblemMark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EmphasisProblemL = EmphasisProblemL.T
    object EmphasisProblemL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
