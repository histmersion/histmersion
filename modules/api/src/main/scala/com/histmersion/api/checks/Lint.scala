package com.histmersion
package api.checks

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.maintenance.ReindexResult

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Lint:
  type AbbreviationsText = AbbreviationsText.T
  object AbbreviationsText extends Newtype[String, Pure]
  type DuplicateKey = DuplicateKey.T
  object DuplicateKey extends Newtype[String, Pure]
  type FormatItemDetail = FormatItemDetail.T
  object FormatItemDetail extends Newtype[String, Pure]
  type IssueCount = IssueCount.T
  object IssueCount extends Newtype[Int, Pure]
  type ItemLimit = ItemLimit.T
  object ItemLimit extends Newtype[Int, Pure]
  type Paragraph = Paragraph.T
  object Paragraph extends Newtype[String, Pure]
  type ProseBody = ProseBody.T
  object ProseBody extends Newtype[String, Pure]
  type SentenceCount = SentenceCount.T
  object SentenceCount extends Newtype[Int, Pure]
  type Suggestion = Suggestion.T
  object Suggestion extends Newtype[String, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class LintItem(citekey: Citekey, detail: LintItem.Detail)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object LintItem:
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]:
      private val FillerMark = "filler: "

      /** The detail of an article with banned filler: the first matches, and how many more. */
      def filler(matches: List[String]): Detail =
        Detail(s"$FillerMark${matches.take(3).mkString(", ")}${
            if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
          }")

      extension (d: Detail)
        /** The matches of a filler detail, as [[filler]] wrote them. */
        def fillerMatches: String = d.value.stripPrefix(FillerMark)

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class LintRuleReport(
      rule: LintRuleReport.Rule,
      description: LintRuleReport.Description,
      count: LintRuleReport.Count,
      items: List[LintItem]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object LintRuleReport:
    export Lint.Rule.{Description, Name as Rule}
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class LintReport(rules: List[LintRuleReport], fixed: List[LintReport.Fixed] = Nil)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def total: IssueCount = IssueCount(rules.map(r => (r.count).value).sum)

  object LintReport:

    type Fixed = Fixed.T
    object Fixed extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class LintFixResult(
      applied: List[LintFixResult.Applied],
      edgesResolved: LintFixResult.EdgesResolved,
      sourcesReindexed: LintFixResult.SourcesReindexed,
      chunksWritten: LintFixResult.ChunksWritten,
      remaining: LintFixResult.Remaining,
      findingsRegeocoded: LintFixResult.FindingsRegeocoded = LintFixResult.FindingsRegeocoded(0),
      findingsPlaced: LintFixResult.FindingsPlaced = LintFixResult.FindingsPlaced(0),
      placesReclassified: LintFixResult.PlacesReclassified = LintFixResult.PlacesReclassified(0),
      placesMerged: LintFixResult.PlacesMerged = LintFixResult.PlacesMerged(0),
      placesRelocated: LintFixResult.PlacesRelocated = LintFixResult.PlacesRelocated(0),
      pointAreasRemoved: LintFixResult.PointAreasRemoved = LintFixResult.PointAreasRemoved(0),
      pointAreasRemaining: LintFixResult.PointAreasRemaining = LintFixResult.PointAreasRemaining(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object LintFixResult:
    type Applied = Applied.T
    object Applied extends Newtype[String, Pure]
    type EdgesResolved = EdgesResolved.T
    object EdgesResolved extends Newtype[Int, Pure]
    type SourcesReindexed = SourcesReindexed.T
    object SourcesReindexed extends Newtype[Int, Pure]
    type ChunksWritten = ChunksWritten.T
    object ChunksWritten extends Newtype[Int, Pure]
    export ReindexResult.Remaining
    type FindingsRegeocoded = FindingsRegeocoded.T
    object FindingsRegeocoded extends Newtype[Int, Pure]
    type FindingsPlaced = FindingsPlaced.T
    object FindingsPlaced extends Newtype[Int, Pure]
    type PlacesReclassified = PlacesReclassified.T
    object PlacesReclassified extends Newtype[Int, Pure]
    type PlacesMerged = PlacesMerged.T
    object PlacesMerged extends Newtype[Int, Pure]
    type PlacesRelocated = PlacesRelocated.T
    object PlacesRelocated extends Newtype[Int, Pure]
    type PointAreasRemoved = PointAreasRemoved.T
    object PointAreasRemoved extends Newtype[Int, Pure]
    type PointAreasRemaining = PointAreasRemaining.T
    object PointAreasRemaining extends Newtype[Int, Pure]

  final case class Rule(name: Rule.Name, description: Rule.Description)

  object Rule:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Description = Description.T
    object Description extends Newtype[String, Pure]

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Rule] = List(
    Rule(
      Rule.Name(DanglingEdges),
      Rule.Description(
        "edge targets that are not citekeys — dead references and unresolved [[wikilinks]]; the ones SHAPED like a citekey (a four-digit year) are listed first, because the rest are links to concept articles"
      )
    ),
    Rule(
      Rule.Name(UnresolvedShortCitekeys),
      Rule.Description(
        "dangling edge targets that ARE a prefix of one or more real citekeys — `lint --fix` rewrites the unambiguous ones"
      )
    ),
    Rule(Rule.Name(NoFindings), Rule.Description("sources with no findings mined out of them yet (un-enriched)")),
    Rule(Rule.Name(NoPeriod), Rule.Description("sources with no deep-time period, so they appear on no timeline")),
    Rule(Rule.Name(NoRegions), Rule.Description("sources with no regions, so they appear in no region tree")),
    Rule(Rule.Name(EmptySummary), Rule.Description("sources whose summary is missing or blank")),
    Rule(Rule.Name(UnassessedStatus), Rule.Description("sources still at status=unassessed")),
    Rule(Rule.Name(DuplicateDoi), Rule.Description("the same DOI stored under more than one citekey")),
    Rule(
      Rule.Name(DuplicateSha256),
      Rule.Description(
        "the same file hash stored under more than one citekey (skipped while the column is unpopulated)"
      )
    ),
    Rule(
      Rule.Name(OrphanSources),
      Rule.Description("sources with no incoming and no outgoing edges — cited by nothing, citing nothing")
    ),
    Rule(
      Rule.Name(StaleChunks),
      Rule.Description("sources that have a body but no full-text chunks — `lint --fix` reindexes them")
    ),
    Rule(
      Rule.Name(UndatedFindings),
      Rule.Description("findings with no period, so they are invisible to every time filter")
    ),
    Rule(
      Rule.Name(UnlocatedFindings),
      Rule.Description("findings with neither coordinates nor a region — invisible on the map")
    ),
    Rule(
      Rule.Name(LegacyFigureLinks),
      Rule.Description(
        "bodies whose figures still point at a `figures/…` path (usually the converter's own absolute path) instead of the canonical `assets/…` — they render as 0×0 images; re-import the note (`histmersion seed --force`) to rewrite them"
      )
    ),
    Rule(
      Rule.Name(DanglingArticleLinks),
      Rule.Description(
        "`[[wikilinks]]` in an ARTICLE that resolve to nothing — neither a compiled article slug nor a source citekey (nor a unique short form of one); they render as plain text, so the reader sees a name with no page behind it"
      )
    ),
    Rule(Rule.Name(NonCanonicalTag), Rule.Description("tags not in lowercase_snake_case (the canonical form)")),
    Rule(Rule.Name(MissingYear), Rule.Description("sources with no publication year — rendered as n.d.")),
    Rule(
      Rule.Name(YearCitekeyMismatch),
      Rule.Description("the four-digit year in the citekey does not match the stored publication year")
    ),
    Rule(
      Rule.Name(AuthorPipe),
      Rule.Description("author names containing a pipe '|' — likely a parse artifact (\"Cline| Eric H.\")")
    ),
    Rule(
      Rule.Name(ArticleFiller),
      Rule.Description("articles whose body contains banned filler phrases from the writing guide")
    ),
    Rule(Rule.Name(ArticleUncitedParagraph), Rule.Description("article body paragraphs with no [[citekey]] citation")),
    Rule(
      Rule.Name(ArticleThinSummary),
      Rule.Description(
        s"article summaries that are missing, a stand-in (\"placeholder\", TODO…) or shorter than $MinSummaryChars characters — the card and the search hit show nothing useful"
      )
    ),
    Rule(
      Rule.Name(ArticleCritiquePairing),
      Rule.Description(
        "article paragraphs that cite one side of a disagreement edge (challenges, refutes, updates, superseded_by) and name the other side's author without citing it — the critique is written but the pair stays open; `lint --rule article-critique-pairing --fix` appends the missing citekey where the name points at exactly one work"
      )
    ),
    Rule(
      Rule.Name(SupersededWithoutEdge),
      Rule.Description("sources with status=superseded but no superseded_by edge — set the edge or change the status")
    ),
    Rule(
      Rule.Name(ArticleFormat),
      Rule.Description(
        "article bodies and summaries whose Markdown will not display as written — tables shown as pipes, unpaired ** or `, links that are not links, malformed [[wikilinks]], pasted frontmatter, headings without a space, chatter and placeholders; one line per problem (`histmersion article lint <slug> --fix` repairs the safe ones)"
      )
    ),
    Rule(
      Rule.Name(NoteFormat),
      Rule.Description(
        "the same formatting check over each source's summary and notes (`histmersion lint --source <citekey> --fix`)"
      )
    ),
    Rule(
      Rule.Name(FindingGeocode),
      Rule.Description(
        "findings whose point the OLD geocoder chose for their place name (before #981 it let an alias, a placeholder point or the wrong dated snapshot win) and that the current ranking puts elsewhere; hand-placed points are never listed — `lint --rule finding-geocode --fix` moves them"
      )
    ),
    Rule(
      Rule.Name(FindingUnplaced),
      Rule.Description(
        "findings that name a place but have no point, where the gazetteer now knows that name (a re-seed added the English names: Dead Sea, Balkans, Kraków…) — `lint --rule finding-unplaced --fix` gives them the geocoder's point; a finding that has a point is never touched"
      )
    ),
    Rule(
      Rule.Name(PlaceKindRule),
      Rule.Description(
        "point-only seed regions that are something else, by their Pleiades types and linked Wikidata item: a people or civic tribe (→ people), a province or diocese (→ polity, with a period), a settlement (→ site), a place Pleiades withdrew as a duplicate (→ merged into its replacement); one line each: id, label, current → proposed kind, why, evidence — `lint --rule place-kind --fix` applies them and re-matches findings to places"
      )
    ),
    Rule(
      Rule.Name(PlaceGridPoint),
      Rule.Description(
        "Pleiades places whose point is only the centre of an atlas grid square, where a linked record (Wikidata's coordinate) gives a real point — `lint --rule place-grid-point --fix` moves them there and re-matches findings to places"
      )
    ),
    Rule(
      Rule.Name(PlaceImportedPointArea),
      Rule.Description(
        "GeoNames and Wikidata places of an area kind (region, polity, people: `places.sources.point-area-kinds`) stored with only a point — no shape, so no region; the importer no longer keeps them. Listed: the counts by source, kind and dating, every one that holds a finding (and how many of those findings it is the only place of), then a sample — `lint --rule place-imported-point-area --fix` removes them with their names, links and finding matches, in batches, and matches the affected sources' findings again"
      )
    ),
    Rule(
      Rule.Name(FindingPlaceholder),
      Rule.Description(
        "findings whose point is a placeholder (an atlas grid square's centre, or the point the geocoder gives a region) and whose place name the gazetteer cannot resolve, or that have none — no site takes them, and the areas that do are only those covering the point; give the finding a place name the gazetteer knows (or the place a name with `places name add`), or a real point"
      )
    ),
    Rule(
      Rule.Name(MediaLicenceRule),
      Rule.Description(
        "stored pictures with no licence, or a provider's picture with no credit line — they cannot be shown as their licence requires; a source's figure is listed as `<citekey>/<name>` (it takes its source's licence with `histmersion media fill-licences` once the source records one), a provider's as `media:<uid>` (re-import it with `histmersion media import wikidata --refresh`); a book cover (Calibre, Open Library) needs a credit line only — it is the publisher's picture of the user's own copy, shown locally and never exported"
      )
    ),
    Rule(
      Rule.Name(EventCorrectionConflict),
      Rule.Description(
        "catalogue events a person corrected by hand whose catalogue has since changed the same field to something else — the person's value is still applied; review each (`histmersion events corrections --status conflict`) and either keep it (patch it again) or take the catalogue's (patch the field to the catalogue's value, which retires the correction)"
      )
    )
  )

  val MinSummaryChars = 80

  val names: List[Rule.Name] = all.map(_.name)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rule(name: Rule.Name): Option[Rule] = all.find(_.name == Rule.Name((name).value.trim.toLowerCase))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def select(requested: List[Rule.Name]): Either[Problem, List[Rule]] =
    val wanted = requested.map((_).value.trim).filter(_.nonEmpty)
    if wanted.isEmpty then Right(all)
    else
      val unknown = wanted.filter(w => rule(Rule.Name(w)).isEmpty)
      if unknown.nonEmpty then
        Left(
          Problem.Invalid.Unknown.here(
            Problem.TypeName.of(Rule.Name),
            Problem.Raw(s"${unknown.mkString(", ")}"),
            names.toVector.map(n => Problem.Raw(s"$n"))
          )
        )
      else
        Right(wanted.flatMap {
          val refactorFunction = rule; (refactorValue) => refactorFunction(Rule.Name(refactorValue))
        }.distinct)

  def blank(s: Option[String]): Boolean = (s.forall((_).trim.isEmpty))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def suggest(target: Citekey, citekeys: Iterable[Citekey], max: ItemLimit): List[Suggestion] =
    val t = (target).value.trim.toLowerCase
    if t.isEmpty || (max).value <= 0 then Nil
    else
      def commonPrefix(a: String, b: String): Int =
        val n = math.min(a.length, b.length)
        var i = 0
        while i < n && a.charAt(i) == b.charAt(i) do i += 1
        i
      val scored = citekeys.iterator
        .filter(ck => (ck) != (target))
        .flatMap { ck =>
          val c = (ck).value.toLowerCase
          val p = commonPrefix(c, t)
          if c.startsWith(t + "_") then Some((0, c.length, ck))
          else if c.startsWith(t) || t.startsWith(c) then Some((1, -(p), ck))
          else if p >= 4 then Some((2, -(p), ck))
          else None
        }
        .toList
      (scored
        .sortBy((rank, negLen, ck) => (rank, negLen, (ck).value))
        .map(_._3)
        .take((max).value))
        .map(refactorValue3 => Suggestion((refactorValue3).value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def looksLikeCitekey(target: Citekey): Boolean = ("""\d{4}""".r.findFirstIn((target).value).isDefined)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def duplicates(rows: List[(Citekey, DuplicateKey)], label: String): List[LintItem] =
    rows
      .filter((_, key) => (key).value.trim.nonEmpty)
      .groupBy((_, key) => (key).value.trim.toLowerCase)
      .toList
      .filter((_, group) => group.map(_._1).distinct.sizeIs > 1)
      .sortBy((key, _) => key)
      .flatMap { (key, group) =>
        val cks = group.map(_._1).distinct.sorted
        cks.map(ck => LintItem(ck, LintItem.Detail(s"$label $key — also on ${cks.filterNot(_ == ck).mkString(", ")}")))
      }

  private val CanonicalTag = """^[a-z0-9][a-z0-9_]*$""".r

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isCanonicalTag(tag: Tag): Boolean = (CanonicalTag.matches((tag).value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normaliseTag(tag: Tag): Tag =
    Tag(
      (tag).value.trim.toLowerCase.replaceAll("""[\s-]+""", "_").replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def nonCanonicalTags(rows: List[(Citekey, List[Tag])]): List[LintItem] =
    rows
      .flatMap { (ck, tags) =>
        tags.filterNot(isCanonicalTag).map(t => LintItem(ck, LintItem.Detail(s"tag '$t' → '${normaliseTag(t)}'")))
      }
      .sortBy(i => (i.citekey, i.detail))

  private val YearInCitekey = """(\d{4})""".r.unanchored

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def yearCitekeyMismatch(rows: List[(Citekey, Option[Year])]): List[LintItem] =
    rows
      .flatMap { (ck, yr) =>
        yr.flatMap { y =>
          YearInCitekey.findFirstMatchIn((ck).value).flatMap { m =>
            val inKey = m.group(1).toInt
            if inKey == (y).value then None
            else Some(LintItem(ck, LintItem.Detail(s"citekey has $inKey but stored year is $y")))
          }
        }
      }
      .sortBy(i => (i.citekey).value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def authorPipe(rows: List[(Citekey, List[AuthorName])]): List[LintItem] =
    rows
      .flatMap { (ck, authors) =>
        authors.filter((_).value.contains("|")).map(a => LintItem(ck, LintItem.Detail(s"author '$a'")))
      }
      .sortBy(i => (i.citekey, i.detail))

  val MaxListLeadIn = 300

  private val Abbreviations: Set[AbbreviationsText] =
    Set(
      AbbreviationsText("c"),
      AbbreviationsText("ca"),
      AbbreviationsText("cf"),
      AbbreviationsText("e.g"),
      AbbreviationsText("i.e"),
      AbbreviationsText("etc"),
      AbbreviationsText("vs"),
      AbbreviationsText("al"),
      AbbreviationsText("fl"),
      AbbreviationsText("st"),
      AbbreviationsText("mt"),
      AbbreviationsText("no"),
      AbbreviationsText("vol"),
      AbbreviationsText("pp"),
      AbbreviationsText("p"),
      AbbreviationsText("ed"),
      AbbreviationsText("eds"),
      AbbreviationsText("dr"),
      AbbreviationsText("fig")
    )

  private val SentenceEnd = "([^\\s]*)[.!?]+[\"”’)]*\\s+(?=(\\S))".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sentenceCount(text: String): SentenceCount =
    val t = (text).trim
    if t.isEmpty then SentenceCount(0)
    else
      // No `\p{Lu}` in the pattern: this code is also compiled to Scala.js, so the next char is tested instead.
      val ends = SentenceEnd.findAllMatchIn(t).count { m =>
        val next = m.group(2).head
        val word = m.group(1).replaceAll("^[(\"“]+", "").toLowerCase
        (next.isUpper || "[“\"(".contains(next)) && !(Abbreviations(
          AbbreviationsText(word)
        ) || (word.length == 1 && word.head.isLetter))
      }
      SentenceCount(ends + 1)

  def isListLeadIn(text: String): Boolean =
    val t = (text).trim
    (t.endsWith(":") && t.length <= MaxListLeadIn && sentenceCount((t)) == SentenceCount(1))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def extractParagraphs(body: ProseBody): List[Paragraph] =
    val lines = (body).value.linesIterator.toList
    var inFence = false
    val paras = List.newBuilder[String]
    val current = new StringBuilder()
    var leadIn: Option[String] = None
    def settle(nextIsList: Boolean): Unit =
      leadIn.foreach(t => if !(nextIsList) then paras += (t))
      leadIn = None
    def flush(): Unit =
      val text = current.toString.trim
      if text.nonEmpty then if isListLeadIn((text)) then leadIn = Some((text)) else paras += text
      current.clear()
    def isList(t: String) = (t).startsWith("- ") || (t).startsWith("* ") || "^\\d+[.)] ".r.findPrefixOf((t)).isDefined
    def block(nextIsList: Boolean): Unit = { flush(); settle(nextIsList) }
    for line <- lines do
      val t = line.trim
      if t.startsWith("```") then
        block(nextIsList = (false))
        inFence = !inFence
      else if inFence then ()
      else if t.isEmpty then flush()
      else if t.startsWith("#") then block(nextIsList = (false))
      else if isList((t)) then block(nextIsList = (true))
      else if t.startsWith("> ") then block(nextIsList = (false))
      else if t.startsWith("_") && t.endsWith("_") then block(nextIsList = (false))
      else if t.startsWith("|") then block(nextIsList = (false))
      else
        if current.isEmpty then settle(nextIsList = (false))
        current.append(line).append(" ")
    block(nextIsList = (false))
    (paras.result()).map(refactorValue2 => Paragraph(refactorValue2))

  type FormatChecker = (String, FormatField, Boolean) => List[FormatIssue]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def formatItems(rows: List[(Citekey, FormatField, FormatItemDetail)], check: FormatChecker): List[LintItem] =
    rows.filter(r => (r._3).value.trim.nonEmpty).sortBy((id, f, _) => ((id).value, f.ordinal)).flatMap {
      (id, field, text) =>
        check((text).value, field, field == FormatField.Body)
          .map(i => LintItem(id, LintItem.Detail(FormatMarkdown.line(i).value)))
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def report(r: Rule, items: List[LintItem], maxItems: ItemLimit): LintRuleReport =
    LintRuleReport(r.name, r.description, LintRuleReport.Count(items.size), items.take(math.max(0, (maxItems).value)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(rep: LintReport): Markdown =
    val fixed = if rep.fixed.isEmpty then "" else rep.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    val (clean, dirty) = rep.rules.partition(_.count == LintRuleReport.Count(0))
    val head =
      if dirty.isEmpty then s"### Lint: clean (${rep.rules.size} rule(s) checked)"
      else s"### Lint: ${rep.total} finding(s) across ${dirty.size} of ${rep.rules.size} rule(s)"
    val blocks = dirty.map { r =>
      val items = r.items.map(i => s"- **${i.citekey}** — ${i.detail}")
      val more = if (r.count).value > r.items.size then s"\n- _… and ${(r.count).value - r.items.size} more_" else ""
      s"\n\n#### ${r.rule} (${r.count})\n_${r.description}_\n\n${items.mkString("\n")}$more"
    }
    val cleanLine =
      if clean.isEmpty then ""
      else s"\n\n_clean: ${clean.map(_.rule).mkString(", ")}_"
    Markdown(s"$fixed$head${blocks.mkString}$cleanLine")
