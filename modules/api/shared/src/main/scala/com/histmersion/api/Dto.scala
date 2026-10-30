package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.{Confidence, SourceStatus}

/** Wire DTOs — the contract between server, CLI and MCP. Circe codec AND tapir Schema are both derived by Kindlings
  * from the same case class, so JSON and OpenAPI/MCP schemas match by construction. The `db` module adds Magnum
  * `DbCodec`s for the same types.
  */

/** A row of the `search` result set: period split to bounds, arrays as proper lists. */
final case class SearchHit(
    citekey: String,
    title: String,
    year: Option[Int],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    // FTS extras (plan §1.4). Optional so callers that don't run a text query keep compiling.
    snippet: Option[String] = None, // ts_headline over the best-matching body chunk (**term**)
    matchedHeading: Option[String] = None, // that chunk's heading breadcrumb
    doi: Option[String] = None, // the link label when present (else the citekey)
    authors: List[String] = Nil // as stored: Library's table shows them under the title
) derives KindlingsCodecAsObject
object SearchHit:
  given Schema[SearchHit] = KindlingsSchema.derived[SearchHit].schema

/** Full source record for `get_source`. */
final case class SourceDetail(
    citekey: String,
    title: String,
    authors: List[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    doi: Option[String],
    ocrMarkdown: Option[String],
    /** Agent-written article about the source (the note's Markdown body). */
    notes: Option[String] = None,
    /** Opaque last-write token; echo it back as `patch_source`'s `ifUpdatedAt` precondition. */
    updatedAt: Option[String] = None,
    /** How the source was acquired: manual|ill|author|scan|external-file|calibre|archive|oa. */
    provenance: Option[String] = None,
    /** The permanent identity (V030): unlike the citekey it survives a rename, and a bundle carries it to another
      * instance.
      */
    uid: Option[java.util.UUID] = None,
    /** The instance that created the source (this one, or the one a bundle import brought it from). */
    origin: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject
object SourceDetail:
  given Schema[SourceDetail] = KindlingsSchema.derived[SourceDetail].schema

/** One geo-located, time-scoped finding row. */
final case class FindingRow(
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String] = None,
    /** The finding's permanent identity (V030); its `<citekey>#<n>` id is a label that a rename moves. */
    uid: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject
object FindingRow:
  given Schema[FindingRow] = KindlingsSchema.derived[FindingRow].schema

/** A source with its findings (the `get_source` response). */
final case class SourceWithFindings(
    source: SourceDetail,
    findings: List[FindingRow],
    /** Formatting problems of the summary and notes — filled on a write (put/patch), so the agent that wrote them is
      * told at once; empty on a plain read.
      */
    formatting: List[FormatIssue] = Nil,
    /** The picture a hover card shows for the source (#1446): a book's cover, else one of its own figures chosen to
      * illustrate it — the local thumbnail, with its credit.
      */
    illustration: Option[MediaThumb] = None
) derives KindlingsCodecAsObject
object SourceWithFindings:
  given Schema[SourceWithFindings] = KindlingsSchema.derived[SourceWithFindings].schema

/** A finding plus its source, for `geo_when` and the map. */
final case class GeoHit(
    citekey: String,
    title: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    confidence: Option[Confidence],
    significance: Option[String] = None,
    /** The source's DOI — what a link to it is labelled with (the owner's rule: DOI when present). */
    doi: Option[String] = None,
    /** What the source is ([[SourceKinds]]): the map draws the finding with that kind's icon. */
    sourceKind: Option[String] = None
) derives KindlingsCodecAsObject
object GeoHit:
  given Schema[GeoHit] = KindlingsSchema.derived[GeoHit].schema

/** One reference edge touching a source, oriented relative to it (out = this cites/relates dst). `citekey` is the OTHER
  * end's id — a citekey for a [[NeighborTarget.Source]], a slug for an [[NeighborTarget.Article]], the raw stored
  * target for an [[NeighborTarget.Unresolved]] one. `target` is decided once, by the query in `SourceRepo.neighbors`.
  */
final case class NeighborRow(
    direction: String,
    kind: String,
    citekey: String,
    title: Option[String],
    status: Option[SourceStatus] = None,
    target: NeighborTarget = NeighborTarget.Source
) derives KindlingsCodecAsObject
object NeighborRow:
  given Schema[NeighborRow] = KindlingsSchema.derived[NeighborRow].schema

/** One row of `/api/contradictions`: an edge whose kind is challenges/refutes/updates/superseded_by, OR a source with a
  * contested/superseded/debunked/retracted status.
  */
final case class ContradictionRow(
    citekey: String,
    title: String,
    year: Option[Int],
    status: SourceStatus,
    direction: Option[String],
    kind: Option[String],
    otherCitekey: Option[String],
    otherTitle: Option[String],
    otherYear: Option[Int] = None,
    otherStatus: Option[SourceStatus] = None
) derives KindlingsCodecAsObject
object ContradictionRow:
  given Schema[ContradictionRow] = KindlingsSchema.derived[ContradictionRow].schema

/** The result of `/api/contradictions`. */
final case class ContradictionsResult(rows: List[ContradictionRow], total: Int) derives KindlingsCodecAsObject
object ContradictionsResult:
  given Schema[ContradictionsResult] = KindlingsSchema.derived[ContradictionsResult].schema

final case class VersionInfo(name: String, version: String) derives KindlingsCodecAsObject
object VersionInfo:
  given Schema[VersionInfo] = KindlingsSchema.derived[VersionInfo].schema

/** `/api/health`: each dependency reports ok/error text (`converter` = the OCR sidecar, plan §1.8). `warnings`: what
  * the server runs without but someone should know — a style file refused, say — never a reason for `status` to change.
  */
final case class Health(
    status: String,
    db: String,
    schemaVersion: Option[String],
    converter: Option[String] = None,
    warnings: List[String] = Nil
) derives KindlingsCodecAsObject
object Health:
  given Schema[Health] = KindlingsSchema.derived[Health].schema

final case class SeedRequest(dir: String, force: Boolean = false) derives KindlingsCodecAsObject
object SeedRequest:
  given Schema[SeedRequest] = KindlingsSchema.derived[SeedRequest].schema

/** `skipped` = citekeys whose stored row is NEWER than the file on disk (agent enrichment that a reseed would have
  * wiped); re-run with `force` to overwrite them anyway.
  */
final case class SeedResult(
    imported: Int,
    failed: List[String],
    edgesResolved: Int,
    skipped: List[String] = Nil
) derives KindlingsCodecAsObject
object SeedResult:
  given Schema[SeedResult] = KindlingsSchema.derived[SeedResult].schema

/** `POST /api/reindex`: one resumable slice of the FTS chunk rebuild. Re-indexing the whole corpus costs minutes
  * (Postgres spends ~2 ms per chunk building the tsvector), far past any HTTP timeout, so the endpoint walks `sources`
  * in citekey order and does a bounded amount of work per call: `lastCitekey` is the cursor to pass back as `after`,
  * `remaining` is how many sources are still untouched. The CLI loops until `remaining` is 0.
  */
final case class ReindexResult(sources: Int, chunks: Int, lastCitekey: Option[String], remaining: Int)
    derives KindlingsCodecAsObject
object ReindexResult:
  given Schema[ReindexResult] = KindlingsSchema.derived[ReindexResult].schema

/** `GET /api/resolve/{id}` — what a bare identifier met in free text names: a stored source, an article, or nothing
  * (both empty). Always a 200: "names nothing" is an answer, not an error.
  */
final case class ResolvedId(
    id: String,
    source: Option[ResolvedSource] = None,
    article: Option[ResolvedArticle] = None
) derives KindlingsCodecAsObject
object ResolvedId:
  given Schema[ResolvedId] = KindlingsSchema.derived[ResolvedId].schema

final case class ResolvedSource(
    citekey: String,
    doi: Option[String],
    authors: List[String],
    year: Option[Int],
    title: String,
    status: SourceStatus
) derives KindlingsCodecAsObject:
  def ref: SourceRef = SourceRef(citekey, doi, authors, year, title, status)
object ResolvedSource:
  given Schema[ResolvedSource] = KindlingsSchema.derived[ResolvedSource].schema

final case class ResolvedArticle(slug: String, title: String) derives KindlingsCodecAsObject
object ResolvedArticle:
  given Schema[ResolvedArticle] = KindlingsSchema.derived[ResolvedArticle].schema

/** `code` lets one error output carry several HTTP statuses (see `Write.Code*`); absent = 400. */
final case class ApiError(message: String, code: Option[String] = None) derives KindlingsCodecAsObject
object ApiError:
  given Schema[ApiError] = KindlingsSchema.derived[ApiError].schema

  /** The `oneOfVariantValueMatcher` test for one [[ApiError.code]]. Tapir hands the matcher an `Any`, and under
    * `-source:future` a constructor pattern on `Any` is a warning (it is not `Matchable`) — so the value is checked
    * with a type test instead of being destructured.
    */
  def hasCode(code: String): PartialFunction[Any, Boolean] =
    case e if e.isInstanceOf[ApiError] && e.asInstanceOf[ApiError].code.contains(code) => true

// ── search results: the SOURCE is the unit of every result (plan §1.0) ─────────────────────────

/** One finding of a hit source that satisfied the filter, with the criteria it satisfied. `why` entries are e.g.
  * `period`, `bbox`, `place: Anatolia`, `tag: sea-peoples`.
  */
final case class FindingMatch(
    id: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: List[String],
    why: List[String],
    /** The gazetteer places this finding was materialised into (plan §1.6a); empty until `histmersion places rebuild`
      * has run.
      */
    places: List[String] = Nil
) derives KindlingsCodecAsObject
object FindingMatch:
  given Schema[FindingMatch] = KindlingsSchema.derived[FindingMatch].schema

/** A source that matched, plus the evidence: `matched` = its findings that satisfied the period/bbox/place/tag
  * criteria, `why` = source-level reasons ("title matches 'collapse'").
  */
final case class Hit(source: SearchHit, matched: List[FindingMatch], why: List[String]) derives KindlingsCodecAsObject
object Hit:
  given Schema[Hit] = KindlingsSchema.derived[Hit].schema

/** `total` = count(DISTINCT source) for the filter, ignoring limit/offset. */
final case class SearchResult(
    total: Int,
    hits: List[Hit],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, hits.size)
object SearchResult:
  given Schema[SearchResult] = KindlingsSchema.derived[SearchResult].schema
  def of(total: Int, hits: List[Hit], offset: Int): SearchResult =
    SearchResult(total, hits, offset, OffsetPage(total, offset, hits.size).next)

/** `geo_when` / `geo-when` (`GET /api/findings/page`): one page of the map feed's findings with the [[OffsetPage]]
  * fields — the map itself reads the plain `GET /api/findings` array.
  */
final case class GeoHitPage(
    total: Int,
    hits: List[GeoHit],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, hits.size)
object GeoHitPage:
  given Schema[GeoHitPage] = KindlingsSchema.derived[GeoHitPage].schema
  def of(total: Int, hits: List[GeoHit], offset: Int): GeoHitPage =
    GeoHitPage(total, hits, offset, OffsetPage(total, offset, hits.size).next)

/** One facet bucket; `count` is always count(DISTINCT source) within the current filter. `id` is the gazetteer place
  * behind a `places` value when there is one (plan §1.6a) — pass THAT as `place=` and the filter matches through
  * `finding_places` and the hierarchy instead of by name.
  */
final case class Facet(
    value: String,
    count: Int,
    id: Option[String] = None,
    /** The gazetteer place behind `id` has a disputed shape (plan §1.6a) — rendered "(contested)". */
    contested: Boolean = false
) derives KindlingsCodecAsObject
object Facet:
  given Schema[Facet] = KindlingsSchema.derived[Facet].schema

/** One bar of the timeline strip: an inclusive `[start, end]` year range and how many units (sources by default) have a
  * period overlapping it. See [[Aggregate]].
  */
final case class TimeBucket(start: Long, end: Long, count: Int) derives KindlingsCodecAsObject
object TimeBucket:
  given Schema[TimeBucket] = KindlingsSchema.derived[TimeBucket].schema

/** The whole `/api/aggregate` answer: the window actually aggregated, the adaptive bucket size chosen for it, and —
  * depending on `by` — the flat strip and/or the nested tree.
  *
  *   - `by=time` (plan §1.7, the default) fills `buckets`: one bar per time bucket, gaps included, which is what the
  *     timeline strip draws.
  *   - `by=region|time,region|region,time` (plan §1.6) fills `tree`: the nested [[Bucket]] forest, empty buckets
  *     pruned, `depth` levels deep, the gazetteer read as of `asOf` (for `by=region`; `time,region` reads it at each
  *     time bucket's own midpoint and leaves `asOf` empty).
  *
  * Every count in both is `count(DISTINCT unit)` and a unit is counted in EVERY bucket it overlaps, so nothing here
  * sums to `total` — see [[Bucket.overlap]].
  */
final case class TimeHistogram(
    from: Long,
    to: Long,
    bucket: Long,
    buckets: List[TimeBucket],
    // ── the §1.6 tree. Defaults keep the wave-1 `by=time` JSON byte-identical for old clients. ──
    by: String = "time",
    unit: String = "sources",
    total: Int = 0,
    depth: Int = 0,
    asOf: Option[Long] = None,
    tree: List[Bucket] = Nil
) derives KindlingsCodecAsObject
object TimeHistogram:
  given Schema[TimeHistogram] = KindlingsSchema.derived[TimeHistogram].schema

// ── get_source sectioning (plan §6 A1/A7/A18) ─────────────────────────────────────────────────

/** One entry in the table of contents: a heading from `source_chunks`, its first chunk `ord` and the total characters
  * under it.
  */
final case class TocEntry(heading: String, ord: Int, chars: Int) derives KindlingsCodecAsObject
object TocEntry:
  given Schema[TocEntry] = KindlingsSchema.derived[TocEntry].schema

/** The valid values of `section` on `get_source`. */
object SourceSection:
  val Summary = "summary"
  val Findings = "findings"
  val Notes = "notes"
  val Toc = "toc"
  val Body = "body"
  val All = "all"
  val all: List[String] = List(Summary, Findings, Notes, Toc, Body, All)

  def parse(s: String): Option[String] =
    val lower = s.trim.toLowerCase
    all.find(_ == lower)
