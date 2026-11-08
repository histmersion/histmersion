package com.histmersion
package api.basemap
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{Header, HeaderNames, StatusCode}
import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure

object BasemapApi:
  object Tile:
    type Bytes = Bytes.T
    object Bytes extends Newtype[Array[Byte], Pure]
    type ContentType = ContentType.T
    object ContentType extends Newtype[String, Pure]
    type Encoding = Encoding.T
    object Encoding extends Newtype[String, Pure]
    type Etag = Etag.T
    object Etag extends Newtype[String, Pure]
    type Format = Format.T
    object Format extends Newtype[String, Pure]
  type TileTemplate = TileTemplate.T
  object TileTemplate extends Newtype[String, Pure]

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
          .map { case (layer, maxZoom, terrainMaxZoom, bbox, dryRun, keepAll, focus) =>
            Basemap.FetchQuery(
              Basemap.FetchQuery.Layer(layer),
              maxZoom.map(Basemap.FetchQuery.MaxZoom(_)),
              terrainMaxZoom.map(Basemap.FetchQuery.TerrainMaxZoom(_)),
              bbox.map(Basemap.FetchQuery.Bbox(_)),
              Basemap.FetchQuery.DryRun(dryRun),
              Basemap.FetchQuery.KeepAll(keepAll),
              focus.map(Basemap.FetchQuery.Focus(_))
            )
          }(v =>
            (
              v.layer.value,
              v.maxZoom.map(_.value),
              v.terrainMaxZoom.map(_.value),
              v.bbox.map(_.value),
              v.dryRun.value,
              v.keepAll.value,
              v.focus.map(_.value)
            )
          )
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
          .map { case (layer, focus, cache) =>
            Basemap.RemoveQuery(
              Basemap.RemoveQuery.Layer(layer),
              focus.map(Basemap.RemoveQuery.Focus(_)),
              Basemap.RemoveQuery.Cache(cache)
            )
          }(v => (v.layer.value, v.focus.map(_.value), v.cache.value))
      )
      .errorOut(fetchErrors)
      .out(jsonBody[Basemap.Removed])

  lazy val tile: PublicEndpoint[
    (
        Basemap.Layer.Wire,
        Basemap.Zoom,
        Basemap.Tile.Column,
        Basemap.Tile.Row,
        Option[Basemap.LayerInfo.Version],
        Option[Tile.Format]
    ),
    Api.Error,
    (Tile.Bytes, Tile.ContentType, Option[Tile.Etag], Tile.Encoding, StatusCode),
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
      .out(statusCode))
      .mapIn[
        (
            Basemap.Layer.Wire,
            Basemap.Zoom,
            Basemap.Tile.Column,
            Basemap.Tile.Row,
            Option[Basemap.LayerInfo.Version],
            Option[Tile.Format]
        )
      ]((refactorDecoded3: (String, Int, Long, Long, Option[String], Option[String])) =>
        (
          Basemap.Layer.Wire((refactorDecoded3)._1),
          Basemap.Zoom((refactorDecoded3)._2),
          Basemap.Tile.Column((refactorDecoded3)._3),
          Basemap.Tile.Row((refactorDecoded3)._4),
          ((refactorDecoded3)._5).map(refactorElement7 => Basemap.LayerInfo.Version(refactorElement7)),
          ((refactorDecoded3)._6).map(refactorElement8 => Tile.Format(refactorElement8))
        )
      )(
        (refactorEncoded3: (
            Basemap.Layer.Wire,
            Basemap.Zoom,
            Basemap.Tile.Column,
            Basemap.Tile.Row,
            Option[Basemap.LayerInfo.Version],
            Option[Tile.Format]
        )) =>
          (
            ((refactorEncoded3)._1).value,
            ((refactorEncoded3)._2).value,
            ((refactorEncoded3)._3).value,
            ((refactorEncoded3)._4).value,
            ((refactorEncoded3)._5).map(refactorElement9 => (refactorElement9).value),
            ((refactorEncoded3)._6).map(refactorElement10 => (refactorElement10).value)
          )
      )
      .mapOut[(Tile.Bytes, Tile.ContentType, Option[Tile.Etag], Tile.Encoding, StatusCode)](
        (refactorDecoded4: (Array[Byte], String, Option[String], String, StatusCode)) =>
          (
            Tile.Bytes((refactorDecoded4)._1),
            Tile.ContentType((refactorDecoded4)._2),
            ((refactorDecoded4)._3).map(refactorElement11 => Tile.Etag(refactorElement11)),
            Tile.Encoding((refactorDecoded4)._4),
            (refactorDecoded4)._5
          )
      )((refactorEncoded4: (Tile.Bytes, Tile.ContentType, Option[Tile.Etag], Tile.Encoding, StatusCode)) =>
        (
          ((refactorEncoded4)._1).value,
          ((refactorEncoded4)._2).value,
          ((refactorEncoded4)._3).map(refactorElement12 => (refactorElement12).value),
          ((refactorEncoded4)._4).value,
          (refactorEncoded4)._5
        )
      )

  private def fetchErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        (Api.Error
          .hasCode(Api.Error.Code(Backup.CodeMaintenance)))
          .andThen(refactorResult => ((refactorResult) == (true)))
      ),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])(
        (Api.Error.hasCode(Api.Error.Code(Busy))).andThen(refactorResult2 => ((refactorResult2) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val Busy = "basemap_busy"

  def tileTemplate(layer: Basemap.Layer, version: Basemap.LayerInfo.Version): TileTemplate =
    TileTemplate(s"/api/basemap/${layer.wire}/{z}/{x}/{y}?v=$version")

  lazy val all: List[AnyEndpoint] = List(status, fetch, remove, tile)
