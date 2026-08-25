package histdb.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract, as Tapir endpoint DEFINITIONS (no logic). The server interprets them
  * (netty-sync) and the CLI derives an sttp client from the very same values, so both sides agree by
  * construction. MCP tools wrap the same server handlers. */
object Api:

  /** Input of `GET /api/findings` (the map feed) and MCP `geo_when`: the SHARED [[Filter]] plus the
    * backwards-compatible `region` parameter older links/clients still send. `region` is simply one
    * more place name (places are any-of), so `?region=Anatolia` keeps meaning what it always did:
    * findings whose `region_name` contains "Anatolia". */
  final case class FindingsQuery(filter: Filter, region: Option[String]):
    def toFilter: Filter =
      region.map(_.trim).filter(_.nonEmpty).fold(filter)(r => filter.copy(places = filter.places :+ r))

  object FindingsQuery:
    def of(filter: Filter): FindingsQuery = FindingsQuery(filter, None)

  private val base = endpoint.in("api").errorOut(jsonBody[ApiError])

  /** The shared [[Filter]] query parameters (plan §1.0). The names are FIXED — the web UI, the CLI
    * and the other streams rely on them: `q`, `tag`*, `author`*, `place`*, `topic`*, `status`*,
    * `yearFrom`, `yearTo`, `from`, `to`, `minLon`, `minLat`, `maxLon`, `maxLat`, `sort`, `limit`,
    * `offset` (`*` = repeatable). Mapped BOTH ways, so the sttp client can encode a `Filter` back
    * into a request. */
  val filterQuery: EndpointInput[Filter] =
    query[Option[String]]("q")
      .and(query[List[String]]("tag"))
      .and(query[List[String]]("author"))
      .and(query[List[String]]("place"))
      .and(query[List[String]]("topic"))
      .and(query[List[String]]("status"))
      .and(query[Option[Long]]("yearFrom"))
      .and(query[Option[Long]]("yearTo"))
      .and(query[Option[Long]]("from"))
      .and(query[Option[Long]]("to"))
      .and(query[Option[Double]]("minLon"))
      .and(query[Option[Double]]("minLat"))
      .and(query[Option[Double]]("maxLon"))
      .and(query[Option[Double]]("maxLat"))
      .and(query[Option[String]]("sort"))
      .and(query[Option[Int]]("limit"))
      .and(query[Option[Int]]("offset"))
      .mapTo[FilterQuery]
      .map(_.toFilter)(FilterQuery.from)

  val version: PublicEndpoint[Unit, ApiError, VersionInfo, Any] =
    base.get.in("version").out(jsonBody[VersionInfo])

  val health: PublicEndpoint[Unit, ApiError, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  val config: PublicEndpoint[Unit, ApiError, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  val search: PublicEndpoint[(Option[String], Option[Int]), ApiError, List[SearchHit], Any] =
    base.get.in("search").in(query[Option[String]]("q")).in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]])

  val getSource: PublicEndpoint[String, ApiError, SourceWithFindings, Any] =
    base.get.in("sources" / path[String]("citekey")).out(jsonBody[SourceWithFindings])
      .errorOut(statusCode(StatusCode.NotFound))

  /** `GET /api/findings?<filter>[&region=…]` — the MAP FEED (plan §1.0: the map draws findings, the
    * sidebar lists sources). Takes the same query parameters as `/api/sources`; a finding is returned
    * when IT satisfies the finding-level criteria (deep-time overlap, viewport, place, tags) and its
    * source satisfies the source-level ones (`q`, authors, status, publication years, topics).
    * `limit` (from the shared filter) defaults to the configured `limits.findings`. */
  val findings: PublicEndpoint[FindingsQuery, ApiError, List[GeoHit], Any] =
    base.get.in("findings")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[GeoHit]])

  /** Input of `GET /api/aggregate`: the aggregation axes plus the SHARED [[Filter]]. `by` = `time`
    * (wave 1; `region` / `time,region` arrive with the §1.6 tree). `unit` = `sources` (default, the
    * unit of every result) | `findings`. The window to bucket is the filter's `from`/`to`; absent, the
    * configured default span is used. */
  final case class AggregateQuery(by: Option[String], unit: Option[String], filter: Filter)

  object AggregateQuery:
    def time(filter: Filter, unit: Option[String] = None): AggregateQuery =
      AggregateQuery(Some("time"), unit, filter)

  /** The timeline strip's data source (plan §1.7): how many sources fall in each time bucket of the
    * currently visible span. Same aggregation as the §1.6 "by time" tree, so the two always agree.
    * Filtered by exactly the same parameters as `/api/sources` and `/api/findings`. */
  val aggregate: PublicEndpoint[AggregateQuery, ApiError, TimeHistogram, Any] =
    base.get.in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .mapInTo[AggregateQuery]
      .out(jsonBody[TimeHistogram])

  val neighbors: PublicEndpoint[String, ApiError, List[NeighborRow], Any] =
    base.get.in("neighbors" / path[String]("citekey")).out(jsonBody[List[NeighborRow]])

  /** Seed from a server-side directory (bind-mounted corpus; `dir` empty → config `corpus.dir`).
    * Skips any file older than its stored row unless `force` — see [[SeedResult.skipped]]. */
  val seed: PublicEndpoint[SeedRequest, ApiError, SeedResult, Any] =
    base.post.in("seed").in(jsonBody[SeedRequest]).out(jsonBody[SeedResult])

  /** `GET /api/sources?<filter>` — the primary discovery endpoint (plan §1.0/§1.3). The SOURCE is the
    * unit of the result; every hit carries the findings that made it match and why. */
  val sources: PublicEndpoint[Filter, ApiError, SearchResult, Any] =
    base.get.in("sources").in(filterQuery).out(jsonBody[SearchResult])

  /** `GET /api/facets?field=…&prefix=…&<filter>` — the vocabulary of a dimension inside the current
    * filter, counted as DISTINCT sources (so a chip shows how many sources it would leave). */
  val facets: PublicEndpoint[(String, Option[String], Filter), ApiError, List[Facet], Any] =
    base.get.in("facets")
      .in(query[String]("field"))
      .in(query[Option[String]]("prefix"))
      .in(filterQuery)
      .out(jsonBody[List[Facet]])

  // ── writes (plan §1.9a) ──────────────────────────────────────────────────────────────────────

  /** One error output carrying three statuses, selected by [[ApiError.code]]: a missing citekey is
    * 404, a failed `ifUpdatedAt` precondition is 412, anything else is a 400 with a readable message. */
  private val writeErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError]) {
        case ApiError(_, Some(Write.CodeNotFound)) => true
      },
      oneOfVariantValueMatcher(StatusCode.PreconditionFailed, jsonBody[ApiError]) {
        case ApiError(_, Some(Write.CodePrecondition)) => true
      },
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** Idempotent create/replace of EVERYTHING under one citekey. */
  val putSource: PublicEndpoint[(String, SourcePut), ApiError, SourceWithFindings, Any] =
    endpoint.put.in("api" / "sources" / path[String]("citekey")).in(jsonBody[SourcePut])
      .errorOut(writeErrors).out(jsonBody[SourceWithFindings])

  /** Selective enrichment: only the parts present in the body are replaced. */
  val patchSource: PublicEndpoint[(String, SourcePatch), ApiError, SourceWithFindings, Any] =
    endpoint.patch.in("api" / "sources" / path[String]("citekey")).in(jsonBody[SourcePatch])
      .errorOut(writeErrors).out(jsonBody[SourceWithFindings])

  /** Seed ONE note from raw text — the host-side path of `histdb seed`, since the CLI has no DB and
    * the server has no host filesystem. Same guard as [[seed]]. */
  val importNote: PublicEndpoint[NoteImport, ApiError, SeedResult, Any] =
    base.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  /** The note as it would live on disk: frontmatter YAML + the `notes` body. */
  val exportSource: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(writeErrors).out(stringBody)

  /** Every citekey, sorted — so `histdb export <dir>` can walk the whole corpus. */
  val citekeys: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("citekeys").out(jsonBody[List[String]])

  /** Unify short edge targets to full citekeys; run once after a client-driven seed. */
  val resolveEdges: PublicEndpoint[Unit, ApiError, EdgesResolved, Any] =
    base.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  val all = List(version, health, config, search, sources, facets, getSource, findings, aggregate, neighbors, seed,
    putSource, patchSource, importNote, exportSource, citekeys, resolveEdges)
