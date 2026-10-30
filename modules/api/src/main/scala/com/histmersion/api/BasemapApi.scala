package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{Header, HeaderNames, StatusCode}

object BasemapApi:

  private lazy val base = endpoint.in("api" / "basemap").errorOut(jsonBody[ApiError])

  lazy val status: PublicEndpoint[Unit, ApiError, BasemapStatus, Any] =
    base.get.out(jsonBody[BasemapStatus])

  lazy val fetch: PublicEndpoint[BasemapFetchQuery, ApiError, JobInfo, Any] =
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
          .mapTo[BasemapFetchQuery]
      )
      .errorOut(fetchErrors)
      .out(jsonBody[JobInfo])

  lazy val remove: PublicEndpoint[BasemapRemoveQuery, ApiError, BasemapRemoved, Any] =
    endpoint.delete
      .in("api" / "basemap")
      .in(
        query[String]("layer")
          .default("all")
          .and(query[Option[String]]("focus"))
          .and(query[Boolean]("cache").default(false))
          .mapTo[BasemapRemoveQuery]
      )
      .errorOut(fetchErrors)
      .out(jsonBody[BasemapRemoved])

  lazy val tile: PublicEndpoint[
    (String, Int, Long, Long, Option[String], Option[String]),
    ApiError,
    (Array[Byte], String, Option[String], String, StatusCode),
    Any
  ] =
    endpoint.get
      .in("api" / "basemap" / path[String]("layer") / path[Int]("z") / path[Long]("x") / path[Long]("y"))
      .in(query[Option[String]]("layers"))
      .in(query[Option[String]]("keys"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](HeaderNames.ContentType))
      .out(header[Option[String]](HeaderNames.ContentEncoding))
      .out(header[String](HeaderNames.CacheControl))
      .out(statusCode)

  private def fetchErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(
        ApiError.hasCode(Backup.CodeMaintenance)
      ),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(Busy)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  val Busy = "basemap_busy"

  def tileTemplate(layer: BasemapLayer, version: String): String =
    s"/api/basemap/${layer.wire}/{z}/{x}/{y}?v=$version"

  lazy val all: List[AnyEndpoint] = List(status, fetch, remove, tile)
