package com.histmersion.api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object Lint:

  type SuggestCommonPrefixResult = SuggestCommonPrefixResult.T
  object SuggestCommonPrefixResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuggestCommonPrefixA = SuggestCommonPrefixA.T
  object SuggestCommonPrefixA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuggestCommonPrefixB = SuggestCommonPrefixB.T
  object SuggestCommonPrefixB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractParagraphsLeadIn = ExtractParagraphsLeadIn.T
  object ExtractParagraphsLeadIn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractParagraphsSettleNextIsList = ExtractParagraphsSettleNextIsList.T
  object ExtractParagraphsSettleNextIsList extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractParagraphsIsListT = ExtractParagraphsIsListT.T
  object ExtractParagraphsIsListT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractParagraphsBlockNextIsList = ExtractParagraphsBlockNextIsList.T
  object ExtractParagraphsBlockNextIsList extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RuleName = RuleName.T
  object RuleName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectResultPart1 = SelectResultPart1.T
  object SelectResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelectRequested = SelectRequested.T
  object SelectRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlankResult = BlankResult.T
  object BlankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlankS = BlankS.T
  object BlankS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuggestResult = SuggestResult.T
  object SuggestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuggestTarget = SuggestTarget.T
  object SuggestTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuggestCitekeys = SuggestCitekeys.T
  object SuggestCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuggestMax = SuggestMax.T
  object SuggestMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LooksLikeCitekeyResult = LooksLikeCitekeyResult.T
  object LooksLikeCitekeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LooksLikeCitekeyTarget = LooksLikeCitekeyTarget.T
  object LooksLikeCitekeyTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicatesRowsPart1 = DuplicatesRowsPart1.T
  object DuplicatesRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicatesRowsPart2 = DuplicatesRowsPart2.T
  object DuplicatesRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicatesLabel = DuplicatesLabel.T
  object DuplicatesLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCanonicalTagResult = IsCanonicalTagResult.T
  object IsCanonicalTagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCanonicalTagTag = IsCanonicalTagTag.T
  object IsCanonicalTagTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseTagResult = NormaliseTagResult.T
  object NormaliseTagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseTagTag = NormaliseTagTag.T
  object NormaliseTagTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NonCanonicalTagsRowsPart1 = NonCanonicalTagsRowsPart1.T
  object NonCanonicalTagsRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NonCanonicalTagsRowsPart2 = NonCanonicalTagsRowsPart2.T
  object NonCanonicalTagsRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearCitekeyMismatchRowsPart1 = YearCitekeyMismatchRowsPart1.T
  object YearCitekeyMismatchRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearCitekeyMismatchRowsPart2 = YearCitekeyMismatchRowsPart2.T
  object YearCitekeyMismatchRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorPipeRowsPart1 = AuthorPipeRowsPart1.T
  object AuthorPipeRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorPipeRowsPart2 = AuthorPipeRowsPart2.T
  object AuthorPipeRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Abbreviations = Abbreviations.T
  object Abbreviations extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SentenceCountResult = SentenceCountResult.T
  object SentenceCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SentenceCountText = SentenceCountText.T
  object SentenceCountText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsListLeadInResult = IsListLeadInResult.T
  object IsListLeadInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsListLeadInText = IsListLeadInText.T
  object IsListLeadInText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractParagraphsResult = ExtractParagraphsResult.T
  object ExtractParagraphsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtractParagraphsBody = ExtractParagraphsBody.T
  object ExtractParagraphsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormatItemsRowsPart1 = FormatItemsRowsPart1.T
  object FormatItemsRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormatItemsRowsPart3 = FormatItemsRowsPart3.T
  object FormatItemsRowsPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportMaxItems = ReportMaxItems.T
  object ReportMaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class LintItem(citekey: com.histmersion.api.library.Citekey, detail: LintItem.Detail) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object LintItem:
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class LintRuleReport(rule: LintRuleReport.Rule, description: LintRuleReport.Description, count: LintRuleReport.Count, items: List[LintItem])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object LintRuleReport:
    type Rule = Rule.T
    object Rule extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Description = Description.T
    object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class LintReport(rules: List[LintRuleReport], fixed: List[LintReport.Fixed] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def total: com.histmersion.api.checks.Lint.LintReport.TotalResult = rules.map(_.count).sum

  object LintReport:
    type TotalResult = TotalResult.T
    object TotalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Fixed = Fixed.T
    object Fixed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class LintFixResult(
      applied: List[LintFixResult.Applied],
      edgesResolved: LintFixResult.EdgesResolved,
      sourcesReindexed: LintFixResult.SourcesReindexed,
      chunksWritten: LintFixResult.ChunksWritten,
      remaining: LintFixResult.Remaining,
      findingsRegeocoded: LintFixResult.FindingsRegeocoded = _root_.com.histmersion.api.checks.Lint.LintFixResult.FindingsRegeocoded(0),
      findingsPlaced: LintFixResult.FindingsPlaced = _root_.com.histmersion.api.checks.Lint.LintFixResult.FindingsPlaced(0),
      placesReclassified: LintFixResult.PlacesReclassified = _root_.com.histmersion.api.checks.Lint.LintFixResult.PlacesReclassified(0),
      placesMerged: LintFixResult.PlacesMerged = _root_.com.histmersion.api.checks.Lint.LintFixResult.PlacesMerged(0),
      placesRelocated: LintFixResult.PlacesRelocated = _root_.com.histmersion.api.checks.Lint.LintFixResult.PlacesRelocated(0),
      pointAreasRemoved: LintFixResult.PointAreasRemoved = _root_.com.histmersion.api.checks.Lint.LintFixResult.PointAreasRemoved(0),
      pointAreasRemaining: LintFixResult.PointAreasRemaining = _root_.com.histmersion.api.checks.Lint.LintFixResult.PointAreasRemaining(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object LintFixResult:
    type Applied = Applied.T
    object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgesResolved = EdgesResolved.T
    object EdgesResolved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcesReindexed = SourcesReindexed.T
    object SourcesReindexed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChunksWritten = ChunksWritten.T
    object ChunksWritten extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Remaining = Remaining.T
    object Remaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsRegeocoded = FindingsRegeocoded.T
    object FindingsRegeocoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsPlaced = FindingsPlaced.T
    object FindingsPlaced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesReclassified = PlacesReclassified.T
    object PlacesReclassified extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesMerged = PlacesMerged.T
    object PlacesMerged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesRelocated = PlacesRelocated.T
    object PlacesRelocated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointAreasRemoved = PointAreasRemoved.T
    object PointAreasRemoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointAreasRemaining = PointAreasRemaining.T
    object PointAreasRemaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Rule(name: Rule.Name, description: Rule.Description)

  object Rule:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Description = Description.T
    object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      _root_.com.histmersion.api.checks.Lint.Rule.Name(DanglingEdges),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("edge targets that are not citekeys — dead references and unresolved [[wikilinks]]; the ones SHAPED like a citekey (a four-digit year) are listed first, because the rest are links to concept articles")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(UnresolvedShortCitekeys),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("dangling edge targets that ARE a prefix of one or more real citekeys — `lint --fix` rewrites the unambiguous ones")
    ),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(NoFindings), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources with no findings mined out of them yet (un-enriched)")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(NoPeriod), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources with no deep-time period, so they appear on no timeline")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(NoRegions), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources with no regions, so they appear in no region tree")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(EmptySummary), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources whose summary is missing or blank")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(UnassessedStatus), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources still at status=unassessed")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(DuplicateDoi), _root_.com.histmersion.api.checks.Lint.Rule.Description("the same DOI stored under more than one citekey")),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(DuplicateSha256),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("the same file hash stored under more than one citekey (skipped while the column is unpopulated)")
    ),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(OrphanSources), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources with no incoming and no outgoing edges — cited by nothing, citing nothing")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(StaleChunks), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources that have a body but no full-text chunks — `lint --fix` reindexes them")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(UndatedFindings), _root_.com.histmersion.api.checks.Lint.Rule.Description("findings with no period, so they are invisible to every time filter")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(UnlocatedFindings), _root_.com.histmersion.api.checks.Lint.Rule.Description("findings with neither coordinates nor a region — invisible on the map")),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(LegacyFigureLinks),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("bodies whose figures still point at a `figures/…` path (usually the converter's own absolute path) instead of the canonical `assets/…` — they render as 0×0 images; re-import the note (`histmersion seed --force`) to rewrite them")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(DanglingArticleLinks),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("`[[wikilinks]]` in an ARTICLE that resolve to nothing — neither a compiled article slug nor a source citekey (nor a unique short form of one); they render as plain text, so the reader sees a name with no page behind it")
    ),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(NonCanonicalTag), _root_.com.histmersion.api.checks.Lint.Rule.Description("tags not in lowercase_snake_case (the canonical form)")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(MissingYear), _root_.com.histmersion.api.checks.Lint.Rule.Description("sources with no publication year — rendered as n.d.")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(YearCitekeyMismatch), _root_.com.histmersion.api.checks.Lint.Rule.Description("the four-digit year in the citekey does not match the stored publication year")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(AuthorPipe), _root_.com.histmersion.api.checks.Lint.Rule.Description("author names containing a pipe '|' — likely a parse artifact (\"Cline| Eric H.\")")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(ArticleFiller), _root_.com.histmersion.api.checks.Lint.Rule.Description("articles whose body contains banned filler phrases from the writing guide")),
    Rule(_root_.com.histmersion.api.checks.Lint.Rule.Name(ArticleUncitedParagraph), _root_.com.histmersion.api.checks.Lint.Rule.Description("article body paragraphs with no [[citekey]] citation")),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(ArticleThinSummary),
      _root_.com.histmersion.api.checks.Lint.Rule.Description(s"article summaries that are missing, a stand-in (\"placeholder\", TODO…) or shorter than $MinSummaryChars characters — the card and the search hit show nothing useful")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(ArticleCritiquePairing),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("article paragraphs that cite one side of a disagreement edge (challenges, refutes, updates, superseded_by) and name the other side's author without citing it — the critique is written but the pair stays open; `lint --rule article-critique-pairing --fix` appends the missing citekey where the name points at exactly one work")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(SupersededWithoutEdge),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("sources with status=superseded but no superseded_by edge — set the edge or change the status")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(ArticleFormat),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("article bodies and summaries whose Markdown will not display as written — tables shown as pipes, unpaired ** or `, links that are not links, malformed [[wikilinks]], pasted frontmatter, headings without a space, chatter and placeholders; one line per problem (`histmersion article lint <slug> --fix` repairs the safe ones)")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(NoteFormat),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("the same formatting check over each source's summary and notes (`histmersion lint --source <citekey> --fix`)")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(FindingGeocode),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("findings whose point the OLD geocoder chose for their place name (before #981 it let an alias, a placeholder point or the wrong dated snapshot win) and that the current ranking puts elsewhere; hand-placed points are never listed — `lint --rule finding-geocode --fix` moves them")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(FindingUnplaced),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("findings that name a place but have no point, where the gazetteer now knows that name (a re-seed added the English names: Dead Sea, Balkans, Kraków…) — `lint --rule finding-unplaced --fix` gives them the geocoder's point; a finding that has a point is never touched")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(PlaceKindRule),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("point-only seed regions that are something else, by their Pleiades types and linked Wikidata item: a people or civic tribe (→ people), a province or diocese (→ polity, with a period), a settlement (→ site), a place Pleiades withdrew as a duplicate (→ merged into its replacement); one line each: id, label, current → proposed kind, why, evidence — `lint --rule place-kind --fix` applies them and re-matches findings to places")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(PlaceGridPoint),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("Pleiades places whose point is only the centre of an atlas grid square, where a linked record (Wikidata's coordinate) gives a real point — `lint --rule place-grid-point --fix` moves them there and re-matches findings to places")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(PlaceImportedPointArea),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("GeoNames and Wikidata places of an area kind (region, polity, people: `places.sources.point-area-kinds`) stored with only a point — no shape, so no region; the importer no longer keeps them. Listed: the counts by source, kind and dating, every one that holds a finding (and how many of those findings it is the only place of), then a sample — `lint --rule place-imported-point-area --fix` removes them with their names, links and finding matches, in batches, and matches the affected sources' findings again")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(FindingPlaceholder),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("findings whose point is a placeholder (an atlas grid square's centre, or the point the geocoder gives a region) and whose place name the gazetteer cannot resolve, or that have none — no site takes them, and the areas that do are only those covering the point; give the finding a place name the gazetteer knows (or the place a name with `places name add`), or a real point")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(MediaLicenceRule),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("stored pictures with no licence, or a provider's picture with no credit line — they cannot be shown as their licence requires; a source's figure is listed as `<citekey>/<name>` (it takes its source's licence with `histmersion media fill-licences` once the source records one), a provider's as `media:<uid>` (re-import it with `histmersion media import wikidata --refresh`); a book cover (Calibre, Open Library) needs a credit line only — it is the publisher's picture of the user's own copy, shown locally and never exported")
    ),
    Rule(
      _root_.com.histmersion.api.checks.Lint.Rule.Name(EventCorrectionConflict),
      _root_.com.histmersion.api.checks.Lint.Rule.Description("catalogue events a person corrected by hand whose catalogue has since changed the same field to something else — the person's value is still applied; review each (`histmersion events corrections --status conflict`) and either keep it (patch it again) or take the catalogue's (patch the field to the catalogue's value, which retires the correction)")
    )
  )

  val MinSummaryChars = 80

  val names: List[com.histmersion.api.checks.Lint.Names] = all.map(_.name)

  def rule(name: com.histmersion.api.checks.Lint.RuleName): Option[Rule] = all.find(_.name == name.trim.toLowerCase)

  def select(requested: List[com.histmersion.api.checks.Lint.SelectRequested]): Either[com.histmersion.api.checks.Lint.SelectResultPart1, List[Rule]] =
    val wanted = requested.map(_.trim).filter(_.nonEmpty)
    if wanted.isEmpty then Right(all)
    else
      val unknown = wanted.filter(w => rule(w).isEmpty)
      if unknown.nonEmpty then
        Left(_root_.com.histmersion.api.checks.Lint.SelectResultPart1(s"unknown lint rule(s): ${unknown.mkString(", ")} — known rules: ${names.mkString(", ")}"))
      else Right(wanted.flatMap(rule).distinct)

  def blank(s: Option[com.histmersion.api.checks.Lint.BlankS]): com.histmersion.api.checks.Lint.BlankResult = s.forall(_.trim.isEmpty)

  def suggest(target: com.histmersion.api.checks.Lint.SuggestTarget, citekeys: Iterable[com.histmersion.api.checks.Lint.SuggestCitekeys], max: com.histmersion.api.checks.Lint.SuggestMax): List[com.histmersion.api.checks.Lint.SuggestResult] =
    val t = target.trim.toLowerCase
    if t.isEmpty || max <= 0 then Nil
    else
      def commonPrefix(a: com.histmersion.api.checks.Lint.SuggestCommonPrefixA, b: com.histmersion.api.checks.Lint.SuggestCommonPrefixB): com.histmersion.api.checks.Lint.SuggestCommonPrefixResult =
        val n = math.min(a.length, b.length)
        var i = 0
        while i < n && a.charAt(i) == b.charAt(i) do i += 1
        _root_.com.histmersion.api.checks.Lint.SuggestCommonPrefixResult(i)
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

  def looksLikeCitekey(target: com.histmersion.api.checks.Lint.LooksLikeCitekeyTarget): com.histmersion.api.checks.Lint.LooksLikeCitekeyResult = """\d{4}""".r.findFirstIn(target).isDefined

  def duplicates(rows: List[(com.histmersion.api.checks.Lint.DuplicatesRowsPart1, com.histmersion.api.checks.Lint.DuplicatesRowsPart2)], label: com.histmersion.api.checks.Lint.DuplicatesLabel): List[LintItem] =
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

  def isCanonicalTag(tag: com.histmersion.api.checks.Lint.IsCanonicalTagTag): com.histmersion.api.checks.Lint.IsCanonicalTagResult = CanonicalTag.matches(tag)

  def normaliseTag(tag: com.histmersion.api.checks.Lint.NormaliseTagTag): com.histmersion.api.checks.Lint.NormaliseTagResult =
    tag.trim.toLowerCase.replaceAll("""[\s-]+""", "_").replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")

  def nonCanonicalTags(rows: List[(com.histmersion.api.checks.Lint.NonCanonicalTagsRowsPart1, List[com.histmersion.api.checks.Lint.NonCanonicalTagsRowsPart2])]): List[LintItem] =
    rows
      .flatMap { (ck, tags) =>
        tags.filterNot(isCanonicalTag).map(t => LintItem(ck, s"tag '$t' → '${normaliseTag(t)}'"))
      }
      .sortBy(i => (i.citekey, i.detail))

  private val YearInCitekey = """(\d{4})""".r.unanchored

  def yearCitekeyMismatch(rows: List[(com.histmersion.api.checks.Lint.YearCitekeyMismatchRowsPart1, Option[com.histmersion.api.checks.Lint.YearCitekeyMismatchRowsPart2])]): List[LintItem] =
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

  def authorPipe(rows: List[(com.histmersion.api.checks.Lint.AuthorPipeRowsPart1, List[com.histmersion.api.checks.Lint.AuthorPipeRowsPart2])]): List[LintItem] =
    rows
      .flatMap { (ck, authors) =>
        authors.filter(_.contains("|")).map(a => LintItem(ck, s"author '$a'"))
      }
      .sortBy(i => (i.citekey, i.detail))

  val MaxListLeadIn = 300

  private val Abbreviations: Set[com.histmersion.api.checks.Lint.Abbreviations] =
    Set(
      _root_.com.histmersion.api.checks.Lint.Abbreviations("c"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("ca"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("cf"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("e.g"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("i.e"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("etc"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("vs"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("al"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("fl"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("st"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("mt"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("no"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("vol"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("pp"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("p"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("ed"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("eds"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("dr"),
      _root_.com.histmersion.api.checks.Lint.Abbreviations("fig")
    )

  private val SentenceEnd = "([^\\s]*)[.!?]+[\"”’)]*\\s+(?=(\\S))".r

  def sentenceCount(text: com.histmersion.api.checks.Lint.SentenceCountText): com.histmersion.api.checks.Lint.SentenceCountResult =
    val t = text.trim
    if t.isEmpty then _root_.com.histmersion.api.checks.Lint.SentenceCountResult(0)
    else
      // No `\p{Lu}` in the pattern: this code is also compiled to Scala.js, so the next char is tested instead.
      val ends = SentenceEnd.findAllMatchIn(t).count { m =>
        val next = m.group(2).head
        val word = m.group(1).replaceAll("^[(\"“]+", "").toLowerCase
        (next.isUpper || "[“\"(".contains(next)) && !(Abbreviations(word) || (word.length == 1 && word.head.isLetter))
      }
      _root_.com.histmersion.api.checks.Lint.SentenceCountResult(ends + 1)

  def isListLeadIn(text: com.histmersion.api.checks.Lint.IsListLeadInText): com.histmersion.api.checks.Lint.IsListLeadInResult =
    val t = text.trim
    t.endsWith(":") && t.length <= MaxListLeadIn && sentenceCount(t) == 1

  def extractParagraphs(body: com.histmersion.api.checks.Lint.ExtractParagraphsBody): List[com.histmersion.api.checks.Lint.ExtractParagraphsResult] =
    val lines = body.linesIterator.toList
    var inFence = false
    val paras = List.newBuilder[String]
    val current = new StringBuilder()
    var leadIn: Option[com.histmersion.api.checks.Lint.ExtractParagraphsLeadIn] = None
    def settle(nextIsList: com.histmersion.api.checks.Lint.ExtractParagraphsSettleNextIsList): Unit =
      leadIn.foreach(t => if !nextIsList then paras += t)
      leadIn = None
    def flush(): Unit =
      val text = current.toString.trim
      if text.nonEmpty then if isListLeadIn(_root_.com.histmersion.api.checks.Lint.IsListLeadInText(text)) then leadIn = Some(_root_.com.histmersion.api.checks.Lint.ExtractParagraphsLeadIn(text)) else paras += text
      current.clear()
    def isList(t: com.histmersion.api.checks.Lint.ExtractParagraphsIsListT) = t.startsWith("- ") || t.startsWith("* ") || "^\\d+[.)] ".r.findPrefixOf(t).isDefined
    def block(nextIsList: com.histmersion.api.checks.Lint.ExtractParagraphsBlockNextIsList): Unit = { flush(); settle(nextIsList) }
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
    block(nextIsList = _root_.com.histmersion.api.checks.Lint.ExtractParagraphsBlockNextIsList(false))
    (paras.result()).map(refactorValue2 => _root_.com.histmersion.api.checks.Lint.ExtractParagraphsResult(refactorValue2))

  type FormatChecker = (String, FormatField, Boolean) => List[FormatIssue]

  def formatItems(rows: List[(com.histmersion.api.checks.Lint.FormatItemsRowsPart1, FormatField, com.histmersion.api.checks.Lint.FormatItemsRowsPart3)], check: FormatChecker): List[LintItem] =
    rows.filter(_._3.trim.nonEmpty).sortBy((id, f, _) => (id, f.ordinal)).flatMap { (id, field, text) =>
      check(text, field, field == FormatField.Body).map(i => LintItem(id, FormatMarkdown.line(i)))
    }

  def report(r: Rule, items: List[LintItem], maxItems: com.histmersion.api.checks.Lint.ReportMaxItems): LintRuleReport =
    LintRuleReport(r.name, r.description, _root_.com.histmersion.api.checks.Lint.LintRuleReport.Count(items.size), items.take(math.max(0, maxItems)))

  def markdown(rep: LintReport): com.histmersion.api.checks.Lint.MarkdownResult =
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
    _root_.com.histmersion.api.checks.Lint.MarkdownResult(s"$fixed$head${blocks.mkString}$cleanLine")
