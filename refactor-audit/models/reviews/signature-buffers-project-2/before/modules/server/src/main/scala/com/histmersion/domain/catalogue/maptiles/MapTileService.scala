package com.histmersion.domain.catalogue.maptiles

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.{TileLayer, TilesApi}
import com.histmersion.config.UiMapTilesSettings
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.catalogue.events.{EventSelection, EventService, EventSourceFilter, EventSql}
import com.histmersion.domain.catalogue.maptiles.layers.MapLayer
import com.histmersion.util.database.Transactor

import sttp.model.StatusCode
import com.histmersion.api.Api

/** `GET /api/tiles/{layer}/{z}/{x}/{y}` ([[TilesApi]]): a layer's tile cut from the database, kept ([[TileCache]])
  * under the versions of what it reads, and answered 304 when the browser holds it. The sites and the events are the
  * catalogue's own layers; the pictures are `media`'s, a [[MapLayer]] the media domain draws. A mechanism over the
  * layers' SQL on one connection: it has no in-memory twin.
  */
final class MapTileService(
    db: Transactor,
    settings: () => UiMapTilesSettings,
    modernFrom: () => MapTileService.ModernFromResult,
    scopeOf: Filter => PlaceScope,
    undatedSites: UndatedSites,
    events: EventService,
    related: EventSourceFilter,
    media: MapLayer
):
  import MapTiles.*

  val cache: TileCache = TileCache(() => settings().cacheMb.toLong.max(1) * 1024 * 1024)

  def tile(q: TilesApi.TileQuery): Either[Api.Error, TilesApi.TileAnswer] =
    val s = settings()
    for
      layer <- TileLayer
        .parse(q.layer)
        .toRight(Api.Error(s"unknown layer '${q.layer}': one of ${TileLayer.values.map(_.wire).mkString(", ")}"))
      coord <- TileCoord.of(q.z, q.x, q.y, s.maxZoom).left.map(Api.Error(_))
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
  private def currentVersions(): Map[com.histmersion.domain.catalogue.maptiles.MapTileService.CurrentVersionsResultPart1, com.histmersion.domain.catalogue.maptiles.MapTileService.CurrentVersionsResultPart2] =
    db.read(TileRepo.changeRows).foreach((g, n) => if n > CompactAbove then db.transact(TileRepo.compact(g)))
    db.read(TileRepo.versions)

  private def window(f: Filter): Option[(com.histmersion.domain.catalogue.maptiles.MapTileService.WindowResultPart1, com.histmersion.domain.catalogue.maptiles.MapTileService.WindowResultPart2)] = f.period.map(p => (p.start.min(p.end), p.start.max(p.end)))

  /** A sites tile with the sites of no known date: the dated ones of the span and the undated ones (an undated one the
    * span shows by its findings counted once, as dated), each alone when the tile keeps that many, else counted by its
    * grid's squares together ([[UndatedCells]]).
    */
  private def sitesWithUndated(
      c: TileCoord,
      grid: TileGrid,
      window: (com.histmersion.domain.catalogue.maptiles.MapTileService.SitesWithUndatedWindowPart1, com.histmersion.domain.catalogue.maptiles.MapTileService.SitesWithUndatedWindowPart2),
      siteType: Option[com.histmersion.domain.catalogue.maptiles.MapTileService.SitesWithUndatedSiteType]
  ): List[TileCell] =
    val dated = TileRepo.datedSites(c, window, siteType, modernFrom())
    val datedCounts = db.read(TileRepo.gridCells(dated, c, grid))
    val undatedCounts = undatedSites.counted(c, grid, siteType)
    if UndatedCells.total(datedCounts, undatedCounts) <= grid.pointsAt(c) then
      val datedItems = db.read(TileRepo.items(dated, c))
      val shownByFindings = datedItems.filter(_.undated > 0).map(_.key)
      datedItems.map(_.modify(_.undated).setTo(0)) ++ undatedSites.items(c, siteType, shownByFindings)
    else UndatedCells.merge(c.z, datedCounts, undatedCounts)

  private def cut(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, grid: TileGrid): Array[com.histmersion.domain.catalogue.maptiles.MapTileService.CutResult] =
    val points = layer match
      case TileLayer.Sites =>
        // a sites tile with no span is every year's: the time rule needs a window
        val w = window(q.filter).getOrElse((Long.MinValue / 4, Long.MaxValue / 4))
        val cells =
          if q.withUndated then sitesWithUndated(c, grid, w, q.siteType)
          else db.read(TileRepo.siteCells(c, grid, w, q.siteType, modernFrom()))
        val records = db.read(TileRepo.sitesOf(cells.filter(_.isItem).map(_.key)))
        TileFeatures.of(c.z, cells, records.get, TileFeatures.site)
      case TileLayer.Events =>
        val f = q.filter
        def where(f: Filter) = EventSelection(
          f,
          scopeOf(f),
          q.text,
          q.kind,
          q.historicity,
          q.placeId,
          EventSql.Narrow(q.tier.map(_.trim).filter(_.nonEmpty), q.context.getOrElse(false), None)
        ).sql(related)
        val cells =
          if q.withUndated then
            db.read(TileRepo.eventCellsWithUndated(c, grid, where(f), where(f.modify(_.period).setTo(None))))
          else db.read(TileRepo.eventCells(c, grid, where(f)))
        val records = events.geoOf(cells.filter(_.isItem).map(_.key))
        TileFeatures.of(c.z, cells, records.get, TileFeatures.event)
      case TileLayer.Media =>
        val cells = db.read(TileRepo.cells(media.candidates(q, window(q.filter), TileRepo.envelope(c)), c, grid))
        val drawn = media.features(cells.filter(_.isItem).map(_.key))
        TileFeatures.of(c.z, cells, drawn.get, (cell, feature) => feature(cell))
    VectorTileWriter.tile(Seq(layer.wire -> points))

object MapTileService:
  type ModernFromResult = ModernFromResult.T
  object ModernFromResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CurrentVersionsResultPart1 = CurrentVersionsResultPart1.T
  object CurrentVersionsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentVersionsResultPart2 = CurrentVersionsResultPart2.T
  object CurrentVersionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowResultPart1 = WindowResultPart1.T
  object WindowResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowResultPart2 = WindowResultPart2.T
  object WindowResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesWithUndatedWindowPart1 = SitesWithUndatedWindowPart1.T
  object SitesWithUndatedWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesWithUndatedWindowPart2 = SitesWithUndatedWindowPart2.T
  object SitesWithUndatedWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesWithUndatedSiteType = SitesWithUndatedSiteType.T
  object SitesWithUndatedSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CutResult = CutResult.T
  object CutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
