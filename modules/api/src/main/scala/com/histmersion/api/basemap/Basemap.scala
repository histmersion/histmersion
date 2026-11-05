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
      layer: DropRule.Layer,
      whole: DropRule.Whole,
      drop: Set[DropRule.Drop] = Set.empty,
      spare: Set[DropRule.Spare] = Set.empty
  )

  object DropRule:
    type Layer = Layer.T
    object Layer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Whole = Whole.T
    object Whole extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Drop = Drop.T
    object Drop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Spare = Spare.T
    object Spare extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  final case class Read(geoms: Set[Read.Geoms] = Set.empty, kinds: Set[Read.Kinds] = Set.empty):
    def merge(o: Read): Read =
      Read(
        if geoms.isEmpty || o.geoms.isEmpty then Set.empty else geoms ++ o.geoms,
        if kinds.isEmpty || o.kinds.isEmpty then Set.empty else kinds ++ o.kinds
      )

  object Read:
    type Geoms = Geoms.T
    object Geoms extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kinds = Kinds.T
    object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      layer: FetchQuery.Layer = "all",
      maxZoom: Option[FetchQuery.MaxZoom] = None,
      terrainMaxZoom: Option[FetchQuery.TerrainMaxZoom] = None,
      bbox: Option[FetchQuery.Bbox] = None,
      dryRun: FetchQuery.DryRun = false,
      keepAll: FetchQuery.KeepAll = false,
      focus: Option[FetchQuery.Focus] = None
  )

  object FetchQuery:
    type Layer = Layer.T
    object Layer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TerrainMaxZoom = TerrainMaxZoom.T
    object TerrainMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = Bbox.T
    object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeepAll = KeepAll.T
    object KeepAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Focus = Focus.T
    object Focus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Focus(
      name: Focus.Name,
      file: Focus.File,
      bytes: com.histmersion.api.ByteCount,
      bbox: Focus.Bbox,
      minZoom: Focus.MinZoom,
      maxZoom: Focus.MaxZoom,
      tiles: Focus.Tiles,
      fetchedAt: Option[Focus.FetchedAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Focus:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = Bbox.T
    object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tiles = Tiles.T
    object Tiles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FetchedAt = FetchedAt.T
    object FetchedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



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
      layer: LayerInfo.Layer,
      present: LayerInfo.Present,
      file: LayerInfo.File,
      bytes: com.histmersion.api.ByteCount = 0,
      minZoom: Option[LayerInfo.MinZoom] = None,
      maxZoom: Option[LayerInfo.MaxZoom] = None,
      bbox: Option[LayerInfo.Bbox] = None,
      tiles: Option[LayerInfo.Tiles] = None,
      tileType: Option[LayerInfo.TileType] = None,
      source: Option[LayerInfo.Source] = None,
      fetchedAt: Option[LayerInfo.FetchedAt] = None,
      /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
      version: Option[LayerInfo.Version] = None,
      attribution: LayerInfo.Attribution = "",
      kept: Option[LayerInfo.Kept] = None,
      dropped: List[LayerInfo.Dropped] = Nil,
      error: Option[LayerInfo.Error] = None,
      onDemand: Option[OnDemand] = None,
      focus: List[Focus] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def deepest: Option[Int] =
      (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter(_.enabled).map(_.maxZoom).toList).maxOption

  object LayerInfo:
    type Layer = Layer.T
    object Layer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Present = Present.T
    object Present extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = Bbox.T
    object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tiles = Tiles.T
    object Tiles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TileType = TileType.T
    object TileType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FetchedAt = FetchedAt.T
    object FetchedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attribution = Attribution.T
    object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kept = Kept.T
    object Kept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dropped = Dropped.T
    object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class OnDemand(
      enabled: OnDemand.Enabled,
      maxZoom: OnDemand.MaxZoom,
      dir: OnDemand.Dir,
      tiles: OnDemand.Tiles,
      bytes: com.histmersion.api.ByteCount,
      maxBytes: OnDemand.MaxBytes,
      offlineUntil: Option[OnDemand.OfflineUntil] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object OnDemand:
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dir = Dir.T
    object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tiles = Tiles.T
    object Tiles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxBytes = MaxBytes.T
    object MaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfflineUntil = OfflineUntil.T
    object OfflineUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class Region(name: Region.Name, bbox: Region.Bbox) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Region:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = Bbox.T
    object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class RemoveQuery(layer: RemoveQuery.Layer = "all", focus: Option[RemoveQuery.Focus] = None, cache: RemoveQuery.Cache = false)

  object RemoveQuery:
    type Layer = Layer.T
    object Layer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Focus = Focus.T
    object Focus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cache = Cache.T
    object Cache extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Removed(removed: List[Removed.Value], bytes: com.histmersion.api.ByteCount) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Removed:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class Status(
      available: Status.Available,
      dir: Status.Dir,
      bbox: Status.Bbox,
      layers: List[LayerInfo],
      fetchCommand: Status.FetchCommand,
      freeBytes: Status.FreeBytes = 0,
      running: Option[Status.Running] = None,
      hints: List[Status.Hints] = Nil,
      styles: List[StyleState] = Nil,
      mapMaxZoom: Status.MapMaxZoom = 16,
      regions: List[Region] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def missing(id: String): List[String] = styles.find(_.id == id).fold(Nil)(_.missing)

  object Status:
    type Available = Available.T
    object Available extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dir = Dir.T
    object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = Bbox.T
    object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FetchCommand = FetchCommand.T
    object FetchCommand extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FreeBytes = FreeBytes.T
    object FreeBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Running = Running.T
    object Running extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hints = Hints.T
    object Hints extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapMaxZoom = MapMaxZoom.T
    object MapMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class StyleState(id: StyleState.Id, name: StyleState.Name, missing: List[StyleState.Missing] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object StyleState:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Missing = Missing.T
    object Missing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
