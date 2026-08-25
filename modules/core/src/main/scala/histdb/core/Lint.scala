package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `histdb lint` — the health checks the research workspace used to run by hand as DuckDB queries
  * plus `grep` for dead wikilinks (plan §5.1 row `lint-wiki`, §5.2 stream 2.8).
  *
  * A rule is **a pure SQL projection plus a pure classifier**: `db.LintRepo` fetches the raw rows
  * (the corpus is small — a few thousand rows at worst) and everything that decides *whether a row
  * offends*, *what the detail line says* and *how the report renders* lives here, with no database.
  * That is what makes each rule unit-testable.
  *
  * The report is deliberately shaped as `rules[] { rule, count, items[] }`: `count` is always the
  * TRUE total, while `items` is capped at `lint.max-items` so a corpus-wide problem does not produce
  * a megabyte of JSON.
  */
object Lint:

  /** One offending row. `citekey` is the source to go and fix; `detail` says what is wrong with it
    * (and, for a dangling edge, what it probably meant). */
  final case class LintItem(citekey: String, detail: String) derives KindlingsCodecAsObject
  object LintItem:
    given Schema[LintItem] = KindlingsSchema.derived[LintItem].schema

  /** One rule's verdict. `count` ignores the `items` cap. */
  final case class LintRuleReport(rule: String, description: String, count: Int, items: List[LintItem])
      derives KindlingsCodecAsObject
  object LintRuleReport:
    given Schema[LintRuleReport] = KindlingsSchema.derived[LintRuleReport].schema

  /** `fixed` lists the repairs actually applied (`POST /api/lint/fix`), e.g.
    * `resolve-edges: 3 edge target(s) rewritten`. Empty for a plain read-only lint. */
  final case class LintReport(rules: List[LintRuleReport], fixed: List[String] = Nil)
      derives KindlingsCodecAsObject:
    def total: Int = rules.map(_.count).sum
  object LintReport:
    given Schema[LintReport] = KindlingsSchema.derived[LintReport].schema

  /** What `POST /api/lint/fix` did. Both repairs are bounded per call — `remaining` is how many
    * sources still need re-chunking, so a caller (the CLI) loops until it reaches 0. */
  final case class LintFixResult(
      applied: List[String],
      edgesResolved: Int,
      sourcesReindexed: Int,
      chunksWritten: Int,
      remaining: Int
  ) derives KindlingsCodecAsObject
  object LintFixResult:
    given Schema[LintFixResult] = KindlingsSchema.derived[LintFixResult].schema

  /** The catalog. `name` is the wire/CLI spelling (`--rule dangling-edges`). */
  final case class Rule(name: String, description: String)

  val DanglingEdges           = "dangling-edges"
  val UnresolvedShortCitekeys = "unresolved-short-citekeys"
  val NoFindings              = "no-findings"
  val NoPeriod                = "no-period"
  val NoRegions               = "no-regions"
  val EmptySummary            = "empty-summary"
  val UnassessedStatus        = "unassessed-status"
  val DuplicateDoi            = "duplicate-doi"
  val DuplicateSha256         = "duplicate-sha256"
  val OrphanSources           = "orphan-sources"
  val StaleChunks             = "stale-chunks"
  val UndatedFindings         = "undated-findings"
  val UnlocatedFindings       = "unlocated-findings"
  val LegacyFigureLinks       = "legacy-figure-links"
  val DanglingArticleLinks    = "dangling-article-links"
  // ── wave 3 rules (plan §6, A9/A14/A15/A17/A18) ───────────────────────────────────────────
  val NonCanonicalTag         = "non-canonical-tag"
  val MissingYear             = "missing-year"
  val YearCitekeyMismatch     = "year-citekey-mismatch"
  val AuthorPipe              = "author-pipe"
  val ArticleFiller           = "article-filler"
  val ArticleUncitedParagraph = "article-uncited-paragraph"
  val SupersededWithoutEdge   = "superseded-without-edge"

  val all: List[Rule] = List(
    Rule(DanglingEdges, "edge targets that are not citekeys — dead references and unresolved [[wikilinks]]; the ones SHAPED like a citekey (a four-digit year) are listed first, because the rest are links to concept articles"),
    Rule(UnresolvedShortCitekeys, "dangling edge targets that ARE a prefix of one or more real citekeys — `lint --fix` rewrites the unambiguous ones"),
    Rule(NoFindings, "sources with no findings mined out of them yet (un-enriched)"),
    Rule(NoPeriod, "sources with no deep-time period, so they appear on no timeline"),
    Rule(NoRegions, "sources with no regions, so they appear in no region tree"),
    Rule(EmptySummary, "sources whose summary is missing or blank"),
    Rule(UnassessedStatus, "sources still at status=unassessed"),
    Rule(DuplicateDoi, "the same DOI stored under more than one citekey"),
    Rule(DuplicateSha256, "the same file hash stored under more than one citekey (skipped while the column is unpopulated)"),
    Rule(OrphanSources, "sources with no incoming and no outgoing edges — cited by nothing, citing nothing"),
    Rule(StaleChunks, "sources that have a body but no full-text chunks — `lint --fix` reindexes them"),
    Rule(UndatedFindings, "findings with no period, so they are invisible to every time filter"),
    Rule(UnlocatedFindings, "findings with neither coordinates nor a region — invisible on the map"),
    Rule(LegacyFigureLinks, "bodies whose figures still point at a `figures/…` path (usually the converter's own absolute path) instead of the canonical `assets/…` — they render as 0×0 images; re-import the note (`histdb seed --force`) to rewrite them"),
    Rule(DanglingArticleLinks, "`[[wikilinks]]` in an ARTICLE that resolve to nothing — neither a compiled article slug nor a source citekey (nor a unique short form of one); they render as plain text, so the reader sees a name with no page behind it"),
    Rule(NonCanonicalTag, "tags not in lowercase_snake_case (the canonical form)"),
    Rule(MissingYear, "sources with no publication year — rendered as n.d."),
    Rule(YearCitekeyMismatch, "the four-digit year in the citekey does not match the stored publication year"),
    Rule(AuthorPipe, "author names containing a pipe '|' — likely a parse artifact (\"Cline| Eric H.\")"),
    Rule(ArticleFiller, "articles whose body contains banned filler phrases from the writing guide"),
    Rule(ArticleUncitedParagraph, "article body paragraphs with no [[citekey]] citation"),
    Rule(SupersededWithoutEdge, "sources with status=superseded but no superseded_by edge — set the edge or change the status")
  )

  val names: List[String] = all.map(_.name)

  def rule(name: String): Option[Rule] = all.find(_.name == name.trim.toLowerCase)

  /** Which rules to run: empty selection = all of them. An unknown name is an error, not a silent
    * empty report — a typo in `--rule` must never look like a clean bill of health. */
  def select(requested: List[String]): Either[String, List[Rule]] =
    val wanted = requested.map(_.trim).filter(_.nonEmpty)
    if wanted.isEmpty then Right(all)
    else
      val unknown = wanted.filter(w => rule(w).isEmpty)
      if unknown.nonEmpty then
        Left(s"unknown lint rule(s): ${unknown.mkString(", ")} — known rules: ${names.mkString(", ")}")
      else Right(wanted.flatMap(rule).distinct)

  // ── pure classifiers ─────────────────────────────────────────────────────────────────────────

  /** Blank = absent, or nothing but whitespace. */
  def blank(s: Option[String]): Boolean = s.forall(_.trim.isEmpty)

  /** The citekeys a dangling edge target most plausibly meant, best first.
    *
    * Ranked the way the corpus is actually written: `heggarty2023` is a SHORT citekey for
    * `heggarty2023_language`, so an exact `target_` prefix wins; then any prefix match; then keys
    * sharing a long common prefix (a typo — `heggarrty2023_language`). Ties break alphabetically so
    * the output is deterministic. */
  def suggest(target: String, citekeys: Iterable[String], max: Int): List[String] =
    val t = target.trim.toLowerCase
    if t.isEmpty || max <= 0 then Nil
    else
      def commonPrefix(a: String, b: String): Int =
        val n = math.min(a.length, b.length)
        var i = 0
        while i < n && a.charAt(i) == b.charAt(i) do i += 1
        i
      // rank: 0 = "<target>_…" (a short citekey), 1 = any prefix match, 2 = shares ≥ 4 leading chars
      val scored = citekeys.iterator.filter(_ != target).flatMap { ck =>
        val c = ck.toLowerCase
        val p = commonPrefix(c, t)
        if c.startsWith(t + "_") then Some((0, c.length, ck))
        else if c.startsWith(t) || t.startsWith(c) then Some((1, -p, ck))
        else if p >= 4 then Some((2, -p, ck))
        else None
      }.toList
      scored.sortBy((rank, negLen, ck) => (rank, negLen, ck)).map(_._3).take(max)

  /** Does this edge target look like a SOURCE reference rather than a concept/article link?
    *
    * The corpus convention is `author<year>_word` — `heggarty2023_language`, `gray_atkinson_2003` —
    * so a four-digit year is the signal. Targets without one (`bronze_age_collapse`,
    * `oral_tradition`) are `[[wikilinks]]` to compiled ARTICLES, not to sources: on the seeded corpus
    * they are 1 050 of the 1 051 unresolved targets, and flagging them as broken references would
    * bury the one that actually is. */
  def looksLikeCitekey(target: String): Boolean = """\d{4}""".r.findFirstIn(target).isDefined

  /** `dangling-edges`: every edge whose target is not a citekey, annotated with the closest
    * candidates. Deduplicated on (src, kind, dst); the targets that look like SOURCE references sort
    * first, because those are the ones a human can act on — the rest are article links and say so. */
  def danglingEdges(edges: List[Edge], citekeys: Set[String], maxSuggestions: Int): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.contains(e.dst))
      .sortBy(e => (if looksLikeCitekey(e.dst) then 0 else 1, e.src, e.kind, e.dst))
      .map { e =>
        val near = suggest(e.dst, citekeys, maxSuggestions)
        val what =
          if looksLikeCitekey(e.dst) then "no such source"
          else "no such source — looks like a concept/article link, not a citekey"
        val tail = if near.isEmpty then "" else s" — closest: ${near.mkString(", ")}"
        LintItem(e.src, s"${e.kind} → ${e.dst} ($what)$tail")
      }

  /** `unresolved-short-citekeys`: the subset of the dangling targets that ARE the prefix of a real
    * citekey — i.e. a short form nobody expanded. Exactly one candidate means
    * `POST /api/edges/resolve` can fix it mechanically; more than one needs a human to choose. */
  def unresolvedShortCitekeys(edges: List[Edge], citekeys: Set[String], maxSuggestions: Int): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.contains(e.dst))
      .flatMap { e =>
        val near = citekeys.filter(ck => ck.startsWith(e.dst + "_")).toList.sorted
        if near.isEmpty then None
        else
          val shown = near.take(maxSuggestions)
          val more  = if near.sizeIs > shown.size then s", +${near.size - shown.size} more" else ""
          val verdict =
            if near.sizeIs == 1 then s"resolves to ${near.head} (fixable: lint --fix)"
            else s"ambiguous between ${shown.mkString(", ")}$more"
          Some(LintItem(e.src, s"${e.kind} → ${e.dst} $verdict"))
      }
      .sortBy(i => (i.citekey, i.detail))

  /** `duplicate-doi` / `duplicate-sha256`: rows sharing a normalised key. Each member of a duplicate
    * group becomes an item naming its partners, so either side can be opened from the report. */
  def duplicates(rows: List[(String, String)], label: String): List[LintItem] =
    rows
      .filter((_, key) => key.trim.nonEmpty)
      .groupBy((_, key) => key.trim.toLowerCase)
      .toList
      .filter((_, group) => group.map(_._1).distinct.sizeIs > 1)
      .sortBy((key, _) => key) // duplicate groups stay together, members alphabetical inside one
      .flatMap { (key, group) =>
        val cks = group.map(_._1).distinct.sorted
        cks.map(ck => LintItem(ck, s"$label $key — also on ${cks.filterNot(_ == ck).mkString(", ")}"))
      }

  // ── wave 3 classifiers (pure, no database) ─────────────────────────────────────────────────

  /** Lowercase snake_case: `[a-z0-9][a-z0-9_]*`. */
  private val CanonicalTag = """^[a-z0-9][a-z0-9_]*$""".r

  /** A tag is canonical when it is lowercase snake_case. */
  def isCanonicalTag(tag: String): Boolean = CanonicalTag.matches(tag)

  /** Normalise a tag to lowercase snake_case: lowercase, spaces/hyphens → underscores, collapse. */
  def normaliseTag(tag: String): String =
    tag.trim.toLowerCase.replaceAll("""[\s-]+""", "_").replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")

  /** `non-canonical-tag`: sources whose tags are not in lowercase_snake_case. */
  def nonCanonicalTags(rows: List[(String, List[String])]): List[LintItem] =
    rows.flatMap { (ck, tags) =>
      tags.filterNot(isCanonicalTag).map(t => LintItem(ck, s"tag '$t' → '${normaliseTag(t)}'"))
    }.sortBy(i => (i.citekey, i.detail))

  /** `year-citekey-mismatch`: the four-digit year in the citekey disagrees with the stored year. */
  private val YearInCitekey = """(\d{4})""".r.unanchored

  def yearCitekeyMismatch(rows: List[(String, Option[Int])]): List[LintItem] =
    rows.flatMap { (ck, yr) =>
      yr.flatMap { y =>
        YearInCitekey.findFirstMatchIn(ck).flatMap { m =>
          val inKey = m.group(1).toInt
          if inKey == y then None
          else Some(LintItem(ck, s"citekey has $inKey but stored year is $y"))
        }
      }
    }.sortBy(_.citekey)

  /** `author-pipe`: an author name containing '|' is a parse artifact. */
  def authorPipe(rows: List[(String, List[String])]): List[LintItem] =
    rows.flatMap { (ck, authors) =>
      authors.filter(_.contains("|")).map(a => LintItem(ck, s"author '$a'"))
    }.sortBy(i => (i.citekey, i.detail))

  /** Banned filler phrases from the writing guide. A body paragraph containing any of these is an
    * `article-filler` lint finding. Kept here in Lint.scala and referenced by the guide. */
  val FillerPhrases: List[String] = List(
    "it is worth noting",
    "it should be noted",
    "it is important to note",
    "interestingly",
    "notably",
    "significantly",
    "it is clear that",
    "needless to say",
    "of course",
    "as is well known",
    "it goes without saying",
    "in conclusion",
    "to summarize",
    "in summary",
    "broadly speaking",
    "generally speaking",
    "it must be emphasized",
    "it cannot be overstated",
    "plays a crucial role",
    "rich tapestry",
    "sheds light on",
    "shed new light",
    "paved the way",
    "remains to be seen",
    "further research is needed"
  )

  private val FillerPattern: scala.util.matching.Regex =
    FillerPhrases.map(p => java.util.regex.Pattern.quote(p)).mkString("(?i)(?:", "|", ")").r

  /** `article-filler`: articles whose body contains banned filler phrases. */
  def articleFiller(articles: List[(String, String)]): List[LintItem] =
    articles.flatMap { (slug, body) =>
      val matches = FillerPattern.findAllMatchIn(body).map(_.matched.toLowerCase).toList.distinct
      if matches.isEmpty then None
      else Some(LintItem(slug, s"filler: ${matches.take(3).mkString(", ")}${if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""}"))
    }.sortBy(_.citekey)

  /** A body paragraph is text between blank lines (or at the start/end of the body). We skip
    * headings (`# …`), list items (`- …`), blockquotes (`> …`), metadata lines (`_…_`), and
    * code blocks. A paragraph without any `[[citekey]]` is uncited. */
  private val WikilinkCitekey = """\[\[([a-z][a-z0-9]*_[a-z0-9_]+)""".r

  def articleUncitedParagraphs(articles: List[(String, String)]): List[LintItem] =
    articles.flatMap { (slug, body) =>
      val paras = extractParagraphs(body)
      val uncited = paras.filter(p => WikilinkCitekey.findFirstIn(p).isEmpty)
      if uncited.isEmpty then None
      else Some(LintItem(slug, s"${uncited.size} paragraph(s) with no [[citekey]]: \"${uncited.head.take(60)}…\""))
    }.sortBy(_.citekey)

  /** Extract prose paragraphs from an article body, skipping headings, lists, quotes, metadata, fences. */
  def extractParagraphs(body: String): List[String] =
    val lines = body.linesIterator.toList
    var inFence = false
    val paras   = List.newBuilder[String]
    val current = new StringBuilder()
    def flush(): Unit =
      val text = current.toString.trim
      if text.nonEmpty then paras += text
      current.clear()
    for line <- lines do
      if line.trim.startsWith("```") then
        flush()
        inFence = !inFence
      else if inFence then () // skip fenced code
      else if line.trim.isEmpty then flush()
      else if line.trim.startsWith("#") then flush() // heading
      else if line.trim.startsWith("- ") || line.trim.startsWith("* ") then flush() // list
      else if line.trim.startsWith("> ") then flush() // blockquote
      else if line.trim.startsWith("_") && line.trim.endsWith("_") then flush() // metadata line
      else if line.trim.startsWith("|") then flush() // table
      else current.append(line).append(" ")
    flush()
    paras.result()

  /** Assemble one rule's verdict, capping the listed items (never the count). */
  def report(r: Rule, items: List[LintItem], maxItems: Int): LintRuleReport =
    LintRuleReport(r.name, r.description, items.size, items.take(math.max(0, maxItems)))

  // ── rendering ────────────────────────────────────────────────────────────────────────────────

  /** The Markdown every surface prints: grouped by rule, with counts, clean rules folded into one
    * line so a healthy corpus is a short report. (Lives here rather than in `Markdown` so the lint
    * stream owns one file — see AGENTS.md "one implementation per operation".) */
  def markdown(rep: LintReport): String =
    val fixed = if rep.fixed.isEmpty then "" else rep.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    val (clean, dirty) = rep.rules.partition(_.count == 0)
    val head =
      if dirty.isEmpty then s"### Lint: clean (${rep.rules.size} rule(s) checked)"
      else s"### Lint: ${rep.total} finding(s) across ${dirty.size} of ${rep.rules.size} rule(s)"
    val blocks = dirty.map { r =>
      val items = r.items.map(i => s"- **${i.citekey}** — ${i.detail}")
      val more  = if r.count > r.items.size then s"\n- _… and ${r.count - r.items.size} more_" else ""
      s"\n\n#### ${r.rule} (${r.count})\n_${r.description}_\n\n${items.mkString("\n")}$more"
    }
    val cleanLine =
      if clean.isEmpty then ""
      else s"\n\n_clean: ${clean.map(_.rule).mkString(", ")}_"
    s"$fixed$head${blocks.mkString}$cleanLine"
