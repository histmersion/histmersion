package com.histmersion
package domain.catalogue.maptiles
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.library.Filter
import com.histmersion.domain.DeepTime.SignedYear
import com.histmersion.domain.catalogue.{Event, Place}
import com.histmersion.api.catalogue.{TileLayer, TilesApi}
import com.histmersion.config.UiMapTilesSettings
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.catalogue.events.{EventSelection, EventService, EventSourceFilter, EventSql}
import layers.MapLayer
import com.histmersion.util.database.Transactor

import sttp.model.StatusCode
import com.histmersion.api.Api
import com.histmersion.domain.catalogue.periods.BPeriodRow

/** `GET /api/tiles/{layer}/{z}/{x}/{y}` ([[TilesApi]]): a layer's tile cut from the database, kept ([[TileCache]])
  * under the versions of what it reads, and answered 304 when the browser holds it. The sites and the events are the
  * catalogue's own layers; the pictures are `media`'s, a [[MapLayer]] the media domain draws. A mechanism over the
  * layers' SQL on one connection: it has no in-memory twin.
  */
final class MapTileService(
    db: Transactor,
    settings: () => UiMapTilesSettings,
    modernFrom: () => SignedYear,
    scopeOf: Filter => PlaceScope,
    undatedSites: UndatedSites,
    events: EventService,
    related: EventSourceFilter,
    media: MapLayer
):
  import MapTiles.*

  val cache: TileCache = TileCache(() => ByteCount.assume(settings().cacheMb.value.toLong.max(1) * 1024 * 1024))

  def tile(q: TilesApi.TileQuery): Either[Api.Error, TilesApi.TileAnswer] =
    val s = settings()
    for
      layer <- TileLayer
        .parse(q.layer)
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown layer '${q.layer}': one of ${TileLayer.values.map(_.wire).mkString(", ")}")
            )
          )
        )
      coord <- TileCoord
        .of(TileCoord.Z(q.z.value), TileCoord.X(q.x.value), TileCoord.Y(q.y.value), TileCoord.Z(s.maxZoom.value))
        .left
        .map(why => Api.Error(Problem.Unclassified(Legacy.Text(s"${why.sentence}"))))
    yield
      val versions = currentVersions()
      val k = key(layer, coord, q, versions)
      val tag = etag(k)
      if matches(q.ifNoneMatch.map(h => Etag(h.value)), tag) then
        TilesApi.TileAnswer(
          Api.FileBytes(Array.emptyByteArray),
          TilesApi.TileAnswer.ContentType(TilesApi.ContentType),
          TilesApi.TileAnswer.Etag(tag.value),
          TilesApi.TileAnswer.CacheControl(CacheControl),
          StatusCode.NotModified
        )
      else
        val bytes = cache.get(k).getOrElse {
          val b = cut(
            layer,
            coord,
            q,
            TileGrid.of(
              TileGrid.MaxZoom(s.maxZoom.value),
              TileGrid.MaxPoints(s.maxPoints.value),
              TileGrid.CellPx(s.cellPx.value)
            )
          )
          cache.put(k, b)
          b
        }
        TilesApi.TileAnswer(
          Api.FileBytes(bytes.value),
          TilesApi.TileAnswer.ContentType(TilesApi.ContentType),
          TilesApi.TileAnswer.Etag(tag.value),
          TilesApi.TileAnswer.CacheControl(CacheControl),
          StatusCode.Ok
        )

  /** The groups' versions, their change rows folded first where they have grown many. */
  private def currentVersions(): Map[TileRepo.GroupName, UndatedBuild.Version] =
    db.read(TileRepo.changeRows).foreach((g, n) => if n.value > CompactAbove then db.transact(TileRepo.compact(g)))
    db.read(TileRepo.versions)

  private def window(f: Filter): Option[(SignedYear, SignedYear)] =
    f.period.map(p => (SignedYear(p.start.value.min(p.end.value)), SignedYear(p.start.value.max(p.end.value))))

  /** A sites tile with the sites of no known date: the dated ones of the span and the undated ones (an undated one the
    * span shows by its findings counted once, as dated), each alone when the tile keeps that many, else counted by its
    * grid's squares together ([[UndatedCells]]).
    */
  private def sitesWithUndated(
      c: TileCoord,
      grid: TileGrid,
      window: (SignedYear, SignedYear),
      siteType: Option[TileRepo.SiteType]
  ): List[TileCell] =
    val dated = TileRepo.datedSites(c, window, siteType, modernFrom())
    val datedCounts = db.read(TileRepo.gridCells(dated, c, grid))
    val undatedCounts = undatedSites.counted(c, grid, siteType)
    if UndatedCells.total(datedCounts, undatedCounts).value <= grid.pointsAt(c).value then
      val datedItems = db.read(TileRepo.items(dated, c))
      val shownByFindings = datedItems.filter(_.undated.value > 0).map(cell => TileRepo.SiteId(cell.key.value))
      datedItems.map(_.modify(_.undated).setTo(TileCell.Undated(0))) ++ undatedSites.items(c, siteType, shownByFindings)
    else UndatedCells.merge(c.z, datedCounts, undatedCounts)

  private def cut(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, grid: TileGrid): VectorTileWriter.VectorTile =
    val points = layer match
      case TileLayer.Sites =>
        // a sites tile with no span is every year's: the time rule needs a window
        val w = window(q.filter).getOrElse((SignedYear(Long.MinValue / 4), SignedYear(Long.MaxValue / 4)))
        val siteType = q.siteType.map(t => TileRepo.SiteType(t.value))
        val cells =
          if q.withUndated then sitesWithUndated(c, grid, w, siteType)
          else db.read(TileRepo.siteCells(c, grid, w, siteType, modernFrom()))
        val records = db.read(TileRepo.sitesOf(cells.filter(_.isItem).map(cell => TileRepo.SiteId(cell.key.value))))
        TileFeatures.of(c.z, cells, key => records.get(TileRepo.SiteId(key.value)), TileFeatures.site)
      case TileLayer.Events =>
        val f = q.filter
        // an id that is no place id names a place no event is at: the tile is empty
        val place = q.placeId.map(_.value.trim).filter(_.nonEmpty) match
          case None    => Some(None)
          case Some(p) => Place.Id.option(p).map(Some(_))
        def where(f: Filter, place: Option[Place.Id]) = EventSelection(
          f,
          scopeOf(f),
          q.text.map(t => EventSelection.Text(t.value)),
          q.kind.map(k => EventSelection.Kind(k.value)),
          q.historicity.map(h => EventSelection.Historicity(h.value)),
          place,
          EventSql.Narrow(
            q.tier.map(t => EventSql.Narrow.Tier(t.value.trim)).filter(_.value.nonEmpty),
            EventSql.Narrow.Context(q.context.fold(false)(_.value)),
            None
          )
        ).sql(related)
        val cells: List[TileCell] = place.fold(List.empty[TileCell]) { p =>
          if q.withUndated then
            db.read(
              TileRepo.eventCellsWithUndated(c, grid, where(f, p), where(f.modify(_.period).setTo(None), p))
            )
          else db.read(TileRepo.eventCells(c, grid, where(f, p)))
        }
        val records = events.geoOf(cells.filter(_.isItem).map(cell => Event.Id.assume(cell.key.value)))
        TileFeatures.of(c.z, cells, key => records.get(Event.Id.assume(key.value)), TileFeatures.event)
      case TileLayer.Media =>
        val cells = db.read(
          TileRepo.cells(
            media.candidates(
              q,
              window(q.filter).map((lo, hi) => (BPeriodRow.PLo(lo.value), BPeriodRow.PHi(hi.value))),
              TileRepo.envelope(c)
            ),
            c,
            grid
          )
        )
        val drawn = media.features(cells.filter(_.isItem).map(cell => TileKey(cell.key.value)))
        TileFeatures.of(c.z, cells, key => drawn.get(TileKey(key.value)), (cell, feature) => feature(cell))
    VectorTileWriter.tile(Seq(VectorTileWriter.LayerName(layer.wire.value) -> points))
