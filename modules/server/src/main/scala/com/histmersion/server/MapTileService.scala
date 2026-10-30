package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.UiMapTilesSettings
import com.histmersion.db.*
import com.histmersion.domain.*
import com.histmersion.domain.maptiles.UndatedCells
import com.histmersion.server.maptiles.UndatedSites

import sttp.model.StatusCode

import javax.sql.DataSource

/** `GET /api/tiles/{layer}/{z}/{x}/{y}` ([[TilesApi]]): a layer's tile cut from the database, kept ([[TileCache]])
  * under the versions of what it reads, and answered 304 when the browser holds it.
  */
final class MapTileService(
    ds: DataSource,
    settings: () => UiMapTilesSettings,
    modernFrom: () => Long,
    scopeOf: Filter => PlaceScope,
    undatedSites: UndatedSites
):
  import MapTiles.*

  val cache: TileCache = TileCache(() => settings().cacheMb.toLong.max(1) * 1024 * 1024)

  def tile(q: TilesApi.TileQuery): Either[ApiError, TilesApi.TileAnswer] =
    val s = settings()
    for
      layer <- TileLayer
        .parse(q.layer)
        .toRight(ApiError(s"unknown layer '${q.layer}': one of ${TileLayer.values.map(_.wire).mkString(", ")}"))
      coord <- TileCoord.of(q.z, q.x, q.y, s.maxZoom).left.map(ApiError(_))
    yield
      val versions = currentVersions()
      val k = key(layer, coord, q, versions)
      val tag = etag(k)
      if matches(q.ifNoneMatch, tag) then
        TilesApi.TileAnswer(Array.emptyByteArray, TilesApi.ContentType, tag, CacheControl, StatusCode.NotModified)
      else
        val bytes = cache.get(k).getOrElse {
          val b = cut(layer, coord, q, TileGrid.of(s.maxZoom, s.maxPoints, s.cellPx))
          cache.put(k, b)
          b
        }
        TilesApi.TileAnswer(bytes, TilesApi.ContentType, tag, CacheControl, StatusCode.Ok)

  /** The groups' versions, their change rows folded first where they have grown many. */
  private def currentVersions(): Map[String, Long] =
    TileRepo.changeRows(ds).foreach((g, n) => if n > CompactAbove then TileRepo.compact(ds, g))
    TileRepo.versions(ds)

  private def window(f: Filter): Option[(Long, Long)] = f.period.map(p => (p.start.min(p.end), p.start.max(p.end)))

  /** A sites tile with the sites of no known date: the dated ones of the span and the undated ones (an undated one the
    * span shows by its findings counted once, as dated), each alone when the tile keeps that many, else counted by its
    * grid's squares together ([[UndatedCells]]).
    */
  private def sitesWithUndated(
      c: TileCoord,
      grid: TileGrid,
      window: (Long, Long),
      siteType: Option[String]
  ): List[TileCell] =
    val dated = TileRepo.datedSites(c, window, siteType, modernFrom())
    val datedCounts = com.augustnagro.magnum.connect(ds)(TileRepo.gridCells(dated, c, grid))
    val undatedCounts = undatedSites.counted(c, grid, siteType)
    if UndatedCells.total(datedCounts, undatedCounts) <= grid.pointsAt(c) then
      val datedItems = com.augustnagro.magnum.connect(ds)(TileRepo.items(dated, c))
      val shownByFindings = datedItems.filter(_.undated > 0).map(_.key)
      datedItems.map(_.copy(undated = 0)) ++ undatedSites.items(c, siteType, shownByFindings)
    else UndatedCells.merge(c.z, datedCounts, undatedCounts)

  private def cut(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, grid: TileGrid): Array[Byte] =
    val points = layer match
      case TileLayer.Sites =>
        // a sites tile with no span is every year's: the time rule needs a window
        val w = window(q.filter).getOrElse((Long.MinValue / 4, Long.MaxValue / 4))
        val cells =
          if q.withUndated then sitesWithUndated(c, grid, w, q.siteType)
          else TileRepo.siteCells(ds, c, grid, w, q.siteType, modernFrom())
        val records = TileRepo.sitesOf(ds, cells.filter(_.isItem).map(_.key))
        TileFeatures.of(c.z, cells, records.get, TileFeatures.site)
      case TileLayer.Events =>
        val f = q.filter
        def where(f: Filter) = EventRepo.listWhere(
          f,
          scopeOf(f),
          q.text,
          q.kind,
          q.historicity,
          q.placeId,
          EventRepo.Narrow(q.tier.map(_.trim).filter(_.nonEmpty), q.context.getOrElse(false), None)
        )
        val cells =
          if q.withUndated then TileRepo.eventCellsWithUndated(ds, c, grid, where(f), where(f.copy(period = None)))
          else TileRepo.eventCells(ds, c, grid, where(f))
        val records = EventRepo.geoOf(ds, cells.filter(_.isItem).map(_.key))
        TileFeatures.of(c.z, cells, records.get, TileFeatures.event)
      case TileLayer.Media =>
        val cells = TileRepo.mediaCells(ds, c, grid, q.kind, q.provider, q.license, window(q.filter))
        val records = MediaRepo.geoOf(ds, cells.filter(_.isItem).map(_.key))
        TileFeatures.of(c.z, cells, records.get, TileFeatures.media)
    VectorTileWriter.tile(Seq(layer.wire -> points))
