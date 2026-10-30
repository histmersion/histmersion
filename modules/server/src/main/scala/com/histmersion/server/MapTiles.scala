package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.UiMapTilesSettings
import com.histmersion.db.*
import com.histmersion.domain.*
import com.histmersion.domain.maptiles.UndatedCells
import com.histmersion.server.maptiles.UndatedSites

import sttp.model.StatusCode

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.sql.DataSource

/** The tiles a server keeps, by key, the least recently asked for going first once they hold more than `maxBytes`. A
  * tile's key carries the versions of what it was cut from, so a write makes the tiles it touched unreachable at once;
  * they age out. Thread-safe.
  */
final class TileCache(maxBytes: () => Long):
  private val tiles = new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true)
  // `var`: the bytes held
  private var held = 0L

  def get(key: String): Option[Array[Byte]] = synchronized(Option(tiles.get(key)))

  def put(key: String, bytes: Array[Byte]): Unit = synchronized:
    Option(tiles.put(key, bytes)).foreach(old => held -= old.length)
    held += bytes.length
    val it = tiles.entrySet.iterator
    while held > maxBytes() && it.hasNext do
      val e = it.next()
      held -= e.getValue.length
      it.remove()

  def clear(): Unit = synchronized:
    tiles.clear()
    held = 0L

  def stats: (Int, Long) = synchronized((tiles.size, held))

object MapTiles:
  /** A tile's caching rule: the browser asks again each time and the server answers 304 while the data's version is
    * unchanged — never a stale tile after a write, a few bytes when nothing changed.
    */
  val CacheControl = "no-cache"

  /** A group's change rows above which they are folded into one ([[TileRepo.compact]]). */
  val CompactAbove = 256

  /** Pure: what a tile request is, as a cache key and an ETag: the layer, the tile, every parameter the layer reads
    * (the rest — a filter's paging, another layer's options — left out, so they never split one tile into many) and the
    * versions of the groups it reads.
    */
  def key(layer: TileLayer, c: TileCoord, q: TilesApi.TileQuery, versions: Map[String, Long]): String =
    val f = q.filter
    val time = f.period.fold("")(p => s"${p.start}..${p.end}")
    val undated = if q.withUndated then "undated" else ""
    val params = layer match
      case TileLayer.Sites  => List(time, q.siteType.getOrElse(""), undated)
      case TileLayer.Media  => List(time, q.kind.getOrElse(""), q.provider.getOrElse(""), q.license.getOrElse(""))
      case TileLayer.Events =>
        List(
          f.copy(limit = 0, offset = 0, sort = Filter().sort).toString,
          q.kind.getOrElse(""),
          q.tier.getOrElse(""),
          q.historicity.getOrElse(""),
          q.context.fold("")(_.toString),
          q.text.getOrElse(""),
          q.placeId.getOrElse(""),
          undated
        )
    val v = TileRepo.Groups(layer).map(g => s"$g=${versions.getOrElse(g, 0L)}")
    (List(layer.wire, s"${c.z}/${c.x}/${c.y}") ++ params ++ v).mkString("|")

  /** Pure: a key's ETag (a strong one: the tile's bytes follow from the key). */
  def etag(key: String): String =
    val d = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))
    "\"" + d.take(12).map(b => f"${b & 0xff}%02x").mkString + "\""

  /** Pure: does the browser's `If-None-Match` name `tag` (one of a list, or `*`)? */
  def matches(ifNoneMatch: Option[String], tag: String): Boolean =
    ifNoneMatch.exists(h => h.split(",").map(_.trim.stripPrefix("W/")).exists(t => t == tag || t == "*"))

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
