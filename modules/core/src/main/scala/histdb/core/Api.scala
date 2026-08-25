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

  /** Rebuild the FTS chunk index (plan §1.4) — needed once after V002, and after any bulk change to
    * `ocr_markdown` that bypassed `upsert`. Resumable: one call does a bounded amount of work from the
    * `after` cursor (see [[ReindexResult]]); `limit` caps the sources per call. */
  val reindex: PublicEndpoint[(Option[String], Option[Int]), ApiError, ReindexResult, Any] =
    base.post.in("reindex").in(query[Option[String]]("after")).in(query[Option[Int]]("limit"))
      .out(jsonBody[ReindexResult])

  val all = List(version, health, config, search, getSource, findings, neighbors, seed, reindex)
