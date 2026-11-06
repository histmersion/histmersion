package com.histmersion.api.basemap
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{Header, HeaderNames, StatusCode}
import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given
import com.histmersion.api.Api

object BasemapApi:

  type TilePart3Part1 = TilePart3Part1.T
  object TilePart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TilePart1Part1 = TilePart1Part1.T
  object TilePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart1Part2 = TilePart1Part2.T
  object TilePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart1Part3 = TilePart1Part3.T
  object TilePart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart1Part4 = TilePart1Part4.T
  object TilePart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart1Part5 = TilePart1Part5.T
  object TilePart1Part5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart1Part6 = TilePart1Part6.T
  object TilePart1Part6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart3Part2 = TilePart3Part2.T
  object TilePart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart3Part3 = TilePart3Part3.T
  object TilePart3Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePart3Part4 = TilePart3Part4.T
  object TilePart3Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileTemplateResult = TileTemplateResult.T
  object TileTemplateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileTemplateVersion = TileTemplateVersion.T
  object TileTemplateVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    (com.histmersion.api.basemap.BasemapApi.TilePart1Part1, com.histmersion.api.basemap.BasemapApi.TilePart1Part2, com.histmersion.api.basemap.BasemapApi.TilePart1Part3, com.histmersion.api.basemap.BasemapApi.TilePart1Part4, Option[com.histmersion.api.basemap.BasemapApi.TilePart1Part5], Option[com.histmersion.api.basemap.BasemapApi.TilePart1Part6]),
    Api.Error,
    (com.histmersion.api.basemap.BasemapApi.TilePart3Part1, com.histmersion.api.basemap.BasemapApi.TilePart3Part2, Option[com.histmersion.api.basemap.BasemapApi.TilePart3Part3], com.histmersion.api.basemap.BasemapApi.TilePart3Part4, StatusCode),
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

  def tileTemplate(layer: Basemap.Layer, version: com.histmersion.api.basemap.BasemapApi.TileTemplateVersion): com.histmersion.api.basemap.BasemapApi.TileTemplateResult =
    s"/api/basemap/${layer.wire}/{z}/{x}/{y}?v=$version"

  lazy val all: List[AnyEndpoint] = List(status, fetch, remove, tile)
