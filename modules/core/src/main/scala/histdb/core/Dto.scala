package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Wire DTOs — the contract between server, CLI and MCP. Circe codec AND tapir Schema are both
  * derived by Kindlings from the same case class, so JSON and OpenAPI/MCP schemas match by
  * construction. The `db` module adds Magnum `DbCodec`s for the same types. */

/** A row of the `search` result set (arrays flattened to comma-joined strings; period split to bounds). */
final case class SearchHit(
    citekey: String,
    title: String,
    year: Option[Int],
    status: String,
    topics: String,
    regions: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    // FTS extras (plan §1.4). Optional so callers that don't run a text query keep compiling.
    snippet: Option[String] = None,        // ts_headline over the best-matching body chunk (**term**)
    matchedHeading: Option[String] = None  // that chunk's heading breadcrumb
) derives KindlingsCodecAsObject
object SearchHit:
  given Schema[SearchHit] = KindlingsSchema.derived[SearchHit].schema

/** Full source record for `get_source`. */
final case class SourceDetail(
    citekey: String,
    title: String,
    authors: String,
    year: Option[Int],
    venue: Option[String],
    status: String,
    topics: String,
    regions: String,
    tags: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    doi: Option[String],
    ocrMarkdown: Option[String],
    /** Agent-written article about the source (the note's Markdown body). */
    notes: Option[String] = None,
    /** Opaque last-write token; echo it back as `patch_source`'s `ifUpdatedAt` precondition. */
    updatedAt: Option[String] = None
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
    tags: String,
    confidence: Option[String]
) derives KindlingsCodecAsObject
object FindingRow:
  given Schema[FindingRow] = KindlingsSchema.derived[FindingRow].schema

/** A source with its findings (the `get_source` response). */
final case class SourceWithFindings(source: SourceDetail, findings: List[FindingRow])
    derives KindlingsCodecAsObject
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
    confidence: Option[String]
) derives KindlingsCodecAsObject
object GeoHit:
  given Schema[GeoHit] = KindlingsSchema.derived[GeoHit].schema

/** One reference edge touching a source, oriented relative to it (out = this cites/relates dst). */
final case class NeighborRow(direction: String, kind: String, citekey: String, title: Option[String])
    derives KindlingsCodecAsObject
object NeighborRow:
  given Schema[NeighborRow] = KindlingsSchema.derived[NeighborRow].schema

final case class VersionInfo(name: String, version: String) derives KindlingsCodecAsObject
object VersionInfo:
  given Schema[VersionInfo] = KindlingsSchema.derived[VersionInfo].schema

/** `/api/health`: each dependency reports ok/error text (`converter` = the OCR sidecar, plan §1.8). */
final case class Health(
    status: String,
    db: String,
    schemaVersion: Option[String],
    converter: Option[String] = None
) derives KindlingsCodecAsObject
object Health:
  given Schema[Health] = KindlingsSchema.derived[Health].schema

final case class SeedRequest(dir: String, force: Boolean = false) derives KindlingsCodecAsObject
object SeedRequest:
  given Schema[SeedRequest] = KindlingsSchema.derived[SeedRequest].schema

/** `skipped` = citekeys whose stored row is NEWER than the file on disk (agent enrichment that a
  * reseed would have wiped); re-run with `force` to overwrite them anyway. */
final case class SeedResult(
    imported: Int,
    failed: List[String],
    edgesResolved: Int,
    skipped: List[String] = Nil
) derives KindlingsCodecAsObject
object SeedResult:
  given Schema[SeedResult] = KindlingsSchema.derived[SeedResult].schema

/** `POST /api/reindex`: one resumable slice of the FTS chunk rebuild.
  * Re-indexing the whole corpus costs minutes (Postgres spends ~2 ms per chunk building the
  * tsvector), far past any HTTP timeout, so the endpoint walks `sources` in citekey order and does a
  * bounded amount of work per call: `lastCitekey` is the cursor to pass back as `after`, `remaining`
  * is how many sources are still untouched. The CLI loops until `remaining` is 0. */
final case class ReindexResult(sources: Int, chunks: Int, lastCitekey: Option[String], remaining: Int)
    derives KindlingsCodecAsObject
object ReindexResult:
  given Schema[ReindexResult] = KindlingsSchema.derived[ReindexResult].schema

/** `code` lets one error output carry several HTTP statuses (see `Write.Code*`); absent = 400. */
final case class ApiError(message: String, code: Option[String] = None) derives KindlingsCodecAsObject
object ApiError:
  given Schema[ApiError] = KindlingsSchema.derived[ApiError].schema

// ── search results: the SOURCE is the unit of every result (plan §1.0) ─────────────────────────

/** One finding of a hit source that satisfied the filter, with the criteria it satisfied.
  * `why` entries are e.g. `period`, `bbox`, `place: Anatolia`, `tag: sea-peoples`. */
final case class FindingMatch(
    id: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: String,
    why: List[String],
    /** The gazetteer places this finding was materialised into (plan §1.6a), comma-joined labels;
      * empty until `histdb places rebuild` has run. */
    places: String = ""
) derives KindlingsCodecAsObject
object FindingMatch:
  given Schema[FindingMatch] = KindlingsSchema.derived[FindingMatch].schema

/** A source that matched, plus the evidence: `matched` = its findings that satisfied the
  * period/bbox/place/tag criteria, `why` = source-level reasons ("title matches 'collapse'"). */
final case class Hit(source: SearchHit, matched: List[FindingMatch], why: List[String])
    derives KindlingsCodecAsObject
object Hit:
  given Schema[Hit] = KindlingsSchema.derived[Hit].schema

/** `total` = count(DISTINCT source) for the filter, ignoring limit/offset. */
final case class SearchResult(total: Int, hits: List[Hit]) derives KindlingsCodecAsObject
object SearchResult:
  given Schema[SearchResult] = KindlingsSchema.derived[SearchResult].schema

/** One facet bucket; `count` is always count(DISTINCT source) within the current filter. `id` is the
  * gazetteer place behind a `places` value when there is one (plan §1.6a) — pass THAT as `place=` and
  * the filter matches through `finding_places` and the hierarchy instead of by name. */
final case class Facet(value: String, count: Int, id: Option[String] = None,
    /** The gazetteer place behind `id` has a disputed shape (plan §1.6a) — rendered "(contested)". */
    contested: Boolean = false) derives KindlingsCodecAsObject
object Facet:
  given Schema[Facet] = KindlingsSchema.derived[Facet].schema
/** One bar of the timeline strip: an inclusive `[start, end]` year range and how many units
  * (sources by default) have a period overlapping it. See [[Aggregate]]. */
final case class TimeBucket(start: Long, end: Long, count: Int) derives KindlingsCodecAsObject
object TimeBucket:
  given Schema[TimeBucket] = KindlingsSchema.derived[TimeBucket].schema

/** `/api/aggregate?by=time`: the whole strip — the window actually aggregated, the adaptive bucket
  * size chosen for it, and the bars. Counts overlap, so they do NOT sum to a total. */
final case class TimeHistogram(from: Long, to: Long, bucket: Long, buckets: List[TimeBucket])
    derives KindlingsCodecAsObject
object TimeHistogram:
  given Schema[TimeHistogram] = KindlingsSchema.derived[TimeHistogram].schema
