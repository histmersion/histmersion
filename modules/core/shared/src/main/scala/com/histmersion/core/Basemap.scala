package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The terrain base map (docs/research-terrain-basemap.md): MODERN terrain only — relief, coast,
  * water, rivers, natural land cover — drawn by the web page from two regional PMTiles extracts the
  * server downloads once (`histmersion basemap fetch`) and serves (`GET /api/basemap/{layer}/{z}/{x}/{y}`).
  * The wire types and the pure tile arithmetic; the server owns the files. */
enum BasemapLayer(val wire: String, val title: String):
  /** Coast, water, rivers, land cover and land use, natural points: vector tiles (MVT) without the
    * modern layers (`basemap.vector.drop`: roads, buildings, places, boundaries …). */
  case Vector  extends BasemapLayer("vector", "coast, water and land cover")
  /** Elevation, terrarium-encoded raster tiles the page shades into relief. */
  case Terrain extends BasemapLayer("terrain", "relief")
  /** The physical names — ranges, deserts, peninsulas, lakes, major rivers, peaks — from Natural
    * Earth, a small vector archive built by the fetch (seas and gulfs come with the vector layer). */
  case Labels  extends BasemapLayer("labels", "physical names")

  def file: String = s"$wire.pmtiles"

object BasemapLayer:
  def parse(s: String): Option[BasemapLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** `all` (or blank) is every layer. */
  def parseMany(s: String): Either[String, List[BasemapLayer]] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case "" | "all" => Right(values.toList)
      case other      => parse(other).map(List(_)).toRight(s"unknown base-map layer '$s' (vector, terrain, labels or all)")

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
    /** What the extract kept ([[Basemap.keptLine]]): the vector layer's filter. */
    kept: Option[String] = None,
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
    running: Option[String] = None,
    /** What a fetch would add (the physical names to an extract made before they existed). */
    hints: List[String] = Nil
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
    bbox: Option[String] = None, dryRun: Boolean = false, keepAll: Boolean = false)

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

  /** One entry of `basemap.vector.drop`: a vector layer dropped whole (`roads`), only some of its
    * kinds (`water swimming_pool fountain`), or all of it but some kinds (`pois +peak +volcano`). */
  final case class DropRule(layer: String, whole: Boolean, drop: Set[String] = Set.empty, spare: Set[String] = Set.empty)

  /** `basemap.vector.drop`: "roads; buildings; pois +peak" — `;` between layers; a layer alone is
    * dropped whole, `kind` drops that kind, `+kind` spares it and drops the rest. Everything not named
    * is KEPT, so a style can draw it without a new download. Empty: nothing dropped. Pure. */
  def parseDrop(s: String): Either[String, List[DropRule]] =
    val parts = s.split(";").toList.map(_.trim).filter(_.nonEmpty).map(_.split("\\s+").toList)
    val rules = parts.map { words =>
      val kinds = words.tail
      val spare = kinds.filter(_.startsWith("+")).map(_.drop(1)).filter(_.nonEmpty).toSet
      val drop  = kinds.filterNot(_.startsWith("+")).toSet
      DropRule(words.head, kinds.isEmpty, drop, spare)
    }
    val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
    val mixed = rules.filter(r => r.drop.nonEmpty && r.spare.nonEmpty).map(_.layer)
    if dup.nonEmpty then Left(s"basemap.vector.drop names ${dup.mkString(", ")} twice")
    else if mixed.nonEmpty then Left(s"basemap.vector.drop: ${mixed.mkString(", ")} both drops kinds and spares kinds (+kind) — one or the other")
    else Right(rules)

  /** What an extract kept, said in a line (`basemap status`): from its metadata — `keepAll`, the drop
    * list it was thinned with, or neither (an extract made before the drop list, which kept only coast,
    * water and land cover). Pure. */
  def keptLine(keepAll: Boolean, drop: Option[String]): String =
    if keepAll then "everything (unfiltered)"
    else drop match
      case Some(d) if d.trim.isEmpty => "everything (nothing dropped)"
      case Some(d)                   => s"all but: $d"
      case None                      => "earth, water and land cover only (fetched before the drop list)"
