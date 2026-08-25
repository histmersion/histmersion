package histdb.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract, as Tapir endpoint DEFINITIONS (no logic). The server interprets them
  * (netty-sync) and the CLI derives an sttp client from the very same values, so both sides agree by
  * construction. MCP tools wrap the same server handlers. */
object Api:

  /** Query for `findings` / `geo_when`: optional region substring, optional bbox, optional period. */
  final case class FindingsQuery(
      region: Option[String],
      minLon: Option[Double],
      minLat: Option[Double],
      maxLon: Option[Double],
      maxLat: Option[Double],
      from: Option[Long],
      to: Option[Long],
      limit: Option[Int]
  ):
    def bbox: Option[(Double, Double, Double, Double)] = (minLon, minLat, maxLon, maxLat) match
      case (Some(a), Some(b), Some(c), Some(d)) => Some((a, b, c, d))
      case _                                    => None

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

  val findings: PublicEndpoint[FindingsQuery, ApiError, List[GeoHit], Any] =
    base.get.in("findings")
      .in(query[Option[String]]("region"))
      .in(query[Option[Double]]("minLon")).in(query[Option[Double]]("minLat"))
      .in(query[Option[Double]]("maxLon")).in(query[Option[Double]]("maxLat"))
      .in(query[Option[Long]]("from")).in(query[Option[Long]]("to"))
      .in(query[Option[Int]]("limit"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[GeoHit]])

  val neighbors: PublicEndpoint[String, ApiError, List[NeighborRow], Any] =
    base.get.in("neighbors" / path[String]("citekey")).out(jsonBody[List[NeighborRow]])

  /** Seed from a server-side directory (bind-mounted corpus; `dir` empty → config `corpus.dir`). */
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

  val all = List(version, health, config, search, sources, facets, getSource, findings, neighbors, seed)
