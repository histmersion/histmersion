package com.histmersion.api.basemap

import com.histmersion.api.BBox
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Basemap:

  val FetchCommand = "histmersion basemap fetch"

  /** Deeper extracts run to tens of gigabytes. */
  val MaxZoomCap = 14

  /** Web Mercator's latitude limit. */
  val MaxLat = 85.0511287798066

  def validFocusName(s: String): Either[String, String] =
    val n = s.trim.toLowerCase(java.util.Locale.ROOT)
    Either.cond(n.matches("[a-z0-9][a-z0-9-]{0,40}"), n, s"'$s' is not a focus region name (letters, digits and -)")

  def focusRegion(spec: String): Either[String, (String, BBox)] =
    spec.trim.split("\\s+", 2).toList match
      case n :: b :: Nil => for name <- validFocusName(n); box <- validBBox(b.replace(" ", "")) yield (name, box)
      case _             => Left(s"'$spec' is not \"name minLon,minLat,maxLon,maxLat\"")

  def focusNameOf(b: BBox): String =
    def f(d: Double) = java.math
      .BigDecimal(d)
      .setScale(2, java.math.RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
      .replace("-", "m")
      .replace(".", "p")
    s"bbox-${f(b.minLon)}-${f(b.minLat)}-${f(b.maxLon)}-${f(b.maxLat)}"

  def tileX(lon: Double, z: Int): Long =
    val n = 1L << z
    math.floor((lon + 180.0) / 360.0 * n).toLong.max(0L).min(n - 1)

  def tileY(lat: Double, z: Int): Long =
    val n = 1L << z
    val r = math.toRadians(lat.max(-MaxLat).min(MaxLat))
    val y = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n
    math.floor(y).toLong.max(0L).min(n - 1)

  def tileRange(b: BBox, z: Int): ((Long, Long), (Long, Long)) =
    ((tileX(b.minLon, z), tileX(b.maxLon, z)), (tileY(b.maxLat, z), tileY(b.minLat, z)))

  def tileCount(b: BBox, minZoom: Int, maxZoom: Int): Long =
    (minZoom to maxZoom).map { z =>
      val ((x0, x1), (y0, y1)) = tileRange(b, z)
      (x1 - x0 + 1) * (y1 - y0 + 1)
    }.sum

  /** Web Mercator's ±85.05°: as far as web maps go. */
  val World: BBox = BBox(-180, -85.05, 180, 85.05)

  def validBBox(s: String): Either[String, BBox] =
    if s.trim.equalsIgnoreCase("world") then Right(World)
    else
      BBox.parse(s).toRight(s"'$s' is not minLon,minLat,maxLon,maxLat").flatMap { b =>
        if b.minLon >= b.maxLon || b.minLat >= b.maxLat then Left(s"'$s': west must be < east and south < north")
        else if b.minLon < -180 || b.maxLon > 180 || b.minLat < -90 || b.maxLat > 90 then
          Left(s"'$s' is outside the world")
        else Right(b)
      }

  final case class DropRule(
      layer: String,
      whole: Boolean,
      drop: Set[String] = Set.empty,
      spare: Set[String] = Set.empty
  )

  def parseDrop(s: String): Either[String, List[DropRule]] =
    val parts = s.split(";").toList.map(_.trim).filter(_.nonEmpty).map(_.split("\\s+").toList)
    val rules = parts.map { words =>
      val kinds = words.tail
      val spare = kinds.filter(_.startsWith("+")).map(_.drop(1)).filter(_.nonEmpty).toSet
      val drop = kinds.filterNot(_.startsWith("+")).toSet
      DropRule(words.head, kinds.isEmpty, drop, spare)
    }
    val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
    val mixed = rules.filter(r => r.drop.nonEmpty && r.spare.nonEmpty).map(_.layer)
    if dup.nonEmpty then Left(s"basemap.vector.drop names ${dup.mkString(", ")} twice")
    else if mixed.nonEmpty then
      Left(s"basemap.vector.drop: ${mixed.mkString(", ")} both drops kinds and spares kinds (+kind) — one or the other")
    else Right(rules)

  final case class Read(geoms: Set[Int] = Set.empty, kinds: Set[String] = Set.empty):
    def merge(o: Read): Read =
      Read(
        if geoms.isEmpty || o.geoms.isEmpty then Set.empty else geoms ++ o.geoms,
        if kinds.isEmpty || o.kinds.isEmpty then Set.empty else kinds ++ o.kinds
      )

  private val KindName = "[A-Za-z0-9_:.-]+".r

  def parseSubset(s: String): Either[String, Map[String, Read]] =
    val parts = s.split(",").toList.map(_.trim).filter(_.nonEmpty)
    val parsed = parts.map { p =>
      val (head, kindPart) = p.split("~", 2).toList match
        case h :: k :: Nil => (h, Some(k))
        case h :: _        => (h, None)
        case Nil           => ("", None)
      val kinds = kindPart.map(_.split("\\|").toList.map(_.trim).filter(_.nonEmpty))
      val kindsOk = kinds.forall(ks => ks.nonEmpty && ks.forall(KindName.matches))
      head.split(":", 2).toList match
        case _ if !kindsOk => Left(s"'$p': kinds are `~kind|kind`")
        case l :: g :: Nil =>
          val codes = g.toList.map { case 'p' => 1; case 'l' => 2; case 'a' => 3; case _ => 0 }
          Either.cond(
            l.nonEmpty && codes.nonEmpty && !codes.contains(0),
            l -> Read(codes.toSet, kinds.fold(Set.empty)(_.toSet)),
            s"'$p': geometries are p, l and a"
          )
        case l :: Nil if l.nonEmpty => Right(l -> Read(Set.empty, kinds.fold(Set.empty)(_.toSet)))
        case _                      => Left(s"'$p' is not layer[:geometries][~kind|kind]")
    }
    parsed
      .collectFirst { case Left(e) => e }
      .toLeft(
        parsed
          .collect { case Right(kv) => kv }
          .groupMapReduce(_._1)(_._2)(_.merge(_))
      )

  def subsetParam(m: Map[String, Read], fromZoom: Map[String, Int] = Map.empty): String =
    m.toList
      .sortBy(_._1)
      .map { (l, r) =>
        val g = if r.geoms.isEmpty then ""
        else ":" + r.geoms.toList.sorted.map { case 1 => 'p'; case 2 => 'l'; case _ => 'a' }.mkString
        val k = if r.kinds.isEmpty then "" else "~" + r.kinds.toList.sorted.mkString("|")
        l + g + k + fromZoom.get(l).filter(_ > 0).fold("")(z => s"@$z")
      }
      .mkString(",")

  def subsetAt(s: String, z: Int): String =
    s.split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split("@", 2).toList match
          case l :: n :: Nil =>
            n.trim.toIntOption match
              case Some(from) => Option.when(z >= from)(l)
              case None       => Some(e)
          case _ => Some(e)
      }
      .mkString(",")

  val LegacyLacks: List[String] = List("boundaries", "buildings", "landuse", "places", "pois", "roads", "transit")

  def droppedLayers(keepAll: Boolean, drop: Option[String]): List[String] =
    if keepAll then Nil
    else
      drop match
        case Some(d) => parseDrop(d).toOption.getOrElse(Nil).filter(_.whole).map(_.layer).distinct.sorted
        case None    => LegacyLacks

  def missingFor(needs: List[String], dropped: List[String]): List[String] =
    needs.filter(dropped.contains).distinct.sorted

  val KeepAllCommand = s"$FetchCommand --layer vector --keep-all"

  def keptLine(keepAll: Boolean, drop: Option[String]): String =
    if keepAll then "everything (unfiltered)"
    else
      drop match
        case Some(d) if d.trim.isEmpty => "everything (nothing dropped)"
        case Some(d)                   => s"all but: $d"
        case None                      => "earth, water and land cover only (fetched before the drop list)"

  final case class FetchQuery(
      layer: String = "all",
      maxZoom: Option[Int] = None,
      terrainMaxZoom: Option[Int] = None,
      bbox: Option[String] = None,
      dryRun: Boolean = false,
      keepAll: Boolean = false,
      focus: Option[String] = None
  )

  final case class Focus(
      name: String,
      file: String,
      bytes: Long,
      bbox: String,
      minZoom: Int,
      maxZoom: Int,
      tiles: Long,
      fetchedAt: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  enum Layer(val wire: String, val title: String):
    case Vector extends Layer("vector", "coast, water and land cover")

    case Terrain extends Layer("terrain", "relief")

    case Labels extends Layer("labels", "physical names")

    /** The relief's own tiles have the sea flat at 0; these carry its depth (ETOPO1 from NOAA, or the GEBCO grid). */
    case Bathymetry extends Layer("bathymetry", "sea floor")

    def file: String = s"$wire.pmtiles"

  object Layer:
    def parse(s: String): Option[Layer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    def parseMany(s: String): Either[String, List[Layer]] =
      s.trim.toLowerCase(java.util.Locale.ROOT) match
        case "" | "all" => Right(values.toList)
        case other      =>
          parse(other).map(List(_)).toRight(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)")

  final case class LayerInfo(
      layer: String,
      present: Boolean,
      file: String,
      bytes: Long = 0,
      minZoom: Option[Int] = None,
      maxZoom: Option[Int] = None,
      bbox: Option[String] = None,
      tiles: Option[Long] = None,
      tileType: Option[String] = None,
      source: Option[String] = None,
      fetchedAt: Option[String] = None,
      /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
      version: Option[String] = None,
      attribution: String = "",
      kept: Option[String] = None,
      dropped: List[String] = Nil,
      error: Option[String] = None,
      onDemand: Option[OnDemand] = None,
      focus: List[Focus] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def deepest: Option[Int] =
      (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter(_.enabled).map(_.maxZoom).toList).maxOption



  final case class OnDemand(
      enabled: Boolean,
      maxZoom: Int,
      dir: String,
      tiles: Long,
      bytes: Long,
      maxBytes: Long,
      offlineUntil: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  final case class Region(name: String, bbox: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema



  final case class RemoveQuery(layer: String = "all", focus: Option[String] = None, cache: Boolean = false)

  final case class Removed(removed: List[String], bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema



  final case class Status(
      available: Boolean,
      dir: String,
      bbox: String,
      layers: List[LayerInfo],
      fetchCommand: String,
      freeBytes: Long = 0,
      running: Option[String] = None,
      hints: List[String] = Nil,
      styles: List[StyleState] = Nil,
      mapMaxZoom: Int = 16,
      regions: List[Region] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def missing(id: String): List[String] = styles.find(_.id == id).fold(Nil)(_.missing)



  final case class StyleState(id: String, name: String, missing: List[String] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
