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
    ocrMarkdown: Option[String]
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

/** `/api/health`: each dependency reports ok/error text. */
final case class Health(status: String, db: String, schemaVersion: Option[String])
    derives KindlingsCodecAsObject
object Health:
  given Schema[Health] = KindlingsSchema.derived[Health].schema

final case class SeedRequest(dir: String, force: Boolean = false) derives KindlingsCodecAsObject
object SeedRequest:
  given Schema[SeedRequest] = KindlingsSchema.derived[SeedRequest].schema

final case class SeedResult(imported: Int, failed: List[String], edgesResolved: Int)
    derives KindlingsCodecAsObject
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

final case class ApiError(message: String) derives KindlingsCodecAsObject
object ApiError:
  given Schema[ApiError] = KindlingsSchema.derived[ApiError].schema
