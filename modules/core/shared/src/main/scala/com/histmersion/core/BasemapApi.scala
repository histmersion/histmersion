package com.histmersion.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{Header, HeaderNames, StatusCode}

/** The terrain base map's endpoints ([[Basemap]]). Kept next to [[Api]] (which appends
  * `BasemapApi.all`), like [[UiApi]]. Definitions only.
  *
  * REST and the CLI (`histmersion basemap status|fetch|rm`), no MCP tool — deliberately, like
  * `GET /api/config`: the base map is display data for the web page on this machine, of no use to
  * an agent reading the corpus. */
object BasemapApi:

  private val base = endpoint.in("api" / "basemap").errorOut(jsonBody[ApiError])

  /** `GET /api/basemap` — what is downloaded, where, and the command that downloads it. */
  val status: PublicEndpoint[Unit, ApiError, BasemapStatus, Any] =
    base.get.out(jsonBody[BasemapStatus])

  /** `POST /api/basemap/fetch?layer=&maxZoom=&terrainMaxZoom=&bbox=&dryRun=` — download the extracts
    * as a background JOB (poll `GET /api/jobs/{id}`); resumable, refused when the disk would be left
    * with less than `basemap.min-free-gb`. A dry run reads only the directories and reports sizes. */
  val fetch: PublicEndpoint[BasemapFetchQuery, ApiError, JobInfo, Any] =
    endpoint.post.in("api" / "basemap" / "fetch")
      .in(query[String]("layer").default("all").and(query[Option[Int]]("maxZoom")).and(query[Option[Int]]("terrainMaxZoom"))
        .and(query[Option[String]]("bbox")).and(query[Boolean]("dryRun").default(false)).mapTo[BasemapFetchQuery])
      .errorOut(fetchErrors).out(jsonBody[JobInfo])

  /** `DELETE /api/basemap?layer=vector|terrain|all` — remove the downloaded files (and an unfinished
    * download's parts). */
  val remove: PublicEndpoint[String, ApiError, BasemapRemoved, Any] =
    endpoint.delete.in("api" / "basemap").in(query[String]("layer").default("all"))
      .errorOut(fetchErrors).out(jsonBody[BasemapRemoved])

  /** `GET /api/basemap/{layer}/{z}/{x}/{y}` — one stored tile, as stored: a vector tile gzip-encoded
    * (`Content-Encoding: gzip`), a terrain tile as its image. 204 (empty) where the extract has no
    * tile — outside its region or deeper than it goes — so the page draws nothing there. */
  val tile: PublicEndpoint[(String, Int, Long, Long), ApiError, (Array[Byte], String, Option[String], String, StatusCode), Any] =
    endpoint.get.in("api" / "basemap" / path[String]("layer") / path[Int]("z") / path[Long]("x") / path[Long]("y"))
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](HeaderNames.ContentType))
      .out(header[Option[String]](HeaderNames.ContentEncoding))
      .out(header[String](HeaderNames.CacheControl))
      .out(statusCode)

  /** 503 while a restore holds the maintenance lock, 409 when a fetch runs already, 400 otherwise. */
  private def fetchErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(ApiError.hasCode(Backup.CodeMaintenance)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(Busy)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** The error code of a fetch asked for while one runs. */
  val Busy = "basemap_busy"

  /** The tile URL template the page fills in (`{z}/{x}/{y}`), `?v=` the layer's version. */
  def tileTemplate(layer: BasemapLayer, version: String): String =
    s"/api/basemap/${layer.wire}/{z}/{x}/{y}?v=$version"

  val all: List[AnyEndpoint] = List(status, fetch, remove, tile)
