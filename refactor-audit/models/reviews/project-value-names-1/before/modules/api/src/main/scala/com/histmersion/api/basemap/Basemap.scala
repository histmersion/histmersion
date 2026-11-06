package com.histmersion.api.basemap

import com.histmersion.api.BBox
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Basemap:

  type FocusNameOfFD = FocusNameOfFD.T
  object FocusNameOfFD extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ValidFocusNameResultPart1 = ValidFocusNameResultPart1.T
  object ValidFocusNameResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidFocusNameResultPart2 = ValidFocusNameResultPart2.T
  object ValidFocusNameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidFocusNameS = ValidFocusNameS.T
  object ValidFocusNameS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusRegionResultPart1 = FocusRegionResultPart1.T
  object FocusRegionResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusRegionResultPart2Part1 = FocusRegionResultPart2Part1.T
  object FocusRegionResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusRegionSpec = FocusRegionSpec.T
  object FocusRegionSpec extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusNameOfResult = FocusNameOfResult.T
  object FocusNameOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileXResult = TileXResult.T
  object TileXResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileXLon = TileXLon.T
  object TileXLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileXZ = TileXZ.T
  object TileXZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileYResult = TileYResult.T
  object TileYResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileYLat = TileYLat.T
  object TileYLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileYZ = _root_.com.histmersion.api.basemap.Basemap.TileXZ
  val TileYZ: _root_.com.histmersion.api.basemap.Basemap.TileXZ.type = _root_.com.histmersion.api.basemap.Basemap.TileXZ
  type TileRangeResultPart1Part1 = TileRangeResultPart1Part1.T
  object TileRangeResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileRangeResultPart1Part2 = TileRangeResultPart1Part2.T
  object TileRangeResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileRangeResultPart2Part1 = TileRangeResultPart2Part1.T
  object TileRangeResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileRangeResultPart2Part2 = TileRangeResultPart2Part2.T
  object TileRangeResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileRangeZ = _root_.com.histmersion.api.basemap.Basemap.TileXZ
  val TileRangeZ: _root_.com.histmersion.api.basemap.Basemap.TileXZ.type = _root_.com.histmersion.api.basemap.Basemap.TileXZ
  type TileCountResult = TileCountResult.T
  object TileCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCountMinZoom = TileCountMinZoom.T
  object TileCountMinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileCountMaxZoom = TileCountMaxZoom.T
  object TileCountMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidBBoxResultPart1 = ValidBBoxResultPart1.T
  object ValidBBoxResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidBBoxS = ValidBBoxS.T
  object ValidBBoxS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseDropResultPart1 = ParseDropResultPart1.T
  object ParseDropResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseDropS = ParseDropS.T
  object ParseDropS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSubsetResultPart1 = ParseSubsetResultPart1.T
  object ParseSubsetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSubsetResultPart2Part1 = ParseSubsetResultPart2Part1.T
  object ParseSubsetResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSubsetS = ParseSubsetS.T
  object ParseSubsetS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetParamResult = SubsetParamResult.T
  object SubsetParamResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetParamMPart1 = SubsetParamMPart1.T
  object SubsetParamMPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type SubsetParamFromZoomPart1 = SubsetParamFromZoomPart1.T
  object SubsetParamFromZoomPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetParamFromZoomPart2 = SubsetParamFromZoomPart2.T
  object SubsetParamFromZoomPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetAtResult = SubsetAtResult.T
  object SubsetAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetAtS = SubsetAtS.T
  object SubsetAtS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetAtZ = SubsetAtZ.T
  object SubsetAtZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyLacks = LegacyLacks.T
  object LegacyLacks extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DroppedLayersResult = DroppedLayersResult.T
  object DroppedLayersResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DroppedLayersKeepAll = DroppedLayersKeepAll.T
  object DroppedLayersKeepAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DroppedLayersDrop = DroppedLayersDrop.T
  object DroppedLayersDrop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingForResult = MissingForResult.T
  object MissingForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingForNeeds = MissingForNeeds.T
  object MissingForNeeds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type MissingForDropped = MissingForDropped.T
  object MissingForDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptLineResult = KeptLineResult.T
  object KeptLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptLineKeepAll = KeptLineKeepAll.T
  object KeptLineKeepAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptLineDrop = KeptLineDrop.T
  object KeptLineDrop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val FetchCommand = "histmersion basemap fetch"

  /** Deeper extracts run to tens of gigabytes. */
  val MaxZoomCap = 14

  /** Web Mercator's latitude limit. */
  val MaxLat = 85.0511287798066

  def validFocusName(s: com.histmersion.api.basemap.Basemap.ValidFocusNameS): Either[com.histmersion.api.basemap.Basemap.ValidFocusNameResultPart1, com.histmersion.api.basemap.Basemap.ValidFocusNameResultPart2] =
    val n = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
    Either.cond(n.matches("[a-z0-9][a-z0-9-]{0,40}"), _root_.com.histmersion.api.basemap.Basemap.ValidFocusNameResultPart2(n), _root_.com.histmersion.api.basemap.Basemap.ValidFocusNameResultPart1(s"'$s' is not a focus region name (letters, digits and -)"))

  def focusRegion(spec: com.histmersion.api.basemap.Basemap.FocusRegionSpec): Either[com.histmersion.api.basemap.Basemap.FocusRegionResultPart1, (com.histmersion.api.basemap.Basemap.FocusRegionResultPart2Part1, BBox)] =
    (spec).value.trim.split("\\s+", 2).toList match
      case n :: b :: Nil => for name <- validFocusName(_root_.com.histmersion.api.basemap.Basemap.ValidFocusNameS(n)); box <- validBBox(_root_.com.histmersion.api.basemap.Basemap.ValidBBoxS(b.replace(" ", ""))) yield (name, box)
      case _             => Left(_root_.com.histmersion.api.basemap.Basemap.FocusRegionResultPart1(s"'$spec' is not \"name minLon,minLat,maxLon,maxLat\""))

  def focusNameOf(b: BBox): com.histmersion.api.basemap.Basemap.FocusNameOfResult =
    def f(d: com.histmersion.api.basemap.Basemap.FocusNameOfFD) = java.math
      .BigDecimal(d)
      .setScale(2, java.math.RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
      .replace("-", "m")
      .replace(".", "p")
    s"bbox-${f(b.minLon)}-${f(b.minLat)}-${f(b.maxLon)}-${f(b.maxLat)}"

  def tileX(lon: com.histmersion.api.basemap.Basemap.TileXLon, z: com.histmersion.api.basemap.Basemap.TileXZ): com.histmersion.api.basemap.Basemap.TileXResult =
    val n = 1L << (z).value
    _root_.com.histmersion.api.basemap.Basemap.TileXResult(math.floor(((lon).value + 180.0) / 360.0 * n).toLong.max(0L).min(n - 1))

  def tileY(lat: com.histmersion.api.basemap.Basemap.TileYLat, z: com.histmersion.api.basemap.Basemap.TileYZ): com.histmersion.api.basemap.Basemap.TileYResult =
    val n = 1L << (z).value
    val r = math.toRadians((lat).value.max(-MaxLat).min(MaxLat))
    val y = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n
    _root_.com.histmersion.api.basemap.Basemap.TileYResult(math.floor(y).toLong.max(0L).min(n - 1))

  def tileRange(b: BBox, z: com.histmersion.api.basemap.Basemap.TileRangeZ): ((com.histmersion.api.basemap.Basemap.TileRangeResultPart1Part1, com.histmersion.api.basemap.Basemap.TileRangeResultPart1Part2), (com.histmersion.api.basemap.Basemap.TileRangeResultPart2Part1, com.histmersion.api.basemap.Basemap.TileRangeResultPart2Part2)) =
    ((tileX(b.minLon, z), tileX(b.maxLon, z)), (tileY(b.maxLat, z), tileY(b.minLat, z)))

  def tileCount(b: BBox, minZoom: com.histmersion.api.basemap.Basemap.TileCountMinZoom, maxZoom: com.histmersion.api.basemap.Basemap.TileCountMaxZoom): com.histmersion.api.basemap.Basemap.TileCountResult =
    (minZoom to maxZoom).map { z =>
      val ((x0, x1), (y0, y1)) = tileRange(b, z)
      (x1 - x0 + 1) * (y1 - y0 + 1)
    }.sum

  /** Web Mercator's ±85.05°: as far as web maps go. */
  val World: BBox = BBox(-180, -85.05, 180, 85.05)

  def validBBox(s: com.histmersion.api.basemap.Basemap.ValidBBoxS): Either[com.histmersion.api.basemap.Basemap.ValidBBoxResultPart1, BBox] =
    if (s).value.trim.equalsIgnoreCase("world") then Right(World)
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

  def parseDrop(s: com.histmersion.api.basemap.Basemap.ParseDropS): Either[com.histmersion.api.basemap.Basemap.ParseDropResultPart1, List[DropRule]] =
    val parts = (s).value.split(";").toList.map(_.trim).filter(_.nonEmpty).map(_.split("\\s+").toList)
    val rules = parts.map { words =>
      val kinds = words.tail
      val spare = kinds.filter(_.startsWith("+")).map(_.drop(1)).filter(_.nonEmpty).toSet
      val drop = kinds.filterNot(_.startsWith("+")).toSet
      DropRule(_root_.com.histmersion.api.basemap.Basemap.DropRule.Layer(words.head), _root_.com.histmersion.api.basemap.Basemap.DropRule.Whole(kinds.isEmpty), (drop).map(refactorValue7 => _root_.com.histmersion.api.basemap.Basemap.DropRule.Drop(refactorValue7)), (spare).map(refactorValue8 => _root_.com.histmersion.api.basemap.Basemap.DropRule.Spare(refactorValue8)))
    }
    val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
    val mixed = rules.filter(r => r.drop.nonEmpty && r.spare.nonEmpty).map(_.layer)
    if dup.nonEmpty then Left(_root_.com.histmersion.api.basemap.Basemap.ParseDropResultPart1(s"basemap.vector.drop names ${dup.mkString(", ")} twice"))
    else if mixed.nonEmpty then
      Left(_root_.com.histmersion.api.basemap.Basemap.ParseDropResultPart1(s"basemap.vector.drop: ${mixed.mkString(", ")} both drops kinds and spares kinds (+kind) — one or the other"))
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

  def parseSubset(s: com.histmersion.api.basemap.Basemap.ParseSubsetS): Either[com.histmersion.api.basemap.Basemap.ParseSubsetResultPart1, Map[com.histmersion.api.basemap.Basemap.ParseSubsetResultPart2Part1, Read]] =
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
            l -> Read((codes.toSet).map(refactorValue3 => _root_.com.histmersion.api.basemap.Basemap.Read.Geoms(refactorValue3)), kinds.fold(Set.empty)((_.toSet).map(refactorValue4 => _root_.com.histmersion.api.basemap.Basemap.Read.Kinds(refactorValue4)))),
            s"'$p': geometries are p, l and a"
          )
        case l :: Nil if l.nonEmpty => Right(l -> Read(Set.empty, kinds.fold(Set.empty)((_.toSet).map(refactorValue11 => _root_.com.histmersion.api.basemap.Basemap.Read.Kinds(refactorValue11)))))
        case _                      => Left(s"'$p' is not layer[:geometries][~kind|kind]")
    }
    ((parsed
      .collectFirst { case Left(e) => e }
      .toLeft(
        parsed
          .collect { case Right(kv) => kv }
          .groupMapReduce(_._1)(_._2)(_.merge(_))
      )).left.map(refactorError => _root_.com.histmersion.api.basemap.Basemap.ParseSubsetResultPart1(refactorError))).map(refactorValue6 => (refactorValue6).iterator.map { case (refactorKey, refactorItem) => (_root_.com.histmersion.api.basemap.Basemap.ParseSubsetResultPart2Part1(refactorKey), refactorItem) }.toMap)

  def subsetParam(m: Map[com.histmersion.api.basemap.Basemap.SubsetParamMPart1, Read], fromZoom: Map[com.histmersion.api.basemap.Basemap.SubsetParamFromZoomPart1, com.histmersion.api.basemap.Basemap.SubsetParamFromZoomPart2] = Map.empty): com.histmersion.api.basemap.Basemap.SubsetParamResult =
    m.toList
      .sortBy(_._1)
      .map { (l, r) =>
        val g = if r.geoms.isEmpty then ""
        else ":" + r.geoms.toList.sorted.map { case 1 => 'p'; case 2 => 'l'; case _ => 'a' }.mkString
        val k = if r.kinds.isEmpty then "" else "~" + r.kinds.toList.sorted.mkString("|")
        (l).value + g + k + fromZoom.get(_root_.com.histmersion.api.basemap.Basemap.SubsetParamFromZoomPart1((l).value)).filter((_).value > 0).fold("")(z => s"@$z")
      }
      .mkString(",")

  def subsetAt(s: com.histmersion.api.basemap.Basemap.SubsetAtS, z: com.histmersion.api.basemap.Basemap.SubsetAtZ): com.histmersion.api.basemap.Basemap.SubsetAtResult =
    (s).value.split(",")
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

  val LegacyLacks: List[com.histmersion.api.basemap.Basemap.LegacyLacks] = List(_root_.com.histmersion.api.basemap.Basemap.LegacyLacks("boundaries"), _root_.com.histmersion.api.basemap.Basemap.LegacyLacks("buildings"), _root_.com.histmersion.api.basemap.Basemap.LegacyLacks("landuse"), _root_.com.histmersion.api.basemap.Basemap.LegacyLacks("places"), _root_.com.histmersion.api.basemap.Basemap.LegacyLacks("pois"), _root_.com.histmersion.api.basemap.Basemap.LegacyLacks("roads"), _root_.com.histmersion.api.basemap.Basemap.LegacyLacks("transit"))

  def droppedLayers(keepAll: com.histmersion.api.basemap.Basemap.DroppedLayersKeepAll, drop: Option[com.histmersion.api.basemap.Basemap.DroppedLayersDrop]): List[com.histmersion.api.basemap.Basemap.DroppedLayersResult] =
    if (keepAll).value then Nil
    else
      drop match
        case Some(d) => parseDrop(_root_.com.histmersion.api.basemap.Basemap.ParseDropS((d).value)).toOption.getOrElse(Nil).filter((refactorCallback2 => ((refactorCallback2.whole) == _root_.com.histmersion.api.basemap.Basemap.DropRule.Whole(true)))).map(_.layer).distinct.sorted
        case None    => LegacyLacks

  def missingFor(needs: List[com.histmersion.api.basemap.Basemap.MissingForNeeds], dropped: List[com.histmersion.api.basemap.Basemap.MissingForDropped]): List[com.histmersion.api.basemap.Basemap.MissingForResult] =
    (needs.filter(dropped.contains).distinct.sorted).map(refactorValue9 => _root_.com.histmersion.api.basemap.Basemap.MissingForResult((refactorValue9).value))

  val KeepAllCommand = s"$FetchCommand --layer vector --keep-all"

  def keptLine(keepAll: com.histmersion.api.basemap.Basemap.KeptLineKeepAll, drop: Option[com.histmersion.api.basemap.Basemap.KeptLineDrop]): com.histmersion.api.basemap.Basemap.KeptLineResult =
    if (keepAll).value then _root_.com.histmersion.api.basemap.Basemap.KeptLineResult("everything (unfiltered)")
    else
      drop match
        case Some(d) if (d).value.trim.isEmpty => _root_.com.histmersion.api.basemap.Basemap.KeptLineResult("everything (nothing dropped)")
        case Some(d)                   => _root_.com.histmersion.api.basemap.Basemap.KeptLineResult(s"all but: $d")
        case None                      => _root_.com.histmersion.api.basemap.Basemap.KeptLineResult("earth, water and land cover only (fetched before the drop list)")

  final case class FetchQuery(
      layer: FetchQuery.Layer = _root_.com.histmersion.api.basemap.Basemap.FetchQuery.Layer("all"),
      maxZoom: Option[FetchQuery.MaxZoom] = None,
      terrainMaxZoom: Option[FetchQuery.TerrainMaxZoom] = None,
      bbox: Option[FetchQuery.Bbox] = None,
      dryRun: FetchQuery.DryRun = _root_.com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(false),
      keepAll: FetchQuery.KeepAll = _root_.com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll(false),
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



  enum Layer(val wire: Layer.Wire, val title: Layer.Title):
    case Vector extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("vector"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("coast, water and land cover"))

    case Terrain extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("terrain"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("relief"))

    case Labels extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("labels"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("physical names"))

    /** The relief's own tiles have the sea flat at 0; these carry its depth (ETOPO1 from NOAA, or the GEBCO grid). */
    case Bathymetry extends Layer(_root_.com.histmersion.api.basemap.Basemap.Layer.Wire("bathymetry"), _root_.com.histmersion.api.basemap.Basemap.Layer.Title("sea floor"))

    def file: com.histmersion.api.basemap.Basemap.Layer.FileResult = _root_.com.histmersion.api.basemap.Basemap.Layer.FileResult(s"$wire.pmtiles")

  object Layer:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type FileResult = FileResult.T
    object FileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseManyResultPart1 = ParseManyResultPart1.T
    object ParseManyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseManyS = ParseManyS.T
    object ParseManyS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.basemap.Basemap.Layer.ParseS): Option[Layer] = values.find(_.wire == _root_.com.histmersion.api.basemap.Basemap.Layer.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    def parseMany(s: com.histmersion.api.basemap.Basemap.Layer.ParseManyS): Either[com.histmersion.api.basemap.Basemap.Layer.ParseManyResultPart1, List[Layer]] =
      (s).value.trim.toLowerCase(java.util.Locale.ROOT) match
        case "" | "all" => Right(values.toList)
        case other      =>
          parse(_root_.com.histmersion.api.basemap.Basemap.Layer.ParseS(other)).map(List(_)).toRight(_root_.com.histmersion.api.basemap.Basemap.Layer.ParseManyResultPart1(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)"))

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
      attribution: LayerInfo.Attribution = _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Attribution(""),
      kept: Option[LayerInfo.Kept] = None,
      dropped: List[LayerInfo.Dropped] = Nil,
      error: Option[LayerInfo.Error] = None,
      onDemand: Option[OnDemand] = None,
      focus: List[Focus] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def deepest: Option[com.histmersion.api.basemap.Basemap.LayerInfo.DeepestResult] =
      (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter((refactorCallback => ((refactorCallback.enabled) == _root_.com.histmersion.api.basemap.Basemap.OnDemand.Enabled(true)))).map(_.maxZoom).toList).maxOption

  object LayerInfo:
    type DeepestResult = DeepestResult.T
    object DeepestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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



  final case class RemoveQuery(layer: RemoveQuery.Layer = _root_.com.histmersion.api.basemap.Basemap.RemoveQuery.Layer("all"), focus: Option[RemoveQuery.Focus] = None, cache: RemoveQuery.Cache = _root_.com.histmersion.api.basemap.Basemap.RemoveQuery.Cache(false))

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
      freeBytes: Status.FreeBytes = _root_.com.histmersion.api.basemap.Basemap.Status.FreeBytes((0).toLong),
      running: Option[Status.Running] = None,
      hints: List[Status.Hints] = Nil,
      styles: List[StyleState] = Nil,
      mapMaxZoom: Status.MapMaxZoom = _root_.com.histmersion.api.basemap.Basemap.Status.MapMaxZoom(16),
      regions: List[Region] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def missing(id: com.histmersion.api.basemap.Basemap.Status.MissingId): List[com.histmersion.api.basemap.Basemap.Status.MissingResult] = styles.find(_.id == id).fold(Nil)(_.missing)

  object Status:
    type MissingResult = MissingResult.T
    object MissingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MissingId = MissingId.T
    object MissingId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
