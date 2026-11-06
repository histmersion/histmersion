package com.histmersion.api.basemap
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{Header, HeaderNames, StatusCode}
import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given
import com.histmersion.api.Api

object BasemapApi:
  object Tile:
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContentType = ContentType.T
    object ContentType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Encoding = Encoding.T
    object Encoding extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Etag = Etag.T
    object Etag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Format = Format.T
    object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileTemplate = TileTemplate.T
  object TileTemplate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



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
          .map { case (layer, maxZoom, terrainMaxZoom, bbox, dryRun, keepAll, focus) => Basemap.FetchQuery(Basemap.FetchQuery.Layer(layer), maxZoom.map(Basemap.FetchQuery.MaxZoom(_)), terrainMaxZoom.map(Basemap.FetchQuery.TerrainMaxZoom(_)), bbox.map(Basemap.FetchQuery.Bbox(_)), Basemap.FetchQuery.DryRun(dryRun), Basemap.FetchQuery.KeepAll(keepAll), focus.map(Basemap.FetchQuery.Focus(_))) }(v => (v.layer.value, v.maxZoom.map(_.value), v.terrainMaxZoom.map(_.value), v.bbox.map(_.value), v.dryRun.value, v.keepAll.value, v.focus.map(_.value)))
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
          .map { case (layer, focus, cache) => Basemap.RemoveQuery(Basemap.RemoveQuery.Layer(layer), focus.map(Basemap.RemoveQuery.Focus(_)), Basemap.RemoveQuery.Cache(cache)) }(v => (v.layer.value, v.focus.map(_.value), v.cache.value))
      )
      .errorOut(fetchErrors)
      .out(jsonBody[Basemap.Removed])

  lazy val tile: PublicEndpoint[
    (com.histmersion.api.basemap.Basemap.Layer.Wire, com.histmersion.api.basemap.Basemap.Zoom, com.histmersion.api.basemap.Basemap.Tile.Column, com.histmersion.api.basemap.Basemap.Tile.Row, Option[com.histmersion.api.basemap.Basemap.LayerInfo.Version], Option[com.histmersion.api.basemap.BasemapApi.Tile.Format]),
    Api.Error,
    (com.histmersion.api.basemap.BasemapApi.Tile.Bytes, com.histmersion.api.basemap.BasemapApi.Tile.ContentType, Option[com.histmersion.api.basemap.BasemapApi.Tile.Etag], com.histmersion.api.basemap.BasemapApi.Tile.Encoding, StatusCode),
    Any
  ] =
    (endpoint.get
      .in("api" / "basemap" / path[String]("layer") / path[Int]("z") / path[Long]("x") / path[Long]("y"))
      .in(query[Option[String]]("layers"))
      .in(query[Option[String]]("keys"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[Api.Error])
      .out(byteArrayBody)
      .out(header[String](HeaderNames.ContentType))
      .out(header[Option[String]](HeaderNames.ContentEncoding))
      .out(header[String](HeaderNames.CacheControl))
      .out(statusCode)).mapIn[(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire, _root_.com.histmersion.api.basemap.Basemap.Zoom, _root_.com.histmersion.api.basemap.Basemap.Tile.Column, _root_.com.histmersion.api.basemap.Basemap.Tile.Row, _root_.scala.Option[_root_.com.histmersion.api.basemap.Basemap.LayerInfo.Version], _root_.scala.Option[_root_.com.histmersion.api.basemap.BasemapApi.Tile.Format])]((refactorDecoded3: (_root_.java.lang.String, _root_.scala.Int, _root_.scala.Long, _root_.scala.Long, _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => (_root_.com.histmersion.api.basemap.Basemap.Layer.Wire((refactorDecoded3)._1), _root_.com.histmersion.api.basemap.Basemap.Zoom((refactorDecoded3)._2), _root_.com.histmersion.api.basemap.Basemap.Tile.Column((refactorDecoded3)._3), _root_.com.histmersion.api.basemap.Basemap.Tile.Row((refactorDecoded3)._4), ((refactorDecoded3)._5).map(refactorElement7 => _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Version(refactorElement7)), ((refactorDecoded3)._6).map(refactorElement8 => _root_.com.histmersion.api.basemap.BasemapApi.Tile.Format(refactorElement8))))((refactorEncoded3: (_root_.com.histmersion.api.basemap.Basemap.Layer.Wire, _root_.com.histmersion.api.basemap.Basemap.Zoom, _root_.com.histmersion.api.basemap.Basemap.Tile.Column, _root_.com.histmersion.api.basemap.Basemap.Tile.Row, _root_.scala.Option[_root_.com.histmersion.api.basemap.Basemap.LayerInfo.Version], _root_.scala.Option[_root_.com.histmersion.api.basemap.BasemapApi.Tile.Format])) => (((refactorEncoded3)._1).value, ((refactorEncoded3)._2).value, ((refactorEncoded3)._3).value, ((refactorEncoded3)._4).value, ((refactorEncoded3)._5).map(refactorElement9 => (refactorElement9).value), ((refactorEncoded3)._6).map(refactorElement10 => (refactorElement10).value))).mapOut[(_root_.com.histmersion.api.basemap.BasemapApi.Tile.Bytes, _root_.com.histmersion.api.basemap.BasemapApi.Tile.ContentType, _root_.scala.Option[_root_.com.histmersion.api.basemap.BasemapApi.Tile.Etag], _root_.com.histmersion.api.basemap.BasemapApi.Tile.Encoding, _root_.sttp.model.StatusCode)]((refactorDecoded4: (_root_.scala.Array[_root_.scala.Byte], _root_.java.lang.String, _root_.scala.Option[_root_.java.lang.String], _root_.java.lang.String, _root_.sttp.model.StatusCode)) => (_root_.com.histmersion.api.basemap.BasemapApi.Tile.Bytes((refactorDecoded4)._1), _root_.com.histmersion.api.basemap.BasemapApi.Tile.ContentType((refactorDecoded4)._2), ((refactorDecoded4)._3).map(refactorElement11 => _root_.com.histmersion.api.basemap.BasemapApi.Tile.Etag(refactorElement11)), _root_.com.histmersion.api.basemap.BasemapApi.Tile.Encoding((refactorDecoded4)._4), (refactorDecoded4)._5))((refactorEncoded4: (_root_.com.histmersion.api.basemap.BasemapApi.Tile.Bytes, _root_.com.histmersion.api.basemap.BasemapApi.Tile.ContentType, _root_.scala.Option[_root_.com.histmersion.api.basemap.BasemapApi.Tile.Etag], _root_.com.histmersion.api.basemap.BasemapApi.Tile.Encoding, _root_.sttp.model.StatusCode)) => (((refactorEncoded4)._1).value, ((refactorEncoded4)._2).value, ((refactorEncoded4)._3).map(refactorElement12 => (refactorElement12).value), ((refactorEncoded4)._4).value, (refactorEncoded4)._5))

  private def fetchErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        (Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Backup.CodeMaintenance))).andThen(refactorResult => ((refactorResult) == (true)))
      ),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Busy))).andThen(refactorResult2 => ((refactorResult2) == (true)))),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val Busy = "basemap_busy"

  def tileTemplate(layer: Basemap.Layer, version: com.histmersion.api.basemap.Basemap.LayerInfo.Version): com.histmersion.api.basemap.BasemapApi.TileTemplate =
    _root_.com.histmersion.api.basemap.BasemapApi.TileTemplate(s"/api/basemap/${layer.wire}/{z}/{x}/{y}?v=$version")

  lazy val all: List[AnyEndpoint] = List(status, fetch, remove, tile)
