package com.histmersion.api.basemap
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{Header, HeaderNames, StatusCode}
import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given
import com.histmersion.api.Api

object BasemapApi:

  private lazy val base = endpoint.in("api" / "basemap").errorOut(jsonBody[Api.Error])

  lazy val status: PublicEndpoint[Unit, Api.Error, Basemap.Status, Any] =
    base.get.out(jsonBody[Basemap.Status])

  lazy val fetch: PublicEndpoint[Basemap.FetchQuery, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "basemap" / "fetch")
      .in(
        query[String]("layer")
          .default("all")
          .and(query[Option[Int]]("maxZoom"))
          .and(query[Option[Int]]("terrainMaxZoom"))
          .and(query[Option[String]]("bbox"))
          .and(query[Boolean]("dryRun").default(false))
          .and(query[Boolean]("keepAll").default(false))
          .and(query[Option[String]]("focus"))
          .mapTo[Basemap.FetchQuery]
      )
      .errorOut(fetchErrors)
      .out(jsonBody[JobInfo])

  lazy val remove: PublicEndpoint[Basemap.RemoveQuery, Api.Error, Basemap.Removed, Any] =
    endpoint.delete
      .in("api" / "basemap")
      .in(
        query[String]("layer")
          .default("all")
          .and(query[Option[String]]("focus"))
          .and(query[Boolean]("cache").default(false))
          .mapTo[Basemap.RemoveQuery]
      )
      .errorOut(fetchErrors)
      .out(jsonBody[Basemap.Removed])

  lazy val tile: PublicEndpoint[
    (String, Int, Long, Long, Option[String], Option[String]),
    Api.Error,
    (Array[Byte], String, Option[String], String, StatusCode),
    Any
  ] =
    endpoint.get
      .in("api" / "basemap" / path[String]("layer") / path[Int]("z") / path[Long]("x") / path[Long]("y"))
      .in(query[Option[String]]("layers"))
      .in(query[Option[String]]("keys"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[Api.Error])
      .out(byteArrayBody)
      .out(header[String](HeaderNames.ContentType))
      .out(header[Option[String]](HeaderNames.ContentEncoding))
      .out(header[String](HeaderNames.CacheControl))
      .out(statusCode)

  private def fetchErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        Api.Error.hasCode(Backup.CodeMaintenance)
      ),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])(Api.Error.hasCode(Busy)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val Busy = "basemap_busy"

  def tileTemplate(layer: Basemap.Layer, version: String): String =
    s"/api/basemap/${layer.wire}/{z}/{x}/{y}?v=$version"

  lazy val all: List[AnyEndpoint] = List(status, fetch, remove, tile)
