package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Lint:

  final case class LintItem(citekey: String, detail: String) derives KindlingsCodecAsObject, KindlingsSchema
  object LintItem:
    given Schema[LintItem] = summon[KindlingsSchema[LintItem]].schema

  final case class LintRuleReport(rule: String, description: String, count: Int, items: List[LintItem])
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object LintRuleReport:
    given Schema[LintRuleReport] = summon[KindlingsSchema[LintRuleReport]].schema

  final case class LintReport(rules: List[LintRuleReport], fixed: List[String] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def total: Int = rules.map(_.count).sum
  object LintReport:
    given Schema[LintReport] = summon[KindlingsSchema[LintReport]].schema

  final case class LintFixResult(
      applied: List[String],
      edgesResolved: Int,
      sourcesReindexed: Int,
      chunksWritten: Int,
      remaining: Int,
      findingsRegeocoded: Int = 0,
      findingsPlaced: Int = 0,
      placesReclassified: Int = 0,
      placesMerged: Int = 0,
      placesRelocated: Int = 0,
      pointAreasRemoved: Int = 0,
      pointAreasRemaining: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object LintFixResult:
    given Schema[LintFixResult] = summon[KindlingsSchema[LintFixResult]].schema

  final case class Rule(name: String, description: String)

  val DanglingEdges = "dangling-edges"
  val UnresolvedShortCitekeys = "unresolved-short-citekeys"
  val NoFindings = "no-findings"
  val NoPeriod = "no-period"
  val NoRegions = "no-regions"
  val EmptySummary = "empty-summary"
  val UnassessedStatus = "unassessed-status"
  val DuplicateDoi = "duplicate-doi"
  val DuplicateSha256 = "duplicate-sha256"
  val OrphanSources = "orphan-sources"
  val StaleChunks = "stale-chunks"
  val UndatedFindings = "undated-findings"
  val UnlocatedFindings = "unlocated-findings"
  val LegacyFigureLinks = "legacy-figure-links"
  val DanglingArticleLinks = "dangling-article-links"
  val NonCanonicalTag = "non-canonical-tag"
  val MissingYear = "missing-year"
  val YearCitekeyMismatch = "year-citekey-mismatch"
  val AuthorPipe = "author-pipe"
  val ArticleFiller = "article-filler"
  val ArticleUncitedParagraph = "article-uncited-paragraph"
  val ArticleThinSummary = "article-thin-summary"
  val SupersededWithoutEdge = "superseded-without-edge"
  val ArticleCritiquePairing = "article-critique-pairing"
  val ArticleFormat = "article-markdown"
  val NoteFormat = "note-markdown"
  val FindingGeocode = "finding-geocode"
  val FindingUnplaced = "finding-unplaced"
  val PlaceKindRule = "place-kind"
  val PlaceGridPoint = "place-grid-point"
  val PlaceImportedPointArea = "place-imported-point-area"
  val FindingPlaceholder = "finding-placeholder"
  val MediaLicenceRule = "media-licence"
  val EventCorrectionConflict = "event-correction-conflict"

  val all: List[Rule] = List(
    Rule(
      DanglingEdges,
      "edge targets that are not citekeys — dead references and unresolved [[wikilinks]]; the ones SHAPED like a citekey (a four-digit year) are listed first, because the rest are links to concept articles"
    ),
    Rule(
      UnresolvedShortCitekeys,
      "dangling edge targets that ARE a prefix of one or more real citekeys — `lint --fix` rewrites the unambiguous ones"
    ),
    Rule(NoFindings, "sources with no findings mined out of them yet (un-enriched)"),
    Rule(NoPeriod, "sources with no deep-time period, so they appear on no timeline"),
    Rule(NoRegions, "sources with no regions, so they appear in no region tree"),
    Rule(EmptySummary, "sources whose summary is missing or blank"),
    Rule(UnassessedStatus, "sources still at status=unassessed"),
    Rule(DuplicateDoi, "the same DOI stored under more than one citekey"),
    Rule(
      DuplicateSha256,
      "the same file hash stored under more than one citekey (skipped while the column is unpopulated)"
    ),
    Rule(OrphanSources, "sources with no incoming and no outgoing edges — cited by nothing, citing nothing"),
    Rule(StaleChunks, "sources that have a body but no full-text chunks — `lint --fix` reindexes them"),
    Rule(UndatedFindings, "findings with no period, so they are invisible to every time filter"),
    Rule(UnlocatedFindings, "findings with neither coordinates nor a region — invisible on the map"),
    Rule(
      LegacyFigureLinks,
      "bodies whose figures still point at a `figures/…` path (usually the converter's own absolute path) instead of the canonical `assets/…` — they render as 0×0 images; re-import the note (`histmersion seed --force`) to rewrite them"
    ),
    Rule(
      DanglingArticleLinks,
      "`[[wikilinks]]` in an ARTICLE that resolve to nothing — neither a compiled article slug nor a source citekey (nor a unique short form of one); they render as plain text, so the reader sees a name with no page behind it"
    ),
    Rule(NonCanonicalTag, "tags not in lowercase_snake_case (the canonical form)"),
    Rule(MissingYear, "sources with no publication year — rendered as n.d."),
    Rule(YearCitekeyMismatch, "the four-digit year in the citekey does not match the stored publication year"),
    Rule(AuthorPipe, "author names containing a pipe '|' — likely a parse artifact (\"Cline| Eric H.\")"),
    Rule(ArticleFiller, "articles whose body contains banned filler phrases from the writing guide"),
    Rule(ArticleUncitedParagraph, "article body paragraphs with no [[citekey]] citation"),
    Rule(
      ArticleThinSummary,
      s"article summaries that are missing, a stand-in (\"placeholder\", TODO…) or shorter than $MinSummaryChars characters — the card and the search hit show nothing useful"
    ),
    Rule(
      ArticleCritiquePairing,
      "article paragraphs that cite one side of a disagreement edge (challenges, refutes, updates, superseded_by) and name the other side's author without citing it — the critique is written but the pair stays open; `lint --rule article-critique-pairing --fix` appends the missing citekey where the name points at exactly one work"
    ),
    Rule(
      SupersededWithoutEdge,
      "sources with status=superseded but no superseded_by edge — set the edge or change the status"
    ),
    Rule(
      ArticleFormat,
      "article bodies and summaries whose Markdown will not display as written — tables shown as pipes, unpaired ** or `, links that are not links, malformed [[wikilinks]], pasted frontmatter, headings without a space, chatter and placeholders; one line per problem (`histmersion article lint <slug> --fix` repairs the safe ones)"
    ),
    Rule(
      NoteFormat,
      "the same formatting check over each source's summary and notes (`histmersion lint --source <citekey> --fix`)"
    ),
    Rule(
      FindingGeocode,
      "findings whose point the OLD geocoder chose for their place name (before #981 it let an alias, a placeholder point or the wrong dated snapshot win) and that the current ranking puts elsewhere; hand-placed points are never listed — `lint --rule finding-geocode --fix` moves them"
    ),
    Rule(
      FindingUnplaced,
      "findings that name a place but have no point, where the gazetteer now knows that name (a re-seed added the English names: Dead Sea, Balkans, Kraków…) — `lint --rule finding-unplaced --fix` gives them the geocoder's point; a finding that has a point is never touched"
    ),
    Rule(
      PlaceKindRule,
      "point-only seed regions that are something else, by their Pleiades types and linked Wikidata item: a people or civic tribe (→ people), a province or diocese (→ polity, with a period), a settlement (→ site), a place Pleiades withdrew as a duplicate (→ merged into its replacement); one line each: id, label, current → proposed kind, why, evidence — `lint --rule place-kind --fix` applies them and re-matches findings to places"
    ),
    Rule(
      PlaceGridPoint,
      "Pleiades places whose point is only the centre of an atlas grid square, where a linked record (Wikidata's coordinate) gives a real point — `lint --rule place-grid-point --fix` moves them there and re-matches findings to places"
    ),
    Rule(
      PlaceImportedPointArea,
      "GeoNames and Wikidata places of an area kind (region, polity, people: `places.sources.point-area-kinds`) stored with only a point — no shape, so no region; the importer no longer keeps them. Listed: the counts by source, kind and dating, every one that holds a finding (and how many of those findings it is the only place of), then a sample — `lint --rule place-imported-point-area --fix` removes them with their names, links and finding matches, in batches, and matches the affected sources' findings again"
    ),
    Rule(
      FindingPlaceholder,
      "findings whose point is a placeholder (an atlas grid square's centre, or the point the geocoder gives a region) and whose place name the gazetteer cannot resolve, or that have none — no site takes them, and the areas that do are only those covering the point; give the finding a place name the gazetteer knows (or the place a name with `places name add`), or a real point"
    ),
    Rule(
      MediaLicenceRule,
      "stored pictures with no licence, or a provider's picture with no credit line — they cannot be shown as their licence requires; a source's figure is listed as `<citekey>/<name>` (it takes its source's licence with `histmersion media fill-licences` once the source records one), a provider's as `media:<uid>` (re-import it with `histmersion media import wikidata --refresh`); a book cover (Calibre, Open Library) needs a credit line only — it is the publisher's picture of the user's own copy, shown locally and never exported"
    ),
    Rule(
      EventCorrectionConflict,
      "catalogue events a person corrected by hand whose catalogue has since changed the same field to something else — the person's value is still applied; review each (`histmersion events corrections --status conflict`) and either keep it (patch it again) or take the catalogue's (patch the field to the catalogue's value, which retires the correction)"
    )
  )

  val names: List[String] = all.map(_.name)

  def rule(name: String): Option[Rule] = all.find(_.name == name.trim.toLowerCase)

  def select(requested: List[String]): Either[String, List[Rule]] =
    val wanted = requested.map(_.trim).filter(_.nonEmpty)
    if wanted.isEmpty then Right(all)
    else
      val unknown = wanted.filter(w => rule(w).isEmpty)
      if unknown.nonEmpty then
        Left(s"unknown lint rule(s): ${unknown.mkString(", ")} — known rules: ${names.mkString(", ")}")
      else Right(wanted.flatMap(rule).distinct)

  def blank(s: Option[String]): Boolean = s.forall(_.trim.isEmpty)

  def suggest(target: String, citekeys: Iterable[String], max: Int): List[String] =
    val t = target.trim.toLowerCase
    if t.isEmpty || max <= 0 then Nil
    else
      def commonPrefix(a: String, b: String): Int =
        val n = math.min(a.length, b.length)
        var i = 0
        while i < n && a.charAt(i) == b.charAt(i) do i += 1
        i
      val scored = citekeys.iterator
        .filter(_ != target)
        .flatMap { ck =>
          val c = ck.toLowerCase
          val p = commonPrefix(c, t)
          if c.startsWith(t + "_") then Some((0, c.length, ck))
          else if c.startsWith(t) || t.startsWith(c) then Some((1, -p, ck))
          else if p >= 4 then Some((2, -p, ck))
          else None
        }
        .toList
      scored.sortBy((rank, negLen, ck) => (rank, negLen, ck)).map(_._3).take(max)

  def looksLikeCitekey(target: String): Boolean = """\d{4}""".r.findFirstIn(target).isDefined

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

  def unresolvedShortCitekeys(edges: List[Edge], citekeys: Set[String], maxSuggestions: Int): List[LintItem] =
    edges.distinct
      .filterNot(e => citekeys.contains(e.dst))
      .flatMap { e =>
        val near = citekeys.filter(ck => ck.startsWith(e.dst + "_")).toList.sorted
        if near.isEmpty then None
        else
          val shown = near.take(maxSuggestions)
          val more = if near.sizeIs > shown.size then s", +${near.size - shown.size} more" else ""
          val verdict =
            if near.sizeIs == 1 then s"resolves to ${near.head} (fixable: lint --fix)"
            else s"ambiguous between ${shown.mkString(", ")}$more"
          Some(LintItem(e.src, s"${e.kind} → ${e.dst} $verdict"))
      }
      .sortBy(i => (i.citekey, i.detail))

  def duplicates(rows: List[(String, String)], label: String): List[LintItem] =
    rows
      .filter((_, key) => key.trim.nonEmpty)
      .groupBy((_, key) => key.trim.toLowerCase)
      .toList
      .filter((_, group) => group.map(_._1).distinct.sizeIs > 1)
      .sortBy((key, _) => key)
      .flatMap { (key, group) =>
        val cks = group.map(_._1).distinct.sorted
        cks.map(ck => LintItem(ck, s"$label $key — also on ${cks.filterNot(_ == ck).mkString(", ")}"))
      }

  private val CanonicalTag = """^[a-z0-9][a-z0-9_]*$""".r

  def isCanonicalTag(tag: String): Boolean = CanonicalTag.matches(tag)

  def normaliseTag(tag: String): String =
    tag.trim.toLowerCase.replaceAll("""[\s-]+""", "_").replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")

  def nonCanonicalTags(rows: List[(String, List[String])]): List[LintItem] =
    rows
      .flatMap { (ck, tags) =>
        tags.filterNot(isCanonicalTag).map(t => LintItem(ck, s"tag '$t' → '${normaliseTag(t)}'"))
      }
      .sortBy(i => (i.citekey, i.detail))

  private val YearInCitekey = """(\d{4})""".r.unanchored

  def yearCitekeyMismatch(rows: List[(String, Option[Int])]): List[LintItem] =
    rows
      .flatMap { (ck, yr) =>
        yr.flatMap { y =>
          YearInCitekey.findFirstMatchIn(ck).flatMap { m =>
            val inKey = m.group(1).toInt
            if inKey == y then None
            else Some(LintItem(ck, s"citekey has $inKey but stored year is $y"))
          }
        }
      }
      .sortBy(_.citekey)

  def authorPipe(rows: List[(String, List[String])]): List[LintItem] =
    rows
      .flatMap { (ck, authors) =>
        authors.filter(_.contains("|")).map(a => LintItem(ck, s"author '$a'"))
      }
      .sortBy(i => (i.citekey, i.detail))

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

  val MinSummaryChars = 80

  def articleThinSummary(rows: List[(String, String)]): List[LintItem] =
    rows
      .flatMap { (slug, summary) =>
        val s = summary.trim
        val why =
          if s.isEmpty then Some("no summary")
          else
            Articles
              .summaryOf(Some(s))
              .left
              .toOption
              .map(_ => s"stand-in summary: \"$s\"")
              .orElse(Option.when(s.length < MinSummaryChars)(s"only ${s.length} chars: \"$s\""))
        why.map(LintItem(slug, _))
      }
      .sortBy(_.citekey)

  def articleFiller(articles: List[(String, String)]): List[LintItem] =
    articles
      .flatMap { (slug, body) =>
        val matches = FillerPattern.findAllMatchIn(body).map(_.matched.toLowerCase).toList.distinct
        if matches.isEmpty then None
        else
          Some(
            LintItem(
              slug,
              s"filler: ${matches.take(3).mkString(", ")}${
                  if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
                }"
            )
          )
      }
      .sortBy(_.citekey)

  private val WikilinkCitekey =
    """\[\[((?=[a-z0-9_]*(?:_|\d{4}))[a-z][a-z0-9_]*)""".r

  def articleUncitedParagraphs(articles: List[(String, String)]): List[LintItem] =
    articles
      .flatMap { (slug, body) =>
        val paras = extractParagraphs(body)
        val uncited = paras.filter(p => WikilinkCitekey.findFirstIn(p).isEmpty)
        if uncited.isEmpty then None
        else Some(LintItem(slug, s"${uncited.size} paragraph(s) with no [[citekey]]: \"${uncited.head.take(60)}…\""))
      }
      .sortBy(_.citekey)

  def articleCritiquePairing(
      articles: List[(String, String)],
      resolve: String => Option[String],
      edges: List[CoverageEdge],
      work: String => Option[CritiquePairing.Work]
  ): List[LintItem] =
    articles
      .flatMap((slug, body) => CritiquePairing.hits(body, resolve, edges, work).map(h => LintItem(slug, h.line)))
      .sortBy(_.citekey)

  val MaxListLeadIn = 300

  private val Abbreviations: Set[String] =
    Set(
      "c",
      "ca",
      "cf",
      "e.g",
      "i.e",
      "etc",
      "vs",
      "al",
      "fl",
      "st",
      "mt",
      "no",
      "vol",
      "pp",
      "p",
      "ed",
      "eds",
      "dr",
      "fig"
    )

  private val SentenceEnd = "([^\\s]*)[.!?]+[\"”’)]*\\s+(?=(\\S))".r

  def sentenceCount(text: String): Int =
    val t = text.trim
    if t.isEmpty then 0
    else
      // No `\p{Lu}` in the pattern: this code is also compiled to Scala.js, so the next char is tested instead.
      val ends = SentenceEnd.findAllMatchIn(t).count { m =>
        val next = m.group(2).head
        val word = m.group(1).replaceAll("^[(\"“]+", "").toLowerCase
        (next.isUpper || "[“\"(".contains(next)) && !(Abbreviations(word) || (word.length == 1 && word.head.isLetter))
      }
      ends + 1

  def isListLeadIn(text: String): Boolean =
    val t = text.trim
    t.endsWith(":") && t.length <= MaxListLeadIn && sentenceCount(t) == 1

  def extractParagraphs(body: String): List[String] =
    val lines = body.linesIterator.toList
    var inFence = false
    val paras = List.newBuilder[String]
    val current = new StringBuilder()
    var leadIn: Option[String] = None
    def settle(nextIsList: Boolean): Unit =
      leadIn.foreach(t => if !nextIsList then paras += t)
      leadIn = None
    def flush(): Unit =
      val text = current.toString.trim
      if text.nonEmpty then if isListLeadIn(text) then leadIn = Some(text) else paras += text
      current.clear()
    def isList(t: String) = t.startsWith("- ") || t.startsWith("* ") || "^\\d+[.)] ".r.findPrefixOf(t).isDefined
    def block(nextIsList: Boolean): Unit = { flush(); settle(nextIsList) }
    for line <- lines do
      val t = line.trim
      if t.startsWith("```") then
        block(nextIsList = false)
        inFence = !inFence
      else if inFence then ()
      else if t.isEmpty then flush()
      else if t.startsWith("#") then block(nextIsList = false)
      else if isList(t) then block(nextIsList = true)
      else if t.startsWith("> ") then block(nextIsList = false)
      else if t.startsWith("_") && t.endsWith("_") then block(nextIsList = false)
      else if t.startsWith("|") then block(nextIsList = false)
      else
        if current.isEmpty then settle(nextIsList = false)
        current.append(line).append(" ")
    block(nextIsList = false)
    paras.result()

  type FormatChecker = (String, FormatField, Boolean) => List[FormatIssue]

  def formatItems(rows: List[(String, FormatField, String)], check: FormatChecker): List[LintItem] =
    rows.filter(_._3.trim.nonEmpty).sortBy((id, f, _) => (id, f.ordinal)).flatMap { (id, field, text) =>
      check(text, field, field == FormatField.Body).map(i => LintItem(id, FormatMarkdown.line(i)))
    }

  def report(r: Rule, items: List[LintItem], maxItems: Int): LintRuleReport =
    LintRuleReport(r.name, r.description, items.size, items.take(math.max(0, maxItems)))

  def markdown(rep: LintReport): String =
    val fixed = if rep.fixed.isEmpty then "" else rep.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    val (clean, dirty) = rep.rules.partition(_.count == 0)
    val head =
      if dirty.isEmpty then s"### Lint: clean (${rep.rules.size} rule(s) checked)"
      else s"### Lint: ${rep.total} finding(s) across ${dirty.size} of ${rep.rules.size} rule(s)"
    val blocks = dirty.map { r =>
      val items = r.items.map(i => s"- **${i.citekey}** — ${i.detail}")
      val more = if r.count > r.items.size then s"\n- _… and ${r.count - r.items.size} more_" else ""
      s"\n\n#### ${r.rule} (${r.count})\n_${r.description}_\n\n${items.mkString("\n")}$more"
    }
    val cleanLine =
      if clean.isEmpty then ""
      else s"\n\n_clean: ${clean.map(_.rule).mkString(", ")}_"
    s"$fixed$head${blocks.mkString}$cleanLine"
