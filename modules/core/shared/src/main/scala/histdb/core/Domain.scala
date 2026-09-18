package histdb.core

/** Deep time as signed integer years (BCE negative). A point in time is `start == end`;
  * a range is `start < end`. Stored in Postgres as `int8range`. */
final case class Period(start: Long, end: Long):
  def isPoint: Boolean = start == end

/** WGS84 lon/lat, matching PostGIS `geometry(Point, 4326)`. */
final case class GeoPoint(lon: Double, lat: Double)

/** A geo-located, time-scoped claim mined from a source. */
final case class Finding(
    id: String,
    citekey: String,
    claim: String,
    region: Option[String],
    geo: Option[GeoPoint],
    period: Option[Period],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[String],
    significance: Option[String] = None
)

/** One paper/book. `ocrMarkdown` (the full converted body) is the store-of-record content that the
  * server serialises back to the LLM as Markdown; the original binary stays in Calibre. */
final case class Source(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    period: Option[Period],
    summary: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    provenance: Option[String],
    calibreId: Option[String],
    ocrMarkdown: Option[String] = None,
    findings: List[Finding] = Nil,
    edges: List[Edge] = Nil,
    /** Free Markdown the agent compiles ABOUT this source (the body of `wiki/sources/<ck>.md`). */
    notes: Option[String] = None,
    /** Opaque last-write token (`updated_at`), set by the store. `patch_source`'s `ifUpdatedAt`
      * precondition compares against it; `None` when the value did not come from the database. */
    updatedAt: Option[String] = None
)

/** A directed, typed reference between sources: `src` --kind--> `dst`.
  * kind ∈ {references, superseded_by, refutes, challenges, updates, supersedes}. */
final case class Edge(src: String, dst: String, kind: String)

enum SourceStatus:
  case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

object SourceStatus:
  def fromString(s: String): SourceStatus =
    values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)
