package com.histmersion
package api.basemap

import com.histmersion.api.Problem

import com.histmersion.api.BBox
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.ByteCount
object Basemap:
  object Tile:
    type Column = Column.T
    object Column extends Newtype[Long, Pure]
    type Row = Row.T
    object Row extends Newtype[Long, Pure]
  object Subset:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]
  type Zoom = Zoom.T
  object Zoom extends Newtype[Int, Pure]

  val FetchCommand = "histmersion basemap fetch"

  /** Deeper extracts run to tens of gigabytes. */
  val MaxZoomCap = 14

  /** Web Mercator's latitude limit. */
  val MaxLat = 85.0511287798066

  def validFocusName(s: Focus.Name): Either[Problem, Focus.Name] =
    val n = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
    Either.cond(
      n.matches("[a-z0-9][a-z0-9-]{0,40}"),
      Focus.Name(n),
      Problem(s"'$s' is not a focus region name (letters, digits and -)")
    )

  def focusRegion(spec: Focus.Spec): Either[Problem, (Focus.Name, BBox)] =
    (spec).value.trim.split("\\s+", 2).toList match
      case n :: b :: Nil =>
        for name <- validFocusName(Focus.Name(n)); box <- validBBox(Focus.Bbox(b.replace(" ", ""))) yield (name, box)
      case _ => Left(Problem(s"'$spec' is not \"name minLon,minLat,maxLon,maxLat\""))

  def focusNameOf(b: BBox): Focus.Name =
    def f(d: Double) = java.math
      .BigDecimal(d)
      .setScale(2, java.math.RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
      .replace("-", "m")
      .replace(".", "p")
    Focus.Name(s"bbox-${f((b.minLon).value)}-${f((b.minLat).value)}-${f((b.maxLon).value)}-${f((b.maxLat).value)}")

  def tileX(lon: Longitude, z: Zoom): Tile.Column =
    val n = 1L << (z).value
    Tile.Column(math.floor(((lon).value + 180.0) / 360.0 * n).toLong.max(0L).min(n - 1))

  def tileY(lat: Latitude, z: Zoom): Tile.Row =
    val n = 1L << (z).value
    val r = math.toRadians((lat).value.max(-MaxLat).min(MaxLat))
    val y = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n
    Tile.Row(math.floor(y).toLong.max(0L).min(n - 1))

  def tileRange(b: BBox, z: Zoom): ((Tile.Column, Tile.Column), (Tile.Row, Tile.Row)) =
    ((tileX(b.minLon, z), tileX(b.maxLon, z)), (tileY(b.maxLat, z), tileY(b.minLat, z)))

  def tileCount(b: BBox, minZoom: Zoom, maxZoom: Zoom): LayerInfo.Tiles =
    LayerInfo.Tiles(((minZoom).value to (maxZoom).value).map { z =>
      val ((x0, x1), (y0, y1)) = tileRange(b, Zoom(z))
      ((x1).value - (x0).value + 1) * ((y1).value - (y0).value + 1)
    }.sum)

  /** Web Mercator's ±85.05°: as far as web maps go. */
  val World: BBox = BBox(Longitude(-180.0), Latitude(-85.05), Longitude(180.0), Latitude(85.05))

  def validBBox(s: Focus.Bbox): Either[Problem, BBox] =
    if (s).value.trim.equalsIgnoreCase("world") then Right(World)
    else
      BBox.parse(s).toRight(Problem(s"'$s' is not minLon,minLat,maxLon,maxLat")).flatMap { b =>
        if (b.minLon).value >= (b.maxLon).value || (b.minLat).value >= (b.maxLat).value then
          Left(Problem(s"'$s': west must be < east and south < north"))
        else if (b.minLon).value < -180 || (b.maxLon).value > 180 || (b.minLat).value < -90 || (b.maxLat).value > 90
        then Left(Problem(s"'$s' is outside the world"))
        else Right(b)
      }

  final case class DropRule(
      layer: DropRule.Layer,
      whole: DropRule.Whole,
      drop: Set[DropRule.Drop] = Set.empty,
      spare: Set[DropRule.Spare] = Set.empty
  )

  object DropRule:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]
    export LayerInfo.Dropped as Layer
    type Whole = Whole.T
    object Whole extends Newtype[Boolean, Pure]
    type Drop = Drop.T
    object Drop extends Newtype[String, Pure]
    type Spare = Spare.T
    object Spare extends Newtype[String, Pure]

  def parseDrop(s: DropRule.Spec): Either[Problem, List[DropRule]] =
    val parts = (s).value.split(";").toList.map(_.trim).filter(_.nonEmpty).map(_.split("\\s+").toList)
    val rules = parts.map { words =>
      val kinds = words.tail
      val spare = kinds.filter(_.startsWith("+")).map(_.drop(1)).filter(_.nonEmpty).toSet
      val drop = kinds.filterNot(_.startsWith("+")).toSet
      DropRule(
        DropRule.Layer(words.head),
        DropRule.Whole(kinds.isEmpty),
        (drop).map(refactorValue7 => DropRule.Drop(refactorValue7)),
        (spare).map(refactorValue8 => DropRule.Spare(refactorValue8))
      )
    }
    val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
    val mixed = rules.filter(r => r.drop.nonEmpty && r.spare.nonEmpty).map(_.layer)
    if dup.nonEmpty then Left(Problem(s"basemap.vector.drop names ${dup.mkString(", ")} twice"))
    else if mixed.nonEmpty then
      Left(
        Problem(
          s"basemap.vector.drop: ${mixed.mkString(", ")} both drops kinds and spares kinds (+kind) — one or the other"
        )
      )
    else Right(rules)

  final case class Read(geoms: Set[Read.Geoms] = Set.empty, kinds: Set[Read.Kinds] = Set.empty):
    def merge(o: Read): Read =
      Read(
        if geoms.isEmpty || o.geoms.isEmpty then Set.empty else geoms ++ o.geoms,
        if kinds.isEmpty || o.kinds.isEmpty then Set.empty else kinds ++ o.kinds
      )

  object Read:
    type Geoms = Geoms.T
    object Geoms extends Newtype[Int, Pure]
    type Kinds = Kinds.T
    object Kinds extends Newtype[String, Pure]

  private val KindName = "[A-Za-z0-9_:.-]+".r

  def parseSubset(s: Subset.Spec): Either[Problem, Map[Layer.Wire, Read]] =
    val parts = (s).value.split(",").toList.map(_.trim).filter(_.nonEmpty)
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
            l -> Read(
              (codes.toSet).map(refactorValue3 => Read.Geoms(refactorValue3)),
              kinds.fold(Set.empty[Read.Kinds])(ks => ks.toSet.map(refactorValue4 => Read.Kinds(refactorValue4)))
            ),
            s"'$p': geometries are p, l and a"
          )
        case l :: Nil if l.nonEmpty =>
          Right(
            l -> Read(
              Set.empty,
              kinds.fold(Set.empty[Read.Kinds])(ks => ks.toSet.map(refactorValue11 => Read.Kinds(refactorValue11)))
            )
          )
        case _ => Left(s"'$p' is not layer[:geometries][~kind|kind]")
    }
    ((parsed
      .collectFirst { case Left(e) => e }
      .toLeft(
        parsed
          .collect { case Right(kv) => kv }
          .groupMapReduce(_._1)(_._2)(_.merge(_))
      ))
      .left
      .map(refactorError => Problem(refactorError)))
      .map(refactorValue6 =>
        (refactorValue6).iterator.map { case (refactorKey, refactorItem) =>
          (Layer.Wire(refactorKey), refactorItem)
        }.toMap
      )

  def subsetParam(m: Map[Layer.Wire, Read], fromZoom: Map[Layer.Wire, Zoom] = Map.empty): Subset.Spec =
    Subset.Spec(
      m.toList
        .sortBy(_._1)
        .map { (l, r) =>
          val g = if r.geoms.isEmpty then ""
          else
            ":" + r.geoms.toList.sorted
              .map(g => (g).value match { case 1 => 'p'; case 2 => 'l'; case _ => 'a' })
              .mkString
          val k = if r.kinds.isEmpty then "" else "~" + r.kinds.toList.sorted.mkString("|")
          (l).value + g + k + fromZoom.get(l).filter(z => (z).value > 0).fold("")(z => s"@$z")
        }
        .mkString(",")
    )

  def subsetAt(s: Subset.Spec, z: Zoom): Subset.Spec =
    Subset.Spec(
      (s).value
        .split(",")
        .toList
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap { e =>
          e.split("@", 2).toList match
            case l :: n :: Nil =>
              n.trim.toIntOption match
                case Some(from) => Option.when((z).value >= from)(l)
                case None       => Some(e)
            case _ => Some(e)
        }
        .mkString(",")
    )

  val LegacyLacks: List[LayerInfo.Dropped] = List(
    LayerInfo.Dropped("boundaries"),
    LayerInfo.Dropped("buildings"),
    LayerInfo.Dropped("landuse"),
    LayerInfo.Dropped("places"),
    LayerInfo.Dropped("pois"),
    LayerInfo.Dropped("roads"),
    LayerInfo.Dropped("transit")
  )

  def droppedLayers(keepAll: FetchQuery.KeepAll, drop: Option[DropRule.Spec]): List[LayerInfo.Dropped] =
    if (keepAll).value then Nil
    else
      drop match
        case Some(d) => parseDrop(d).toOption.getOrElse(Nil).filter(r => (r.whole).value).map(_.layer).distinct.sorted
        case None    => LegacyLacks

  def missingFor(needs: List[LayerInfo.Dropped], dropped: List[LayerInfo.Dropped]): List[StyleState.Missing] =
    needs.filter(dropped.contains).distinct.sorted

  val KeepAllCommand = s"$FetchCommand --layer vector --keep-all"

  def keptLine(keepAll: FetchQuery.KeepAll, drop: Option[DropRule.Spec]): LayerInfo.Kept =
    if (keepAll).value then LayerInfo.Kept("everything (unfiltered)")
    else
      drop match
        case Some(d) if (d).value.trim.isEmpty => LayerInfo.Kept("everything (nothing dropped)")
        case Some(d)                           => LayerInfo.Kept(s"all but: $d")
        case None => LayerInfo.Kept("earth, water and land cover only (fetched before the drop list)")

  final case class FetchQuery(
      layer: FetchQuery.Layer = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire("all"),
      maxZoom: Option[FetchQuery.MaxZoom] = None,
      terrainMaxZoom: Option[FetchQuery.TerrainMaxZoom] = None,
      bbox: Option[FetchQuery.Bbox] = None,
      dryRun: FetchQuery.DryRun = FetchQuery.DryRun(false),
      keepAll: FetchQuery.KeepAll = FetchQuery.KeepAll(false),
      focus: Option[FetchQuery.Focus] = None
  )

  object FetchQuery:
    export Basemap.Layer.Wire as Layer
    export Basemap.Focus.MaxZoom
    type TerrainMaxZoom = TerrainMaxZoom.T
    object TerrainMaxZoom extends Newtype[Int, Pure]
    export BBox.Text as Bbox
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    type KeepAll = KeepAll.T
    object KeepAll extends Newtype[Boolean, Pure]
    export Basemap.Focus.Name as Focus

  final case class Focus(
      name: Focus.Name,
      file: Focus.File,
      bytes: ByteCount,
      bbox: Focus.Bbox,
      minZoom: Focus.MinZoom,
      maxZoom: Focus.MaxZoom,
      tiles: Focus.Tiles,
      fetchedAt: Option[Focus.FetchedAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Focus:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export LayerInfo.File
    export BBox.Text as Bbox
    export LayerInfo.MinZoom
    type MaxZoom = MaxZoom.T
    object MaxZoom extends Newtype[Int, Pure]
    export LayerInfo.{FetchedAt, Tiles}

  enum Layer(val wire: Layer.Wire, val title: Layer.Title) derives CanEqual:
    case Vector extends Layer(Layer.Wire("vector"), Layer.Title("coast, water and land cover"))

    case Terrain extends Layer(Layer.Wire("terrain"), Layer.Title("relief"))

    case Labels extends Layer(Layer.Wire("labels"), Layer.Title("physical names"))

    /** The relief's own tiles have the sea flat at 0; these carry its depth (ETOPO1 from NOAA, or the GEBCO grid). */
    case Bathymetry extends Layer(Layer.Wire("bathymetry"), Layer.Title("sea floor"))

    def file: LayerInfo.File = LayerInfo.File(s"$wire.pmtiles")

  object Layer:
    type Wires = Wires.T
    object Wires extends Newtype[String, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]

    def parse(s: Wire): Option[Layer] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    def parseMany(s: Wires): Either[Problem, List[Layer]] =
      (s).value.trim.toLowerCase(java.util.Locale.ROOT) match
        case "" | "all" => Right(values.toList)
        case other      =>
          parse(Wire(other))
            .map(List(_))
            .toRight(Problem(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)"))

  final case class LayerInfo(
      layer: LayerInfo.Layer,
      present: LayerInfo.Present,
      file: LayerInfo.File,
      bytes: ByteCount = ByteCount((0).toLong),
      minZoom: Option[LayerInfo.MinZoom] = None,
      maxZoom: Option[LayerInfo.MaxZoom] = None,
      bbox: Option[LayerInfo.Bbox] = None,
      tiles: Option[LayerInfo.Tiles] = None,
      tileType: Option[LayerInfo.TileType] = None,
      source: Option[LayerInfo.Source] = None,
      fetchedAt: Option[LayerInfo.FetchedAt] = None,
      /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
      version: Option[LayerInfo.Version] = None,
      attribution: LayerInfo.Attribution = LayerInfo.Attribution(""),
      kept: Option[LayerInfo.Kept] = None,
      dropped: List[LayerInfo.Dropped] = Nil,
      error: Option[Problem] = None,
      onDemand: Option[OnDemand] = None,
      focus: List[Focus] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def deepest: Option[Focus.MaxZoom] =
      (maxZoom.toList ++ focus
        .map(_.maxZoom) ++ onDemand.filter(d => (d.enabled).value).map(_.maxZoom).toList).maxOption

  object LayerInfo:

    export Basemap.Layer.Wire as Layer
    type Present = Present.T
    object Present extends Newtype[Boolean, Pure]
    type File = File.T
    object File extends Newtype[String, Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends Newtype[Int, Pure]
    export Focus.MaxZoom
    export BBox.Text as Bbox
    type Tiles = Tiles.T
    object Tiles extends Newtype[Long, Pure]
    type TileType = TileType.T
    object TileType extends Newtype[String, Pure]
    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type FetchedAt = FetchedAt.T
    object FetchedAt extends Newtype[String, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    type Attribution = Attribution.T
    object Attribution extends Newtype[String, Pure]
    type Kept = Kept.T
    object Kept extends Newtype[String, Pure]
    type Dropped = Dropped.T
    object Dropped extends Newtype[String, Pure]

  final case class OnDemand(
      enabled: OnDemand.Enabled,
      maxZoom: OnDemand.MaxZoom,
      dir: OnDemand.Dir,
      tiles: OnDemand.Tiles,
      bytes: ByteCount,
      maxBytes: OnDemand.MaxBytes,
      offlineUntil: Option[OnDemand.OfflineUntil] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object OnDemand:
    type Enabled = Enabled.T
    object Enabled extends Newtype[Boolean, Pure]
    export Focus.MaxZoom
    type Dir = Dir.T
    object Dir extends Newtype[String, Pure]
    export LayerInfo.Tiles
    type MaxBytes = MaxBytes.T
    object MaxBytes extends Newtype[Long, Pure]
    type OfflineUntil = OfflineUntil.T
    object OfflineUntil extends Newtype[String, Pure]

  final case class Region(name: Region.Name, bbox: Region.Bbox)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Region:
    export Focus.Name
    export BBox.Text as Bbox

  final case class RemoveQuery(
      layer: RemoveQuery.Layer = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire("all"),
      focus: Option[RemoveQuery.Focus] = None,
      cache: RemoveQuery.Cache = RemoveQuery.Cache(false)
  )

  object RemoveQuery:
    export Basemap.Layer.Wire as Layer
    export Basemap.Focus.Name as Focus
    type Cache = Cache.T
    object Cache extends Newtype[Boolean, Pure]

  final case class Removed(removed: List[Removed.Value], bytes: ByteCount)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Removed:
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  final case class Status(
      available: Status.Available,
      dir: Status.Dir,
      bbox: Status.Bbox,
      layers: List[LayerInfo],
      fetchCommand: Status.FetchCommand,
      freeBytes: Status.FreeBytes = Status.FreeBytes((0).toLong),
      running: Option[Status.Running] = None,
      hints: List[Status.Hints] = Nil,
      styles: List[StyleState] = Nil,
      mapMaxZoom: Status.MapMaxZoom = Status.MapMaxZoom(16),
      regions: List[Region] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema,
        CanEqual:
    def missing(id: StyleState.Id): List[StyleState.Missing] = styles.find(_.id == id).fold(Nil)(_.missing)

  object Status:

    type Available = Available.T
    object Available extends Newtype[Boolean, Pure]
    export OnDemand.Dir
    export BBox.Text as Bbox
    type FetchCommand = FetchCommand.T
    object FetchCommand extends Newtype[String, Pure]
    type FreeBytes = FreeBytes.T
    object FreeBytes extends Newtype[Long, Pure]
    type Running = Running.T
    object Running extends Newtype[String, Pure]
    type Hints = Hints.T
    object Hints extends Newtype[String, Pure]
    type MapMaxZoom = MapMaxZoom.T
    object MapMaxZoom extends Newtype[Int, Pure]

  final case class StyleState(id: StyleState.Id, name: StyleState.Name, missing: List[StyleState.Missing] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object StyleState:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export LayerInfo.Dropped as Missing
