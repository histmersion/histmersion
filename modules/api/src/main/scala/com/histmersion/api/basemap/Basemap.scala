package com.histmersion.api.basemap

import com.histmersion.api.BBox
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Basemap:
  object Tile:
    type Column = Column.T
    object Column extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Row = Row.T
    object Row extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Subset:
    type Spec = Spec.T
    object Spec extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]



  val FetchCommand = "histmersion basemap fetch"

  /** Deeper extracts run to tens of gigabytes. */
  val MaxZoomCap = 14

  /** Web Mercator's latitude limit. */
  val MaxLat = 85.0511287798066

  def validFocusName(s: com.histmersion.api.basemap.Basemap.Focus.Name): Either[com.histmersion.api.basemap.Basemap.Problem, com.histmersion.api.basemap.Basemap.Focus.Name] =
    val n = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
    Either.cond(n.matches("[a-z0-9][a-z0-9-]{0,40}"), _root_.com.histmersion.api.basemap.Basemap.Focus.Name(n), _root_.com.histmersion.api.basemap.Basemap.Problem(s"'$s' is not a focus region name (letters, digits and -)"))

  def focusRegion(spec: com.histmersion.api.basemap.Basemap.Focus.Spec): Either[com.histmersion.api.basemap.Basemap.Problem, (com.histmersion.api.basemap.Basemap.Focus.Name, BBox)] =
    (spec).value.trim.split("\\s+", 2).toList match
      case n :: b :: Nil => for name <- validFocusName(_root_.com.histmersion.api.basemap.Basemap.Focus.Name(n)); box <- validBBox(_root_.com.histmersion.api.basemap.Basemap.Focus.Bbox(b.replace(" ", ""))) yield (name, box)
      case _             => Left(_root_.com.histmersion.api.basemap.Basemap.Problem(s"'$spec' is not \"name minLon,minLat,maxLon,maxLat\""))

  def focusNameOf(b: BBox): com.histmersion.api.basemap.Basemap.Focus.Name =
    def f(d: Double) = java.math
      .BigDecimal(d)
      .setScale(2, java.math.RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
      .replace("-", "m")
      .replace(".", "p")
    _root_.com.histmersion.api.basemap.Basemap.Focus.Name(s"bbox-${f((b.minLon).value)}-${f((b.minLat).value)}-${f((b.maxLon).value)}-${f((b.maxLat).value)}")

  def tileX(lon: com.histmersion.api.catalogue.Longitude, z: com.histmersion.api.basemap.Basemap.Zoom): com.histmersion.api.basemap.Basemap.Tile.Column =
    val n = 1L << (z).value
    _root_.com.histmersion.api.basemap.Basemap.Tile.Column(math.floor(((lon).value + 180.0) / 360.0 * n).toLong.max(0L).min(n - 1))

  def tileY(lat: com.histmersion.api.catalogue.Latitude, z: com.histmersion.api.basemap.Basemap.Zoom): com.histmersion.api.basemap.Basemap.Tile.Row =
    val n = 1L << (z).value
    val r = math.toRadians((lat).value.max(-MaxLat).min(MaxLat))
    val y = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n
    _root_.com.histmersion.api.basemap.Basemap.Tile.Row(math.floor(y).toLong.max(0L).min(n - 1))

  def tileRange(b: BBox, z: com.histmersion.api.basemap.Basemap.Zoom): ((com.histmersion.api.basemap.Basemap.Tile.Column, com.histmersion.api.basemap.Basemap.Tile.Column), (com.histmersion.api.basemap.Basemap.Tile.Row, com.histmersion.api.basemap.Basemap.Tile.Row)) =
    ((tileX(b.minLon, z), tileX(b.maxLon, z)), (tileY(b.maxLat, z), tileY(b.minLat, z)))

  def tileCount(b: BBox, minZoom: com.histmersion.api.basemap.Basemap.Zoom, maxZoom: com.histmersion.api.basemap.Basemap.Zoom): com.histmersion.api.basemap.Basemap.LayerInfo.Tiles =
    _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Tiles(((minZoom).value to (maxZoom).value).map { z =>
      val ((x0, x1), (y0, y1)) = tileRange(b, _root_.com.histmersion.api.basemap.Basemap.Zoom(z))
      ((x1).value - (x0).value + 1) * ((y1).value - (y0).value + 1)
    }.sum)

  /** Web Mercator's ±85.05°: as far as web maps go. */
  val World: BBox = BBox(_root_.com.histmersion.api.catalogue.Longitude(-180.0), _root_.com.histmersion.api.catalogue.Latitude(-85.05), _root_.com.histmersion.api.catalogue.Longitude(180.0), _root_.com.histmersion.api.catalogue.Latitude(85.05))

  def validBBox(s: com.histmersion.api.basemap.Basemap.Focus.Bbox): Either[com.histmersion.api.basemap.Basemap.Problem, BBox] =
    if (s).value.trim.equalsIgnoreCase("world") then Right(World)
    else
      BBox.parse(s).toRight(_root_.com.histmersion.api.basemap.Basemap.Problem(s"'$s' is not minLon,minLat,maxLon,maxLat")).flatMap { b =>
        if (b.minLon).value >= (b.maxLon).value || (b.minLat).value >= (b.maxLat).value then Left(_root_.com.histmersion.api.basemap.Basemap.Problem(s"'$s': west must be < east and south < north"))
        else if (b.minLon).value < -180 || (b.maxLon).value > 180 || (b.minLat).value < -90 || (b.maxLat).value > 90 then
          Left(_root_.com.histmersion.api.basemap.Basemap.Problem(s"'$s' is outside the world"))
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
    object Spec extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Layer = com.histmersion.api.basemap.Basemap.LayerInfo.Dropped
    val Layer: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped
    type Whole = Whole.T
    object Whole extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Drop = Drop.T
    object Drop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Spare = Spare.T
    object Spare extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parseDrop(s: com.histmersion.api.basemap.Basemap.DropRule.Spec): Either[com.histmersion.api.basemap.Basemap.Problem, List[DropRule]] =
    val parts = (s).value.split(";").toList.map(_.trim).filter(_.nonEmpty).map(_.split("\\s+").toList)
    val rules = parts.map { words =>
      val kinds = words.tail
      val spare = kinds.filter(_.startsWith("+")).map(_.drop(1)).filter(_.nonEmpty).toSet
      val drop = kinds.filterNot(_.startsWith("+")).toSet
      DropRule(_root_.com.histmersion.api.basemap.Basemap.DropRule.Layer(words.head), _root_.com.histmersion.api.basemap.Basemap.DropRule.Whole(kinds.isEmpty), (drop).map(refactorValue7 => _root_.com.histmersion.api.basemap.Basemap.DropRule.Drop(refactorValue7)), (spare).map(refactorValue8 => _root_.com.histmersion.api.basemap.Basemap.DropRule.Spare(refactorValue8)))
    }
    val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
    val mixed = rules.filter(r => r.drop.nonEmpty && r.spare.nonEmpty).map(_.layer)
    if dup.nonEmpty then Left(_root_.com.histmersion.api.basemap.Basemap.Problem(s"basemap.vector.drop names ${dup.mkString(", ")} twice"))
    else if mixed.nonEmpty then
      Left(_root_.com.histmersion.api.basemap.Basemap.Problem(s"basemap.vector.drop: ${mixed.mkString(", ")} both drops kinds and spares kinds (+kind) — one or the other"))
    else Right(rules)

  final case class Read(geoms: Set[Read.Geoms] = Set.empty, kinds: Set[Read.Kinds] = Set.empty):
    def merge(o: Read): Read =
      Read(
        if geoms.isEmpty || o.geoms.isEmpty then Set.empty else geoms ++ o.geoms,
        if kinds.isEmpty || o.kinds.isEmpty then Set.empty else kinds ++ o.kinds
      )

  object Read:
    type Geoms = Geoms.T
    object Geoms extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Int](_.value)
    type Kinds = Kinds.T
    object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)

  private val KindName = "[A-Za-z0-9_:.-]+".r

  def parseSubset(s: com.histmersion.api.basemap.Basemap.Subset.Spec): Either[com.histmersion.api.basemap.Basemap.Problem, Map[com.histmersion.api.basemap.Basemap.Layer.Wire, Read]] =
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
            l -> Read((codes.toSet).map(refactorValue3 => _root_.com.histmersion.api.basemap.Basemap.Read.Geoms(refactorValue3)), kinds.fold(Set.empty[_root_.com.histmersion.api.basemap.Basemap.Read.Kinds])(ks => ks.toSet.map(refactorValue4 => _root_.com.histmersion.api.basemap.Basemap.Read.Kinds(refactorValue4)))),
            s"'$p': geometries are p, l and a"
          )
        case l :: Nil if l.nonEmpty => Right(l -> Read(Set.empty, kinds.fold(Set.empty[_root_.com.histmersion.api.basemap.Basemap.Read.Kinds])(ks => ks.toSet.map(refactorValue11 => _root_.com.histmersion.api.basemap.Basemap.Read.Kinds(refactorValue11)))))
        case _                      => Left(s"'$p' is not layer[:geometries][~kind|kind]")
    }
    ((parsed
      .collectFirst { case Left(e) => e }
      .toLeft(
        parsed
          .collect { case Right(kv) => kv }
          .groupMapReduce(_._1)(_._2)(_.merge(_))
      )).left.map(refactorError => _root_.com.histmersion.api.basemap.Basemap.Problem(refactorError))).map(refactorValue6 => (refactorValue6).iterator.map { case (refactorKey, refactorItem) => (_root_.com.histmersion.api.basemap.Basemap.Layer.Wire(refactorKey), refactorItem) }.toMap)

  def subsetParam(m: Map[com.histmersion.api.basemap.Basemap.Layer.Wire, Read], fromZoom: Map[com.histmersion.api.basemap.Basemap.Layer.Wire, com.histmersion.api.basemap.Basemap.Zoom] = Map.empty): com.histmersion.api.basemap.Basemap.Subset.Spec =
    _root_.com.histmersion.api.basemap.Basemap.Subset.Spec(m.toList
      .sortBy(_._1)
      .map { (l, r) =>
        val g = if r.geoms.isEmpty then ""
        else ":" + r.geoms.toList.sorted.map(g => (g).value match { case 1 => 'p'; case 2 => 'l'; case _ => 'a' }).mkString
        val k = if r.kinds.isEmpty then "" else "~" + r.kinds.toList.sorted.mkString("|")
        (l).value + g + k + fromZoom.get(l).filter(z => (z).value > 0).fold("")(z => s"@$z")
      }
      .mkString(","))

  def subsetAt(s: com.histmersion.api.basemap.Basemap.Subset.Spec, z: com.histmersion.api.basemap.Basemap.Zoom): com.histmersion.api.basemap.Basemap.Subset.Spec =
    _root_.com.histmersion.api.basemap.Basemap.Subset.Spec((s).value.split(",")
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
      .mkString(","))

  val LegacyLacks: List[com.histmersion.api.basemap.Basemap.LayerInfo.Dropped] = List(_root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("boundaries"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("buildings"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("landuse"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("places"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("pois"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("transit"))

  def droppedLayers(keepAll: com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll, drop: Option[com.histmersion.api.basemap.Basemap.DropRule.Spec]): List[com.histmersion.api.basemap.Basemap.LayerInfo.Dropped] =
    if (keepAll).value then Nil
    else
      drop match
        case Some(d) => parseDrop(d).toOption.getOrElse(Nil).filter(r => (r.whole).value).map(_.layer).distinct.sorted
        case None    => LegacyLacks

  def missingFor(needs: List[com.histmersion.api.basemap.Basemap.LayerInfo.Dropped], dropped: List[com.histmersion.api.basemap.Basemap.LayerInfo.Dropped]): List[com.histmersion.api.basemap.Basemap.StyleState.Missing] =
    needs.filter(dropped.contains).distinct.sorted

  val KeepAllCommand = s"$FetchCommand --layer vector --keep-all"

  def keptLine(keepAll: com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll, drop: Option[com.histmersion.api.basemap.Basemap.DropRule.Spec]): com.histmersion.api.basemap.Basemap.LayerInfo.Kept =
    if (keepAll).value then _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Kept("everything (unfiltered)")
    else
      drop match
        case Some(d) if (d).value.trim.isEmpty => _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Kept("everything (nothing dropped)")
        case Some(d)                   => _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Kept(s"all but: $d")
        case None                      => _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Kept("earth, water and land cover only (fetched before the drop list)")

  final case class FetchQuery(
      layer: FetchQuery.Layer = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire("all"),
      maxZoom: Option[FetchQuery.MaxZoom] = None,
      terrainMaxZoom: Option[FetchQuery.TerrainMaxZoom] = None,
      bbox: Option[FetchQuery.Bbox] = None,
      dryRun: FetchQuery.DryRun = _root_.com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(false),
      keepAll: FetchQuery.KeepAll = _root_.com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll(false),
      focus: Option[FetchQuery.Focus] = None
  )

  object FetchQuery:
    type Layer = com.histmersion.api.basemap.Basemap.Layer.Wire
    val Layer: _root_.com.histmersion.api.basemap.Basemap.Layer.Wire.type = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire
    type MaxZoom = com.histmersion.api.basemap.Basemap.Focus.MaxZoom
    val MaxZoom: _root_.com.histmersion.api.basemap.Basemap.Focus.MaxZoom.type = _root_.com.histmersion.api.basemap.Basemap.Focus.MaxZoom
    type TerrainMaxZoom = TerrainMaxZoom.T
    object TerrainMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = com.histmersion.api.basemap.Basemap.Focus.Bbox
    val Bbox: _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeepAll = KeepAll.T
    object KeepAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Focus = com.histmersion.api.basemap.Basemap.Focus.Name
    val Focus: _root_.com.histmersion.api.basemap.Basemap.Focus.Name.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Name

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
    type Spec = Spec.T
    object Spec extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = com.histmersion.api.basemap.Basemap.LayerInfo.File
    val File: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.File.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.File
    type Bbox = com.histmersion.api.BBox.Text
    val Bbox: _root_.com.histmersion.api.BBox.Text.type = _root_.com.histmersion.api.BBox.Text
    type MinZoom = com.histmersion.api.basemap.Basemap.LayerInfo.MinZoom
    val MinZoom: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.MinZoom.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.MinZoom
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Int](_.value)
    type Tiles = com.histmersion.api.basemap.Basemap.LayerInfo.Tiles
    val Tiles: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Tiles.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Tiles
    type FetchedAt = com.histmersion.api.basemap.Basemap.LayerInfo.FetchedAt
    val FetchedAt: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.FetchedAt.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.FetchedAt



  enum Layer(val wire: Layer.Wire, val title: Layer.Title):
    case Vector extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("vector"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("coast, water and land cover"))

    case Terrain extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("terrain"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("relief"))

    case Labels extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("labels"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("physical names"))

    /** The relief's own tiles have the sea flat at 0; these carry its depth (ETOPO1 from NOAA, or the GEBCO grid). */
    case Bathymetry extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("bathymetry"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("sea floor"))

    def file: com.histmersion.api.basemap.Basemap.LayerInfo.File = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.File(s"$wire.pmtiles")

  object Layer:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wires = Wires.T
    object Wires extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.api.basemap.Basemap.Layer.Wire): Option[Layer] = values.find(_.wire == _root_.com.histmersion.api.basemap.Basemap.Layer.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    def parseMany(s: com.histmersion.api.basemap.Basemap.Layer.Wires): Either[com.histmersion.api.basemap.Basemap.Layer.Problem, List[Layer]] =
      (s).value.trim.toLowerCase(java.util.Locale.ROOT) match
        case "" | "all" => Right(values.toList)
        case other      =>
          parse(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire(other)).map(List(_)).toRight(_root_.com.histmersion.api.basemap.Basemap.Layer.Problem(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)"))

  final case class LayerInfo(
      layer: LayerInfo.Layer,
      present: LayerInfo.Present,
      file: LayerInfo.File,
      bytes: com.histmersion.api.ByteCount = _root_.com.histmersion.api.ByteCount((0).toLong),
      minZoom: Option[LayerInfo.MinZoom] = None,
      maxZoom: Option[LayerInfo.MaxZoom] = None,
      bbox: Option[LayerInfo.Bbox] = None,
      tiles: Option[LayerInfo.Tiles] = None,
      tileType: Option[LayerInfo.TileType] = None,
      source: Option[LayerInfo.Source] = None,
      fetchedAt: Option[LayerInfo.FetchedAt] = None,
      /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
      version: Option[LayerInfo.Version] = None,
      attribution: LayerInfo.Attribution = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Attribution(""),
      kept: Option[LayerInfo.Kept] = None,
      dropped: List[LayerInfo.Dropped] = Nil,
      error: Option[LayerInfo.Error] = None,
      onDemand: Option[OnDemand] = None,
      focus: List[Focus] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def deepest: Option[com.histmersion.api.basemap.Basemap.Focus.MaxZoom] =
      (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter(d => (d.enabled).value).map(_.maxZoom).toList).maxOption

  object LayerInfo:

    type Layer = com.histmersion.api.basemap.Basemap.Layer.Wire
    val Layer: _root_.com.histmersion.api.basemap.Basemap.Layer.Wire.type = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire
    type Present = Present.T
    object Present extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = com.histmersion.api.basemap.Basemap.Focus.MaxZoom
    val MaxZoom: _root_.com.histmersion.api.basemap.Basemap.Focus.MaxZoom.type = _root_.com.histmersion.api.basemap.Basemap.Focus.MaxZoom
    type Bbox = com.histmersion.api.basemap.Basemap.Focus.Bbox
    val Bbox: _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox
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
    object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
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
    type MaxZoom = com.histmersion.api.basemap.Basemap.Focus.MaxZoom
    val MaxZoom: _root_.com.histmersion.api.basemap.Basemap.Focus.MaxZoom.type = _root_.com.histmersion.api.basemap.Basemap.Focus.MaxZoom
    type Dir = Dir.T
    object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tiles = com.histmersion.api.basemap.Basemap.LayerInfo.Tiles
    val Tiles: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Tiles.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Tiles
    type MaxBytes = MaxBytes.T
    object MaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfflineUntil = OfflineUntil.T
    object OfflineUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class Region(name: Region.Name, bbox: Region.Bbox) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Region:
    type Name = com.histmersion.api.basemap.Basemap.Focus.Name
    val Name: _root_.com.histmersion.api.basemap.Basemap.Focus.Name.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Name
    type Bbox = com.histmersion.api.basemap.Basemap.Focus.Bbox
    val Bbox: _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox



  final case class RemoveQuery(layer: RemoveQuery.Layer = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire("all"), focus: Option[RemoveQuery.Focus] = None, cache: RemoveQuery.Cache = _root_.com.histmersion.api.basemap.Basemap.RemoveQuery.Cache(false))

  object RemoveQuery:
    type Layer = com.histmersion.api.basemap.Basemap.Layer.Wire
    val Layer: _root_.com.histmersion.api.basemap.Basemap.Layer.Wire.type = _root_.com.histmersion.api.basemap.Basemap.Layer.Wire
    type Focus = com.histmersion.api.basemap.Basemap.Focus.Name
    val Focus: _root_.com.histmersion.api.basemap.Basemap.Focus.Name.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Name
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
      freeBytes: Status.FreeBytes = _root_.com.histmersion.api.basemap.Basemap.Status.FreeBytes((0).toLong),
      running: Option[Status.Running] = None,
      hints: List[Status.Hints] = Nil,
      styles: List[StyleState] = Nil,
      mapMaxZoom: Status.MapMaxZoom = _root_.com.histmersion.api.basemap.Basemap.Status.MapMaxZoom(16),
      regions: List[Region] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def missing(id: com.histmersion.api.basemap.Basemap.StyleState.Id): List[com.histmersion.api.basemap.Basemap.StyleState.Missing] = styles.find(_.id == id).fold(Nil)(_.missing)

  object Status:

    type Available = Available.T
    object Available extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dir = com.histmersion.api.basemap.Basemap.OnDemand.Dir
    val Dir: _root_.com.histmersion.api.basemap.Basemap.OnDemand.Dir.type = _root_.com.histmersion.api.basemap.Basemap.OnDemand.Dir
    type Bbox = com.histmersion.api.basemap.Basemap.Focus.Bbox
    val Bbox: _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox.type = _root_.com.histmersion.api.basemap.Basemap.Focus.Bbox
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
    type Missing = com.histmersion.api.basemap.Basemap.LayerInfo.Dropped
    val Missing: _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped.type = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped
