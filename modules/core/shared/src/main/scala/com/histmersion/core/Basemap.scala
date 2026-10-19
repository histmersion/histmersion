package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The terrain base map (docs/research-terrain-basemap.md): MODERN terrain only — relief, coast,
  * water, rivers, natural land cover — drawn by the web page from two regional PMTiles extracts the
  * server downloads once (`histmersion basemap fetch`) and serves (`GET /api/basemap/{layer}/{z}/{x}/{y}`).
  * The wire types and the pure tile arithmetic; the server owns the files. */
enum BasemapLayer(val wire: String, val title: String):
  /** Coast, water, rivers and land cover: vector tiles (MVT) thinned to those layers. */
  case Vector  extends BasemapLayer("vector", "coast, water and land cover")
  /** Elevation, terrarium-encoded raster tiles the page shades into relief. */
  case Terrain extends BasemapLayer("terrain", "relief")

  def file: String = s"$wire.pmtiles"

object BasemapLayer:
  def parse(s: String): Option[BasemapLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** `all` (or blank) is both layers. */
  def parseMany(s: String): Either[String, List[BasemapLayer]] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case "" | "all" => Right(values.toList)
      case other      => parse(other).map(List(_)).toRight(s"unknown base-map layer '$s' (vector, terrain or all)")

/** One downloaded layer, as the server finds it on disk. */
final case class BasemapLayerInfo(
    layer: String,
    present: Boolean,
    file: String,
    bytes: Long = 0,
    minZoom: Option[Int] = None,
    maxZoom: Option[Int] = None,
    bbox: Option[String] = None,
    tiles: Option[Long] = None,
    tileType: Option[String] = None,
    /** Where it came from (the build file or the URL) and when it was fetched (ISO instant). */
    source: Option[String] = None,
    fetchedAt: Option[String] = None,
    /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
    version: Option[String] = None,
    /** The credit line (HTML) the page shows with the layer. */
    attribution: String = "",
    /** Why the file is there but cannot be read. */
    error: Option[String] = None
) derives KindlingsCodecAsObject

object BasemapLayerInfo:
  given Schema[BasemapLayerInfo] = KindlingsSchema.derived[BasemapLayerInfo].schema

/** `GET /api/basemap`: is the terrain base map there, what of it, and how to get it. */
final case class BasemapStatus(
    /** The vector layer is there (the relief is optional: without it the map is flat). */
    available: Boolean,
    dir: String,
    /** The region the next fetch downloads (`basemap.bbox`). */
    bbox: String,
    layers: List[BasemapLayerInfo],
    /** The command that downloads it, for a page or a message to show. */
    fetchCommand: String,
    /** Free space where the files go, in bytes. */
    freeBytes: Long = 0,
    /** A fetch job running now. */
    running: Option[String] = None
) derives KindlingsCodecAsObject

object BasemapStatus:
  given Schema[BasemapStatus] = KindlingsSchema.derived[BasemapStatus].schema

/** `DELETE /api/basemap?layer=`: what was removed. */
final case class BasemapRemoved(removed: List[String], bytes: Long) derives KindlingsCodecAsObject

object BasemapRemoved:
  given Schema[BasemapRemoved] = KindlingsSchema.derived[BasemapRemoved].schema

/** The fetch's options: which layers, how deep, where; a dry run downloads only the directories and
  * reports what the extract would take. Empty values fall back to the configuration. */
final case class BasemapFetchQuery(layer: String = "all", maxZoom: Option[Int] = None, terrainMaxZoom: Option[Int] = None,
    bbox: Option[String] = None, dryRun: Boolean = false)

object Basemap:

  /** The command a person runs to download the base map. */
  val FetchCommand = "histmersion basemap fetch"

  /** The deepest zoom a fetch accepts: deeper extracts are tens of gigabytes for this region. */
  val MaxZoomCap = 14

  /** Web Mercator's latitude limit. */
  val MaxLat = 85.0511287798066

  /** The tile column of longitude `lon` at zoom `z`. Pure. */
  def tileX(lon: Double, z: Int): Long =
    val n = 1L << z
    math.floor((lon + 180.0) / 360.0 * n).toLong.max(0L).min(n - 1)

  /** The tile row of latitude `lat` at zoom `z` (row 0 at the north). Pure. */
  def tileY(lat: Double, z: Int): Long =
    val n   = 1L << z
    val r   = math.toRadians(lat.max(-MaxLat).min(MaxLat))
    val y   = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n
    math.floor(y).toLong.max(0L).min(n - 1)

  /** The tiles of zoom `z` that touch `b`: columns and rows, inclusive. Pure. */
  def tileRange(b: BBox, z: Int): ((Long, Long), (Long, Long)) =
    ((tileX(b.minLon, z), tileX(b.maxLon, z)), (tileY(b.maxLat, z), tileY(b.minLat, z)))

  /** How many tiles zooms `minZoom`…`maxZoom` hold over `b`. Pure. */
  def tileCount(b: BBox, minZoom: Int, maxZoom: Int): Long =
    (minZoom to maxZoom).map { z =>
      val ((x0, x1), (y0, y1)) = tileRange(b, z)
      (x1 - x0 + 1) * (y1 - y0 + 1)
    }.sum

  /** A bbox a fetch accepts: four numbers, west < east, south < north, inside the world. */
  def validBBox(s: String): Either[String, BBox] =
    BBox.parse(s).toRight(s"'$s' is not minLon,minLat,maxLon,maxLat").flatMap { b =>
      if b.minLon >= b.maxLon || b.minLat >= b.maxLat then Left(s"'$s': west must be < east and south < north")
      else if b.minLon < -180 || b.maxLon > 180 || b.minLat < -90 || b.maxLat > 90 then Left(s"'$s' is outside the world")
      else Right(b)
    }

  /** One kept layer of the vector extract: its name and the feature kinds it keeps (all when empty)
    * or drops. */
  final case class LayerRule(layer: String, keep: Set[String], drop: Set[String])

  /** `basemap.vector.layers`: "earth; water -canal -ditch; landcover forest scrub" — `;` between
    * layers, then each layer's kinds kept (`kind`) or dropped (`-kind`). Pure. */
  def parseLayers(s: String): Either[String, List[LayerRule]] =
    val rules = s.split(";").toList.map(_.trim).filter(_.nonEmpty).map { part =>
      val words = part.split("\\s+").toList
      val kinds = words.tail
      LayerRule(words.head, kinds.filterNot(_.startsWith("-")).toSet, kinds.filter(_.startsWith("-")).map(_.drop(1)).filter(_.nonEmpty).toSet)
    }
    if rules.isEmpty then Left("basemap.vector.layers names no layer")
    else
      val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
      if dup.nonEmpty then Left(s"basemap.vector.layers names ${dup.mkString(", ")} twice") else Right(rules)
