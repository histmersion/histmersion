package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.values.SourceStatus

/** ARTICLES — the compiled wiki (plan §5.2 stream 2.7).
  *
  * A [[Source]] is what the corpus says; `Source.notes` is what an agent compiled about ONE source. An **article** is
  * the other axis: prose compiled ACROSS sources — the concept / myth / culture / region / timeline / report pages of
  * the `wiki/concepts`, `wiki/myths`, … directories. The database is the store of record; `histmersion export` writes
  * each article back to `<kind>/<slug>.md` for Obsidian and git, and `ArticleNote` is the exact inverse pair that makes
  * that round-trip byte-stable.
  *
  * Everything an article points at is an Obsidian wikilink in its body — `[[other_slug]]` for an article, `[[citekey]]`
  * for a source — so the link graph never has to be maintained by hand: [[ArticleLinks]] extracts it from the body on
  * every write.
  *
  * Everything here is PURE (parse, render, link extraction, patch merge); `db.ArticleRepo` persists the results and
  * `server` renders them.
  */

/** One compiled article. The domain model: `db` stores it, `ArticleNote` renders/parses it. */
final case class Article(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    period: Option[Period] = None,
    /** Opaque last-write token (`updated_at`); `None` when the value did not come from the store. */
    updatedAt: Option[String] = None,
    /** The filter it is compiled from (#958): its Not used list and Method note are generated from it. */
    filter: Option[ArticleFilter] = None,
    /** Why each source of the filter that the body does not cite is left out, by citekey. */
    reasons: Map[String, String] = Map.empty
)

/** One `[[…]]` link out of an article, classified at write time. */
final case class ArticleLink(targetKind: String, target: String)

/** The wikilink graph, as pure functions over an article body.
  *
  * A wikilink is `[[target]]`, `[[target|label]]` or `[[target#anchor]]`. The existing wiki also contains
  * BACKSLASH-ESCAPED links (`\[\[haak2015_massive\]\]`, written so a Markdown viewer shows the brackets literally) and
  * IMAGE EMBEDS (`![[map.png]]`); the first are real links and are matched, the second are not links at all and are
  * skipped, as are pure in-page anchors (`[[#Sources|…]]`).
  */
object ArticleLinks:

  val TargetArticle = "article"
  val TargetSource = "source"

  /** The citekey shape the whole project uses: `lastname2015_word`, or `lastname2015` alone (11 live keys have no
    * underscore — a regex that demanded one turned `[[knapp2016]]` into a dead article link). A `[[x]]` is a SOURCE
    * link only when x has this shape AND a source with that citekey exists — otherwise it is an article slug, even if
    * the article does not exist yet (a wiki links forward).
    */
  private val Citekey =
    """^(?=.*(?:_|\d{4}))[a-z][a-z0-9_]*$""".r // `haak2015_massive`, `knapp2016`, `duhoux_linearb1` — not `nodigits`

  /** A slug-shaped target. Anything else (`[[Not A Key]]`, a URL, a filename) is not a link we own, so it is left alone
    * in the text and never reaches `article_links`.
    */
  private val SlugShape = """^[a-z0-9][a-z0-9_.-]*$""".r

  /** `(!)? \[\[ target \]\]`, tolerating a backslash before each bracket. Non-greedy so two links on one line do not
    * merge into one match.
    */
  private val Link = """(!?)\\?\[\\?\[([^\[\]\n]+?)\\?\]\\?\]""".r

  def isCitekeyShaped(s: String): Boolean = Citekey.matches(s)

  /** One `[[…]]` as the renderer finds it: `[start, end)` in the text, the raw inside, whether it is an image embed
    * (`![[x]]`) or written with escaped brackets (`\[\[x\]\]`), and its normalised target — `None` when the renderer
    * leaves it literal (a title with spaces, an anchor-only link).
    */
  final case class Occurrence(
      start: Int,
      end: Int,
      raw: String,
      embed: Boolean,
      escaped: Boolean,
      target: Option[String]
  )

  /** Every wikilink in `md`, in order — the same matches [[rewriteLinks]] replaces, so the formatting lint sees exactly
    * the links the page will draw.
    */
  def occurrences(md: String): List[Occurrence] =
    Link
      .findAllMatchIn(md)
      .map { m =>
        val raw = m.group(2)
        Occurrence(
          m.start,
          m.end,
          raw,
          m.group(1).nonEmpty,
          m.matched.contains('\\'),
          normalize(raw.takeWhile(_ != '|'))
        )
      }
      .toList

  /** The raw target of one wikilink body: strip the `|label`, strip the `#anchor`, trim, lower-case. `None` when what
    * is left is not a slug we can resolve (an anchor-only link, a title with spaces, an image file).
    */
  def normalize(raw: String): Option[String] =
    val noLabel = raw.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(t)

  /** Every distinct link target in the body, in first-appearance order. Image embeds are skipped. */
  def targets(body: String): List[String] =
    Link
      .findAllMatchIn(body)
      .filter(_.group(1).isEmpty) // `![[x]]` is an embed, not a link
      .flatMap(m => normalize(m.group(2)))
      .toList
      .distinct

  /** Classify the extracted targets. `isSource` answers "is there a source with this citekey?" — the repo supplies it
    * from one query, so this stays pure and unit-testable.
    */
  def classify(targets: List[String], isSource: String => Boolean): List[ArticleLink] =
    targets.map { t =>
      if isCitekeyShaped(t) && isSource(t) then ArticleLink(TargetSource, t)
      else ArticleLink(TargetArticle, t)
    }

  /** Extract + classify in one step. */
  def links(body: String, isSource: String => Boolean): List[ArticleLink] =
    classify(targets(body), isSource)

  /** Where a `[[target]]` points, given what the corpus actually holds — the whole resolution policy, as one pure
    * function.
    *
    *   - `citekeyOf` resolves an ALIAS to a full citekey. The wiki writes `[[allentoft2015]]` and `[[knapp2016]]` where
    *     the corpus stores `allentoft2015_population` / `knapp2016_seven`, exactly the short form
    *     `POST /api/edges/resolve` unifies for edges. It is asked FIRST: a citekey-shaped target is a source reference,
    *     and defaulting it to an article slug (which is what used to happen) turns every citation in every compiled
    *     article into a dead link.
    *   - Otherwise a compiled article with that slug.
    *   - Otherwise `None` — NOT a link. A wiki links forward, so an article slug that does not exist yet is a
    *     legitimate thing to write, but rendering it as a blue link to a 404 is worse than rendering it as text.
    */
  def hrefFor(target: String, citekeyOf: String => Option[String], isArticle: String => Boolean): Option[String] =
    citekeyOf(target)
      .map(ck => s"/source/$ck")
      .orElse(Option.when(isArticle(target))(s"/article/$target"))

  /** Rewrite every wikilink as a Markdown link before the body is handed to flexmark. `href` decides where one target
    * points (see [[hrefFor]]); `None` renders the label as plain text carrying `title="unresolved link"`, so a reader
    * can tell "nothing answers to this name" from "I have not clicked it yet". The label survives (`[[x|the X
    * paper]]`), the `#anchor` is dropped (a rendered article has its own heading ids), escaped links are un-escaped,
    * and image embeds and non-slug brackets are left untouched.
    *
    * `displayLabel` (optional) provides a human-readable label for a target (e.g. "Title, Author, Year" for a source
    * citekey). Used only when the wikilink has no explicit `|label`.
    */
  def linkMarkdown(
      md: String,
      href: String => Option[String],
      displayLabel: String => Option[String] = _ => None
  ): String =
    rewriteLinks(md) { (t, explicit, written) =>
      val label = explicit.getOrElse(written)
      val text = if label.isEmpty then displayLabel(t).getOrElse(Label.display(t)) else label
      href(t) match
        case Some(h) => s"[$text]($h)"
        case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
    }

  /** The one wikilink walker: `render(target, explicitLabel, writtenText)` replaces every link we own — `target`
    * normalised, `explicitLabel` the `|label` if one was written (possibly empty: `[[x|]]`), `writtenText` the link as
    * written minus its `#anchor`. Image embeds and non-slug brackets are left literal. [[linkMarkdown]] (Markdown
    * links) and the server's HTML pages (typed anchors) both build on it.
    */
  def rewriteLinks(md: String)(render: (String, Option[String], String) => String): String =
    Link.replaceAllIn(
      md,
      m =>
        val raw = m.group(2)
        val (targetPart, explicit) = raw.indexOf('|') match
          case -1 => (raw, None)
          case i  => (raw.take(i), Some(raw.drop(i + 1).trim))
        java.util.regex.Matcher.quoteReplacement(normalize(targetPart) match
          case Some(t) if m.group(1).isEmpty => render(t, explicit, targetPart.takeWhile(_ != '#').trim)
          case _                             => m.matched)
    ) // `![[x]]` is an embed: leave it literal

  /** Point every wikilink whose normalised target `to` maps somewhere else at that new target, and change NOTHING else:
    * the `|label`, the `#anchor`, escaped brackets and the text around the link all survive byte for byte, and image
    * embeds are never touched. This is what a citekey rename runs over every article body and source note. Returns the
    * new text and how many links moved.
    */
  def retarget(md: String, to: String => Option[String]): (String, Int) =
    def moved(m: scala.util.matching.Regex.Match): Option[String] =
      if m.group(1).nonEmpty then None
      else normalize(m.group(2).takeWhile(_ != '|')).flatMap(to)
    val count = Link.findAllMatchIn(md).count(moved(_).isDefined)
    if count == 0 then (md, 0)
    else
      val out = Link.replaceAllIn(
        md,
        m =>
          java.util.regex.Matcher.quoteReplacement(moved(m) match
            case None       => m.matched
            case Some(next) =>
              val raw = m.group(2)
              val name = raw.takeWhile(c => c != '|' && c != '#')
              val lead = name.takeWhile(_.isWhitespace)
              val trail = name.reverse.takeWhile(_.isWhitespace).reverse
              val newRaw = lead + next + trail + raw.drop(name.length)
              val (s, e) = (m.start(2) - m.start, m.end(2) - m.start)
              m.matched.take(s) + newRaw + m.matched.drop(e))
      )
      (out, count)

  /** The wave-1 behaviour, kept for callers that only know which targets are sources: `[[citekey]]` →
    * `/source/citekey`, everything else → `/article/slug`, nothing unresolvable.
    */
  def resolveMarkdown(md: String, isSource: String => Boolean): String =
    linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then s"/source/$t" else s"/article/$t"))

/** Pure write rules for articles: validation, full replace, and the selective merge. The mirror of [[Write]] on the
  * source side.
  *
  * Also hosts `heading` and `lede` — pure text extractors used by [[ArticleMarkdown]] (shared) and [[ArticleNote]]
  * (JVM-only, snakeyaml-dependent).
  */
object Articles:

  /** The article's `# Title` heading, if the body opens with one. */
  def heading(body: String): Option[String] =
    body.linesIterator
      .map(_.trim)
      .find(_.nonEmpty)
      .filter(_.startsWith("# "))
      .map(_.drop(2).trim)
      .filter(_.nonEmpty)

  /** The lede: the first prose paragraph after the `# Title`, with the wiki's italic markers stripped. Used as the
    * summary when the frontmatter has none.
    */
  def lede(body: String): Option[String] =
    val lines = body.linesIterator.toList.dropWhile(_.trim.isEmpty)
    val rest0 = lines.headOption.filter(_.trim.startsWith("# ")).fold(lines)(_ => lines.tail)
    // a body that opens with its own `## Summary` (or `## Lede`/`## Abstract`) section: that is the lede
    val rest = rest0.dropWhile(_.trim.isEmpty) match
      case h :: tail if LedeHeadings.contains(h.trim.toLowerCase) => tail
      case other                                                  => other
    val para = rest.dropWhile(_.trim.isEmpty).takeWhile(_.trim.nonEmpty)
    val text = para.map(_.trim).mkString(" ").trim
    val plain = List("**", "__", "*", "_")
      .foldLeft(text) { (s, mark) =>
        if s.length > 2 * mark.length && s.startsWith(mark) && s.endsWith(mark) then
          s.drop(mark.length).dropRight(mark.length)
        else s
      }
      .trim
    Option(plain).filter(s => s.nonEmpty && !s.startsWith("#") && !s.startsWith("!"))

  private val LedeHeadings = Set("## summary", "## lede", "## abstract")

  /** Words an author leaves in place of a summary meaning to come back — which then shipped: three articles showed
    * "placeholder" as their card text for a month (2026-08-28 batch).
    */
  private val StandIns =
    Set("placeholder", "todo", "tbd", "tba", "fixme", "xxx", "summary", "tk", "na", "n a", "none", "wip")

  /** A summary as given: trimmed, blank = absent, and a stand-in refused rather than stored. */
  def summaryOf(raw: Option[String]): Either[String, Option[String]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        val norm = s.toLowerCase.replaceAll("[^a-z]+", " ").trim
        if StandIns.contains(norm) || norm.startsWith("lorem ipsum") then
          Left(
            s"summary '$s' is a stand-in, not a summary — write the one-paragraph lede, or omit it " +
              "and the article's opening paragraph (or its `## Summary` section) is used"
          )
        else Right(Some(s))

  /** The kinds the compiled wiki uses today. Not a constraint — `kind` is free text so a new section needs no migration
    * — but the vocabulary the tools advertise and the CLI walks.
    */
  val Kinds = List("concept", "myth", "culture", "region", "timeline", "report", "index")

  // BOUNDED: a slug is a URL segment and, on the export path, a file name. The character class is
  // unchanged (existing slugs use '.' and '-'); only the missing length limit is added.
  private val Slug = """^[a-z0-9][a-z0-9_.-]{0,120}$""".r
  private val Kind = """^[a-z][a-z0-9_-]*$""".r

  def notFound(slug: String): ApiError = ApiError(s"No article '$slug'", Some(Write.CodeNotFound))

  /** Normalise and validate a slug (it is both the primary key and a filename). */
  def slugOf(raw: String): Either[String, String] =
    val s = raw.trim.toLowerCase
    if Slug.matches(s) then Right(s)
    else Left(s"invalid slug '$raw' — use lower-case letters, digits, '_', '-' or '.' (e.g. sea_peoples)")

  def kindOf(raw: String): Either[String, String] =
    val k = raw.trim.toLowerCase
    if Kind.matches(k) then Right(k)
    else Left(s"invalid kind '$raw' — one of ${Kinds.mkString(", ")} (or another lower-case word)")

  /** The singular kind a wiki directory holds: `concepts` → `concept`, `timeline` → `timeline`. */
  def kindOfDir(dirName: String): String =
    val d = dirName.trim.toLowerCase
    if d.length > 1 && d.endsWith("s") && !Kinds.contains(d) then d.dropRight(1) else d

  /** The directory an exported article belongs in — the inverse of [[kindOfDir]] for the directories the wiki actually
    * has, and the identity for anything else.
    */
  def dirOfKind(kind: String): String = kind.trim.toLowerCase

  /** Full replace (`put_article`). */
  def applyPut(slug: String, p: ArticlePut): Either[String, Article] =
    for
      s <- slugOf(slug)
      k <- kindOf(p.kind)
      _ <- if p.title.trim.nonEmpty then Right(()) else Left("title must not be empty")
      period <- periodOf(p.periodStart, p.periodEnd)
      summary <- summaryOf(p.summary)
      filter <- p.filter.fold[Either[String, Option[ArticleFilter]]](Right(None))(ArticleFilter.normalise)
      reasons <- CoverageReason.merge(Map.empty, p.reasons)
    yield Article(
      slug = s,
      title = p.title.trim,
      kind = k,
      body = ArticleCoverage.stripGenerated(p.body),
      summary = summary.orElse(lede(p.body)),
      tags = p.tags.map(_.trim).filter(_.nonEmpty).distinct,
      regions = p.regions.map(_.trim).filter(_.nonEmpty).distinct,
      period = period,
      filter = filter,
      reasons = reasons
    )

  /** The optimistic-concurrency check, separate so the endpoint can answer 412 specifically. */
  def precondition(existing: Article, patch: ArticlePatch): Either[String, Unit] =
    patch.ifUpdatedAt match
      case None       => Right(())
      case Some(want) =>
        val actual = existing.updatedAt.getOrElse("")
        if want == actual then Right(())
        else
          Left(
            s"'${existing.slug}' has moved on: you sent ifUpdatedAt='$want' but the stored " +
              s"updatedAt is '$actual'. Re-read the article and re-apply your patch."
          )

  /** Selective merge (`patch_article`): a present part replaces only itself, an absent part is left exactly as it is.
    * The body is replaced wholesale when given — an article is prose, so there is no meaningful element-wise merge;
    * `append` covers the common "add a section" case without a re-upload.
    */
  def applyPatch(existing: Article, patch: ArticlePatch): Either[String, Article] =
    for
      _ <- precondition(existing, patch)
      kind <- patch.kind.fold[Either[String, String]](Right(existing.kind))(kindOf)
      period <- patch.period.fold[Either[String, Option[Period]]](Right(existing.period))(p =>
        periodOf(Some(p.start), Some(p.end))
      )
      body <- bodyOf(existing.body, patch)
      summary <- summaryOf(patch.summary)
      filter <- patch.filter.fold[Either[String, Option[ArticleFilter]]](Right(existing.filter))(
        ArticleFilter.normalise
      )
      reasons <- CoverageReason.merge(existing.reasons, patch.reasons)
    yield existing.copy(
      title = patch.title.map(_.trim).filter(_.nonEmpty).getOrElse(existing.title),
      kind = kind,
      body = body,
      summary = summary.orElse(existing.summary),
      tags = patch.tags.fold(existing.tags)(Write.applyList(existing.tags, _)),
      regions = patch.regions.getOrElse(existing.regions).map(_.trim).filter(_.nonEmpty).distinct,
      period = period,
      filter = filter,
      reasons = reasons
    )

  /** `body` replaces, `append` adds text. When `section` is given (e.g. `"## Disagreements"`), the `append` text is
    * inserted immediately after that heading's content and before the next heading at the same or higher level. Without
    * `section`, `append` goes at the end (A16).
    */
  private def bodyOf(current: String, patch: ArticlePatch): Either[String, String] =
    // an exported note sent back as the body (#958): its generated sections never enter the store
    val base = patch.body.map(ArticleCoverage.stripGenerated).getOrElse(current)
    patch.append.map(ArticleCoverage.stripGenerated).map(_.trim).filter(_.nonEmpty) match
      case None    => Right(base)
      case Some(a) =>
        patch.section.map(_.trim).filter(_.nonEmpty) match
          case None    => Right(if base.trim.isEmpty then a else s"${base.stripTrailing}\n\n$a")
          case Some(s) => appendUnderSection(base, s, a)

  /** Append `text` under the heading that matches `section` (e.g. `"## Disagreements"`). The match is case-insensitive
    * and ignores leading `#` differences — `"Disagreements"` matches `## Disagreements`. The text is inserted before
    * the next heading at the same or higher level.
    */
  def appendUnderSection(body: String, section: String, text: String): Either[String, String] =
    val sNorm = section.replaceAll("^#+\\s*", "").trim.toLowerCase
    val lines = body.split("\n", -1).toList
    // find the heading line that matches
    val idx = lines.indexWhere { line =>
      val t = line.trim
      t.startsWith("#") && t.replaceAll("^#+\\s*", "").trim.toLowerCase == sNorm
    }
    if idx < 0 then Left(s"no heading matching '$section' in the article body")
    else
      // determine the heading level (number of leading #s)
      val level = lines(idx).trim.takeWhile(_ == '#').length
      // find the next heading at the same or higher level
      val rest = lines.drop(idx + 1)
      val nextIdx = rest.indexWhere { line =>
        val t = line.trim
        t.startsWith("#") && t.takeWhile(_ == '#').length <= level
      }
      val insertAt = if nextIdx < 0 then lines.length else idx + 1 + nextIdx
      val before = lines.take(insertAt)
      val after = lines.drop(insertAt)
      val result = (before.mkString("\n").stripTrailing + "\n\n" + text.trim +
        (if after.isEmpty then "" else "\n\n" + after.mkString("\n")))
      Right(result)

  private def periodOf(start: Option[Long], end: Option[Long]): Either[String, Option[Period]] =
    (start, end) match
      case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
      case (Some(a), Some(b))           => Left(s"period_start ($a) must not be after period_end ($b)")
      case (None, None)                 => Right(None)
      case _                            => Left("give both periodStart and periodEnd, or neither")

/** Markdown rendering for the article surfaces — the wire format for MCP, the CLI and (through flexmark) the HTML page.
  * Kept beside the model rather than in [[Markdown]] so stream 2.7 adds no lines to a file three other streams are
  * editing.
  */
object ArticleMarkdown:

  val AllParts = List("body", "summary", "coverage", "cited", "backlinks")

  /** One article, exactly as `/article/{slug}` renders it: the body, then its backlinks, then the sources it cites.
    * Wikilinks are left as `[[…]]` — the HTML layer resolves them, the terminal shows them as the agent wrote them.
    *
    * `parts` filters which sections appear. Empty = the full rendering (body + the generated coverage sections +
    * backlinks + cited). Valid parts: body, summary, coverage, cited, backlinks. Teasers in cited/backlinks are
    * truncated to 200 chars with markdown stripped.
    */
  def article(v: ArticleView, parts: List[String] = Nil): String =
    if parts.isEmpty then articleFull(v)
    else articleParts(v, parts.filter(AllParts.contains))

  private def articleFull(v: ArticleView): String =
    val a = v.article
    val body = a.body.trim
    val line = metaLine(a.kind, a.regions, YearFmt.period(a.periodStart, a.periodEnd), a.tags)
    val head =
      if Articles.heading(body).isEmpty then s"# ${a.title}\n\n" + (if line.isEmpty then "" else s"$line\n\n") + body
      else if line.isEmpty then body
      else
        body.indexOf('\n') match
          case -1 => s"$body\n\n$line"
          case i  => s"${body.take(i)}\n\n$line${body.drop(i)}"
    val footer = sourcesCited(v.sourcesCited)
    // Item 23: several compiled articles end with their own hand-written "## Sources" list. Rendered
    // under the generated footer that is the same list twice, with different formatting. The
    // generated one wins — it carries the year and the status and it cannot go stale.
    val trimmed = if footer.isEmpty then head else stripSourcesSection(head)
    trimmed + coverage(v) + backlinks(v.backlinks) + footer + FormatMarkdown.notice(v.formatting)

  /** The sections code generates from the article's filter (#958), after the body; nothing when it records none.
    */
  private def coverage(v: ArticleView): String = v.coverage.fold("")(c => "\n\n" + ArticleCoverage.sections(c))

  /** The line under an article's title: kind · regions · period · tags. Regions and tags are web UI links with readable
    * labels (`greek religion` → `/#tag=greek_religion`), as on a source page.
    */
  private[api] def metaLine(kind: String, regions: List[String], period: String, tags: List[String]): String =
    val parts = List(Label.display(kind.trim), chipLinksList(regions, "place"), period.trim, chipLinksList(tags, "tag"))
      .filter(_.nonEmpty)
    if parts.isEmpty then "" else s"_${parts.mkString(" · ")}_"

  /** Chip links from a proper list (replaces the comma-split variant in Markdown). */
  private def chipLinksList(values: List[String], hashKey: String): String =
    values
      .filter(_.nonEmpty)
      .map(v => s"[${Label.display(v).replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${Markdown.enc(v)})")
      .mkString(", ")

  /** A `## Sources` / `## Sources cited` / `## References` section at the END of a body, removed. Only a TRAILING one:
    * a section in the middle of an article is prose about sources, not a list of them. Pure, so "which heading counts"
    * is a test rather than a guess.
    */
  def stripSourcesSection(body: String): String =
    val heading = """^#{1,6}\s*(sources\b.*|references|bibliography|works cited)\s*$""".r
    val lines = body.linesIterator.toList
    lines.lastIndexWhere(l => heading.matches(l.trim.toLowerCase)) match
      case -1 => body
      case i  =>
        // trailing only: nothing but list items, links and blank lines may follow it
        val rest = lines.drop(i + 1).map(_.trim).filter(_.nonEmpty)
        if rest.forall(l => l.startsWith("-") || l.startsWith("*") || l.startsWith("[[") || l.startsWith("1."))
        then lines.take(i).mkString("\n").stripTrailing
        else body

  private def articleParts(v: ArticleView, show: List[String]): String =
    val a = v.article
    val out = List.newBuilder[String]

    if show.contains("summary") then
      a.summary.map(_.trim).filter(_.nonEmpty).foreach(s => out += s"_${stripMd(s).take(200)}_")

    if show.contains("body") then
      val body = a.body.trim
      val line = metaLine(a.kind, a.regions, YearFmt.period(a.periodStart, a.periodEnd), a.tags)
      val head =
        if Articles.heading(body).isEmpty then s"# ${a.title}\n\n" + (if line.isEmpty then "" else s"$line\n\n") + body
        else if line.isEmpty then body
        else
          body.indexOf('\n') match
            case -1 => s"$body\n\n$line"
            case i  => s"${body.take(i)}\n\n$line${body.drop(i)}"
      out += head

    if show.contains("coverage") then v.coverage.foreach(c => out += ArticleCoverage.sections(c))

    if show.contains("backlinks") then
      val bl = backlinks(v.backlinks, truncate = true)
      if bl.nonEmpty then out += bl

    if show.contains("cited") then
      val sc = sourcesCited(v.sourcesCited, truncate = true)
      if sc.nonEmpty then out += sc

    // the formatting problems travel with the text they are about
    if (show.contains("body") || show.contains("summary")) && v.formatting.nonEmpty then
      val shown = v.formatting.filter(i => show.contains(if i.field == FormatField.Summary then "summary" else "body"))
      if shown.nonEmpty then out += FormatMarkdown.notice(shown).trim

    val result = out.result()
    if result.isEmpty then s"# ${a.title}\n\n_(no parts selected)_"
    else result.mkString("\n\n")

  /** Strip markdown formatting for teaser display: bold, italic, links, wikilinks. */
  private def stripMd(s: String): String =
    s.replaceAll("""\[\[([^\]|]+)\|([^\]]+)\]\]""", "$2")
      .replaceAll("""\[\[([^\]]+)\]\]""", "$1")
      .replaceAll("""\[([^\]]+)\]\([^)]+\)""", "$1")
      .replaceAll("""[*_]{1,2}""", "")
      .replaceAll("""`""", "")

  def backlinks(rows: List[Backlink], truncate: Boolean = false): String =
    if rows.isEmpty then ""
    else
      val items = rows.map { b =>
        val title = b.title.map(t => if truncate then s" — ${stripMd(t).take(200)}" else s" — $t").getOrElse("")
        s"- [[${b.id}]]$title"
      }
      s"\n\n## Backlinks (${rows.size})\n\n${items.mkString("\n")}"

  /** `authors year · title · status` per cited source; an unknown citekey is shown as such rather than silently
    * dropped, because that is a dangling citation the agent should fix.
    */
  def sourcesCited(rows: List[CitedSource], truncate: Boolean = false): String =
    if rows.isEmpty then ""
    else
      val items = rows.map { s =>
        val t = s.title
          .map(v => if truncate then stripMd(v).take(200) else v)
          .getOrElse("_(not in the corpus)_")
        val auth = SourceRef.shortAuthors(s.authors)
        val yr = s.year.map(_.toString).getOrElse("")
        val byline = List(auth, yr).filter(_.nonEmpty).mkString(" ")
        val tail = List(byline, s"_${t}_", s.status.map(st => s"**${st.wire}**").getOrElse(""))
          .filter(_.nonEmpty)
          .mkString(" · ")
        s"- [[${s.citekey}]] — $tail"
      }
      s"\n\n## Sources cited (${rows.size})\n\n${items.mkString("\n")}"

  /** The version history of one article, newest first. */
  def versions(slug: String, vs: List[ArticleVersion]): String =
    if vs.isEmpty then s"_No versions recorded for **$slug**._"
    else
      val items = vs.map { v =>
        val sum = v.summary.map(s => s" — ${s.take(120)}").getOrElse("")
        s"- **${v.id}** · ${v.updatedAt} · ${Plural.count(v.wordCount, "word")}$sum"
      }
      s"### ${vs.size} version(s) of $slug\n\n${items.mkString("\n")}"

  /** One historical version's full body. */
  def version(v: ArticleVersionFull): String =
    val sum = v.summary.map(s => s"\n\n_${s.take(200)}_").getOrElse("")
    s"### Version ${v.id} of ${v.slug}\n\n_saved ${v.updatedAt}, archived ${v.createdAt}_$sum\n\n---\n\n${v.body}"

  /** Headed and footed like every offset-paged list ([[OffsetPage.markdown]]): a cut list says where the next page
    * starts.
    */
  def list(r: ArticleList): String =
    OffsetPage.markdown(r.page, "article(s)", "_No matching articles._", r.articles) { a =>
      val tagsStr = a.tags.mkString(", ")
      val meta = List(a.kind, YearFmt.period(a.periodStart, a.periodEnd), tagsStr)
        .map(_.trim)
        .filter(_.nonEmpty)
        .mkString(" · ")
      val sum = a.summary.map(s => s"\n  ${s.trim}").getOrElse("")
      s"- **${a.slug}** — ${a.title}\n  _${meta}_$sum"
    }

// ── wire DTOs (Kindlings derives the circe codec AND the tapir schema from each) ────────────────

/** Full create/replace of one article. */
final case class ArticlePut(
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    /** The filter the article is compiled from (#958): its Not used list and Method note are generated. */
    filter: Option[ArticleFilter] = None,
    /** Why each source of the filter the body does not cite is left out. */
    reasons: List[CoverageReason] = Nil
)
object ArticlePut:
  given Schema[ArticlePut] = KindlingsSchema.derived[ArticlePut].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[ArticlePut] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticlePut])

  def of(a: Article): ArticlePut = ArticlePut(
    a.title,
    a.kind,
    a.body,
    a.summary,
    a.tags,
    a.regions,
    a.period.map(_.start),
    a.period.map(_.end),
    a.filter,
    CoverageReason.list(a.reasons)
  )

/** Selective enrichment. Every part is independently optional; absent parts are untouched. */
final case class ArticlePatch(
    title: Option[String] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    /** Markdown appended after the current body — how to EXTEND an article without re-uploading it. */
    append: Option[String] = None,
    /** When given with `append`, the append text is inserted under this heading (e.g. `"## Disagreements"`) instead of
      * at the end of the body.
      */
    section: Option[String] = None,
    tags: Option[ListPatch] = None,
    regions: Option[List[String]] = None,
    period: Option[PeriodInput] = None,
    kind: Option[String] = None,
    ifUpdatedAt: Option[String] = None,
    /** Replace the filter the article is compiled from (#958); an empty filter `{}` clears it. */
    filter: Option[ArticleFilter] = None,
    /** Set the reason of each named source (an empty reason removes it); the others are kept. */
    reasons: List[CoverageReason] = Nil,
    /** Set why a disagreement of the critique checklist does not bear on the article (V091), per pair (an empty reason
      * removes it); the others are kept. Stored beside the article: a patch of these alone writes no version and leaves
      * `updatedAt` as it is.
      */
    critiqueReasons: List[CritiqueReason] = Nil
):
  /** Nothing but critique reasons (and the token): the article row is not written. */
  def onlyCritiques: Boolean =
    critiqueReasons.nonEmpty && copy(critiqueReasons = Nil, ifUpdatedAt = None) == ArticlePatch()
object ArticlePatch:
  given Schema[ArticlePatch] = KindlingsSchema.derived[ArticlePatch].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[ArticlePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticlePatch])

/** A list row: everything but the body. */
final case class ArticleSummary(
    slug: String,
    title: String,
    kind: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    updatedAt: Option[String] = None,
    /** Where it stands with the review panel (`GET /api/articles/{slug}/review` has the history). */
    review: Option[ReviewBadge] = None
) derives KindlingsCodecAsObject
object ArticleSummary:
  given Schema[ArticleSummary] = KindlingsSchema.derived[ArticleSummary].schema

/** The article itself (`GET /api/articles/{slug}`), body included. */
final case class ArticleRow(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    updatedAt: Option[String] = None,
    /** The article's permanent identity (V030), which a bundle carries; the slug is its label. */
    uid: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject
object ArticleRow:
  given Schema[ArticleRow] = KindlingsSchema.derived[ArticleRow].schema

/** Something that links TO an article: another article, or a source whose notes mention it. */
final case class Backlink(kind: String, id: String, title: Option[String]) derives KindlingsCodecAsObject
object Backlink:
  given Schema[Backlink] = KindlingsSchema.derived[Backlink].schema

/** A source an article cites, with the metadata the footer shows. `title` is `None` when the citekey is not (yet) in
  * the corpus — a dangling citation.
  */
final case class CitedSource(
    citekey: String,
    title: Option[String],
    year: Option[Int],
    status: Option[SourceStatus],
    authors: List[String] = Nil,
    doi: Option[String] = None
) derives KindlingsCodecAsObject
object CitedSource:
  given Schema[CitedSource] = KindlingsSchema.derived[CitedSource].schema

/** `GET /api/articles/{slug}` — the article with both directions of its link graph resolved, and the formatting
  * problems of its body and summary ([[FormatIssue]]: what will not display as the writer meant), so an agent sees them
  * right after reading or writing it.
  */
final case class ArticleView(
    article: ArticleRow,
    backlinks: List[Backlink],
    sourcesCited: List[CitedSource],
    formatting: List[FormatIssue] = Nil,
    /** The coverage of its recorded filter (#958) — `None` when it records none. */
    coverage: Option[Coverage] = None
) derives KindlingsCodecAsObject
object ArticleView:
  given Schema[ArticleView] = KindlingsSchema.derived[ArticleView].schema

/** `GET /api/articles` — `total` ignores limit/offset. */
final case class ArticleList(
    total: Int,
    articles: List[ArticleSummary],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None,
    /** How many of the `total` are of each kind, the largest first (ignores limit/offset). */
    kinds: List[ArticleKindCount] = Nil
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, articles.size)
object ArticleList:
  given Schema[ArticleList] = KindlingsSchema.derived[ArticleList].schema
  def of(total: Int, rows: List[ArticleSummary], offset: Int, kinds: List[ArticleKindCount] = Nil): ArticleList =
    ArticleList(total, rows, offset, OffsetPage(total, offset, rows.size).next, kinds)

  /** Per-kind counts as a list shows them: the largest first, then by kind. */
  def byKind(counts: List[ArticleKindCount]): List[ArticleKindCount] =
    counts.filter(_.count > 0).sortBy(k => (-k.count, k.kind))

/** How many listed articles are of one kind. */
final case class ArticleKindCount(kind: String, count: Int) derives KindlingsCodecAsObject
object ArticleKindCount:
  given Schema[ArticleKindCount] = KindlingsSchema.derived[ArticleKindCount].schema

/** `POST /api/articles/import`: one note's raw text, parsed server-side by [[ArticleNote.parse]]. The host-side path of
  * `histmersion seed` for articles — the CLI can read the host's `wiki` directories, the containerised server cannot.
  * Carries the same "stored row is newer than the file" guard as the source seed, so a reseed cannot wipe an agent's
  * enrichment.
  *
  * @param slug
  *   fallback only, used when the frontmatter has no `slug` (normally the filename)
  * @param kind
  *   fallback only, used when the frontmatter has no `type` (normally the directory)
  * @param mtime
  *   the file's last-modified time in epoch millis; enables the "newer in DB" skip
  */
final case class ArticleImport(
    text: String,
    slug: Option[String] = None,
    kind: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    /** Refuse the import when the body has a formatting ERROR (see [[FormatIssue]]). */
    strict: Boolean = false
) derives KindlingsCodecAsObject
object ArticleImport:
  given Schema[ArticleImport] = KindlingsSchema.derived[ArticleImport].schema

final case class ArticleDeleted(slug: String, deleted: Boolean) derives KindlingsCodecAsObject
object ArticleDeleted:
  given Schema[ArticleDeleted] = KindlingsSchema.derived[ArticleDeleted].schema

/** One version in the history of an article (list view — summary truncated, word count computed). */
final case class ArticleVersion(
    id: Long,
    slug: String,
    summary: Option[String],
    updatedAt: String,
    createdAt: String,
    wordCount: Int
) derives KindlingsCodecAsObject
object ArticleVersion:
  given Schema[ArticleVersion] = KindlingsSchema.derived[ArticleVersion].schema

/** One historical version of an article's body (detail view — full body included). */
final case class ArticleVersionFull(
    id: Long,
    slug: String,
    body: String,
    summary: Option[String],
    updatedAt: String,
    createdAt: String
) derives KindlingsCodecAsObject
object ArticleVersionFull:
  given Schema[ArticleVersionFull] = KindlingsSchema.derived[ArticleVersionFull].schema
