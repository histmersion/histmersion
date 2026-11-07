package com.histmersion.util.basemap

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.storage.Store

import io.circe.Json

import scala.collection.mutable
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.library.Label

/** The base maps' physical names (docs/research-terrain-basemap.md, "Labels"): Natural Earth's label layers — public
  * domain — read from their GeoJSON, cut to the region, and written as a small vector archive the page labels from
  * (`labels.pmtiles`, layers `physical` and `rivers`).
  *
  * Kept: mountain ranges, deserts, plateaus, plains, basins, valleys, deltas, peninsulas, isthmuses, coasts, wetlands,
  * regions (Natural Earth's "geoarea"), island groups and the larger islands, lakes (not reservoirs), peaks, passes,
  * depressions, capes, waterfalls, and the major rivers as lines (not canals). Never a settlement, a country or a road:
  * those files are not read. Seas, gulfs, straits and the smaller islands, lakes and rivers come with the vector layer
  * (Protomaps). Pure.
  */
object BasemapLabels:
  type Area = Area.T
  object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Buffer = Buffer.T
  object Buffer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeatureClass = FeatureClass.T
  object FeatureClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileName = FileName.T
  object FileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelCount = LabelCount.T
  object LabelCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Language = Language.T
  object Language extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Margin = Margin.T
  object Margin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumericProp = NumericProp.T
  object NumericProp extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointCount = PointCount.T
  object PointCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PropKey = PropKey.T
  object PropKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileBytes = TileBytes.T
  object TileBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileKey = TileKey.T
  object TileKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileX = TileX.T
  object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileY = TileY.T
  object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tolerance = Tolerance.T
  object Tolerance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One name to place: a point (`line` empty) or a river's line (`line` its parts, lon/lat), shown from map zoom
    * `minZoom` (to `maxZoom`), with its names by language (`name`, `name:en`, …).
    */
  type WithinInLon = WithinInLon.T
  object WithinInLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithinInLat = WithinInLat.T
  object WithinInLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipTouchesAPart1 = ClipTouchesAPart1.T
  object ClipTouchesAPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipTouchesAPart2 = ClipTouchesAPart2.T
  object ClipTouchesAPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipTouchesBPart1 = ClipTouchesBPart1.T
  object ClipTouchesBPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipTouchesBPart2 = ClipTouchesBPart2.T
  object ClipTouchesBPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type TextK = _root_.com.histmersion.util.basemap.BasemapLabels.PropKey
  val TextK: _root_.com.histmersion.util.basemap.BasemapLabels.PropKey.type = _root_.com.histmersion.util.basemap.BasemapLabels.PropKey
  type NumberK = _root_.com.histmersion.util.basemap.BasemapLabels.PropKey
  val NumberK: _root_.com.histmersion.util.basemap.BasemapLabels.PropKey.type = _root_.com.histmersion.util.basemap.BasemapLabels.PropKey
  type CoordsResultPart1 = CoordsResultPart1.T
  object CoordsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoordsResultPart2 = CoordsResultPart2.T
  object CoordsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RingResultPart1 = RingResultPart1.T
  object RingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RingResultPart2 = RingResultPart2.T
  object RingResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AreaRPart1 = AreaRPart1.T
  object AreaRPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AreaRPart2 = AreaRPart2.T
  object AreaRPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsideRPart1 = InsideRPart1.T
  object InsideRPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsideRPart2 = InsideRPart2.T
  object InsideRPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsidePPart1 = InsidePPart1.T
  object InsidePPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsidePPart2 = InsidePPart2.T
  object InsidePPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelPointResultPart1 = LabelPointResultPart1.T
  object LabelPointResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelPointResultPart2 = LabelPointResultPart2.T
  object LabelPointResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelPointPolygonsPart1 = LabelPointPolygonsPart1.T
  object LabelPointPolygonsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelPointPolygonsPart2 = LabelPointPolygonsPart2.T
  object LabelPointPolygonsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldResultPart1 = WorldResultPart1.T
  object WorldResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldResultPart2 = WorldResultPart2.T
  object WorldResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldLon = WorldLon.T
  object WorldLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldLat = WorldLat.T
  object WorldLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimplifyResultPart1 = SimplifyResultPart1.T
  object SimplifyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimplifyResultPart2 = SimplifyResultPart2.T
  object SimplifyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimplifyLPart1 = SimplifyLPart1.T
  object SimplifyLPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimplifyLPart2 = SimplifyLPart2.T
  object SimplifyLPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipLPart1 = ClipLPart1.T
  object ClipLPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipLPart2 = ClipLPart2.T
  object ClipLPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipX = _root_.com.histmersion.util.basemap.BasemapLabels.TileX
  val ClipX: _root_.com.histmersion.util.basemap.BasemapLabels.TileX.type = _root_.com.histmersion.util.basemap.BasemapLabels.TileX
  type ClipY = _root_.com.histmersion.util.basemap.BasemapLabels.TileY
  val ClipY: _root_.com.histmersion.util.basemap.BasemapLabels.TileY.type = _root_.com.histmersion.util.basemap.BasemapLabels.TileY
  type RelRPart1 = RelRPart1.T
  object RelRPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelRPart2 = RelRPart2.T
  object RelRPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesMaxZoom = _root_.com.histmersion.util.basemap.BasemapLabels.Label.MaxZoom
  val TilesMaxZoom: _root_.com.histmersion.util.basemap.BasemapLabels.Label.MaxZoom.type = _root_.com.histmersion.util.basemap.BasemapLabels.Label.MaxZoom
  type WriteMaxZoom = _root_.com.histmersion.util.basemap.PmTiles.Zoom
  val WriteMaxZoom: _root_.com.histmersion.util.basemap.PmTiles.Zoom.type = _root_.com.histmersion.util.basemap.PmTiles.Zoom

  final case class Label(
      kind: Label.Kind,
      names: List[(Label.NamesPart1, Label.NamesPart2)],
      lon: Label.Lon,
      lat: Label.Lat,
      minZoom: Label.MinZoom,
      maxZoom: Option[Label.MaxZoom] = None,
      line: Vector[Vector[(Label.LinePart1, Label.LinePart2)]] = Vector.empty,
      elevation: Option[Label.Elevation] = None
  )

  object Label:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesPart1 = com.histmersion.util.basemap.BasemapLabels.Language
    val NamesPart1: _root_.com.histmersion.util.basemap.BasemapLabels.Language.type = _root_.com.histmersion.util.basemap.BasemapLabels.Language
    type NamesPart2 = com.histmersion.util.basemap.BasemapLabels.Name
    val NamesPart2: _root_.com.histmersion.util.basemap.BasemapLabels.Name.type = _root_.com.histmersion.util.basemap.BasemapLabels.Name
    type Lon = Lon.T
    object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lat = Lat.T
    object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinePart1 = LinePart1.T
    object LinePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinePart2 = LinePart2.T
    object LinePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Elevation = Elevation.T
    object Elevation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Natural Earth's feature classes, lower-cased → the `kind` a style selects; `None` = not kept. */
  def kindOf(featureClass: com.histmersion.util.basemap.BasemapLabels.FeatureClass): Option[com.histmersion.util.basemap.BasemapLabels.Label.Kind] = featureClass.trim.toLowerCase(java.util.Locale.ROOT) match
    case "range/mtn"                   => Some("range")
    case "foothills"                   => Some("foothills")
    case "desert"                      => Some("desert")
    case "plateau"                     => Some("plateau")
    case "plain" | "lowland"           => Some("plain")
    case "basin"                       => Some("basin")
    case "depression"                  => Some("depression")
    case "valley" | "gorge"            => Some("valley")
    case "delta"                       => Some("delta")
    case "peninsula" | "pen/cape"      => Some("peninsula")
    case "isthmus"                     => Some("isthmus")
    case "coast"                       => Some("coast")
    case "wetlands"                    => Some("wetland")
    case "tundra" | "geoarea"          => Some("region")
    case "continent"                   => Some("continent")
    case "island group"                => Some("island_group")
    case "island"                      => Some("island")
    case "lake" | "alkaline lake"      => Some("lake")
    case "mountain" | "spot elevation" => Some("peak")
    case "pass"                        => Some("pass")
    case "cape"                        => Some("cape")
    case "waterfall"                   => Some("waterfall")
    case "river"                       => Some("river")
    case _                             => None // reservoirs, canals, lake centrelines, poles, …

  /** Natural Earth's `name_<code>` → our `name:<code>` (Protomaps' keys: Chinese as zh-Hans / zh-Hant). */
  private def langKey(code: com.histmersion.util.basemap.BasemapLabels.Language): com.histmersion.util.basemap.BasemapLabels.PropKey = code match
    case "zh"  => "zh-Hans"
    case "zht" => "zh-Hant"
    case c     => c

  /** A property by name, whatever its case (Natural Earth's polygon files use upper case). */
  private def prop(p: io.circe.JsonObject, k: com.histmersion.util.basemap.BasemapLabels.PropKey): Option[Json] =
    p(k).orElse(p(k.toUpperCase(java.util.Locale.ROOT))).filterNot(_.isNull)

  private def text(p: io.circe.JsonObject, k: com.histmersion.util.basemap.BasemapLabels.TextK): Option[com.histmersion.util.basemap.BasemapLabels.Text] =
    prop(p, k).flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)

  private def number(p: io.circe.JsonObject, k: com.histmersion.util.basemap.BasemapLabels.NumberK): Option[com.histmersion.util.basemap.BasemapLabels.NumericProp] =
    prop(p, k).flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))

  /** The names a feature carries: `name` (Natural Earth's own, mostly English or local), `name:en`, and `name:<code>`
    * for each `name_<code>`.
    */
  def names(p: io.circe.JsonObject): List[(com.histmersion.util.basemap.BasemapLabels.Language, com.histmersion.util.basemap.BasemapLabels.Name)] =
    val own = text(p, "name").toList.map("name" -> _)
    val langs = p.keys.toList
      .map(_.toLowerCase(java.util.Locale.ROOT))
      .distinct
      .collect {
        case k if k.startsWith("name_") && k.drop(5).matches("[a-z]{2,3}") => k
      }
      .sorted
      .flatMap(k => text(p, k).map(v => s"name:${langKey(k.drop(5))}" -> v))
    own ++ langs

  /** The map zoom a feature is labelled from: Natural Earth's `min_label`, else `min_zoom`, else its `scalerank`; a
    * fraction rounds to the nearer zoom. Pure.
    */
  def minZoomOf(p: io.circe.JsonObject): com.histmersion.util.basemap.BasemapLabels.Label.MinZoom =
    number(p, "min_label")
      .orElse(number(p, "min_zoom"))
      .orElse(number(p, "scalerank").map(_ + 1))
      .map(z => math.max(0, math.round(z).toInt))
      .getOrElse(6)

  // ── geometry ───────────────────────────────────────────────────────────────────────────────────

  private def coords(j: Json): Option[(com.histmersion.util.basemap.BasemapLabels.CoordsResultPart1, com.histmersion.util.basemap.BasemapLabels.CoordsResultPart2)] =
    j.asArray.flatMap(a =>
      for x <- a.lift(0).flatMap(_.asNumber); y <- a.lift(1).flatMap(_.asNumber) yield (x.toDouble, y.toDouble)
    )

  private def ring(j: Json): Vector[(com.histmersion.util.basemap.BasemapLabels.RingResultPart1, com.histmersion.util.basemap.BasemapLabels.RingResultPart2)] = j.asArray.map(_.flatMap(coords)).getOrElse(Vector.empty)

  /** The signed area of a ring (shoelace), in square degrees. Pure. */
  def area(r: Vector[(com.histmersion.util.basemap.BasemapLabels.AreaRPart1, com.histmersion.util.basemap.BasemapLabels.AreaRPart2)]): com.histmersion.util.basemap.BasemapLabels.Area =
    if r.size < 3 then 0.0
    else
      r.indices.map { i =>
        val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size); x0 * y1 - x1 * y0
      }.sum / 2

  /** Is `p` inside ring `r` (even-odd)? Pure. */
  def inside(r: Vector[(com.histmersion.util.basemap.BasemapLabels.InsideRPart1, com.histmersion.util.basemap.BasemapLabels.InsideRPart2)], p: (com.histmersion.util.basemap.BasemapLabels.InsidePPart1, com.histmersion.util.basemap.BasemapLabels.InsidePPart2)): Boolean =
    val (px, py) = p
    r.indices.count { i =>
      val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
      (y0 > py) != (y1 > py) && px < (x1 - x0) * (py - y0) / (y1 - y0) + x0
    } % 2 == 1

  /** Where a polygon's name goes: inside its largest part, at the middle of the widest stretch of the horizontal line
    * through that part's centre of area (a centroid falls outside a crescent-shaped range; this does not). Holes are
    * ignored — a name may sit on a lake in a range. Pure.
    */
  def labelPoint(polygons: Vector[Vector[(com.histmersion.util.basemap.BasemapLabels.LabelPointPolygonsPart1, com.histmersion.util.basemap.BasemapLabels.LabelPointPolygonsPart2)]]): Option[(com.histmersion.util.basemap.BasemapLabels.LabelPointResultPart1, com.histmersion.util.basemap.BasemapLabels.LabelPointResultPart2)] =
    polygons.filter(_.size >= 3).maxByOption(r => math.abs(area(r))).flatMap { r =>
      val a = area(r)
      val (cx, cy) =
        if a == 0 then (r.map(_._1).sum / r.size, r.map(_._2).sum / r.size)
        else
          val parts = r.indices.map { i =>
            val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
            val c = x0 * y1 - x1 * y0
            ((x0 + x1) * c, (y0 + y1) * c)
          }
          (parts.map(_._1).sum / (6 * a), parts.map(_._2).sum / (6 * a))
      if inside(r, (cx, cy)) then Some((cx, cy))
      else
        // the crossings of the line y = cy with the ring, sorted: inside between pairs
        val xs = r.indices.flatMap { i =>
          val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
          Option.when((y0 > cy) != (y1 > cy))((x1 - x0) * (cy - y0) / (y1 - y0) + x0)
        }.sorted
        xs.grouped(2)
          .collect { case Seq(a, b) => (a, b) }
          .maxByOption((a, b) => b - a)
          .map((a, b) => ((a + b) / 2, cy))
          .orElse(r.headOption)
    }

  /** One GeoJSON feature as a label, when its class is kept and it has a name. */
  def label(f: Json): Option[Label] =
    for
      p <- f.hcursor.downField("properties").focus.flatMap(_.asObject)
      kind <- text(p, "featurecla").flatMap(kindOf)
      ns = names(p)
      if ns.exists(_._1 == "name") || ns.exists(_._1 == "name:en")
      g <- f.hcursor.downField("geometry").focus.flatMap(_.asObject)
      typ <- g("type").flatMap(_.asString)
      cs <- g("coordinates")
      placed <- typ match
        case "Point"   => coords(cs).map(c => (c, Vector.empty[Vector[(Double, Double)]]))
        case "Polygon" => labelPoint(cs.asArray.flatMap(_.headOption).map(ring).toVector).map(c => (c, Vector.empty))
        case "MultiPolygon" =>
          labelPoint(cs.asArray.map(_.flatMap(_.asArray.flatMap(_.headOption).map(ring))).getOrElse(Vector.empty))
            .map(c => (c, Vector.empty))
        case "LineString"      => Option(ring(cs)).filter(_.size >= 2).map(l => (l(l.size / 2), Vector(l)))
        case "MultiLineString" =>
          Option(cs.asArray.map(_.map(ring).filter(_.size >= 2)).getOrElse(Vector.empty))
            .filter(_.nonEmpty)
            .map(ls => (ls.maxBy(_.size).apply(ls.maxBy(_.size).size / 2), ls))
        case _ => None
    yield
      val ((lon, lat), line) = placed
      Label(
        kind,
        ns,
        lon,
        lat,
        minZoomOf(p),
        number(p, "max_label").map(z => math.round(z).toInt),
        if kind == "river" then line else Vector.empty,
        number(p, "elevation").filter(_ => kind == "peak").map(_.round)
      )

  /** Every kept label of one GeoJSON FeatureCollection. */
  def parse(file: com.histmersion.util.basemap.BasemapLabels.FileName, doc: Json): Either[com.histmersion.util.basemap.BasemapLabels.Problem, List[Label]] =
    doc.hcursor
      .downField("features")
      .focus
      .flatMap(_.asArray)
      .toRight(s"$file is not a GeoJSON FeatureCollection")
      .map(_.toList.flatMap(label))

  /** Does the label touch `b` (grown by `margin` degrees)? Pure. */
  def within(l: Label, b: BBox, margin: com.histmersion.util.basemap.BasemapLabels.Margin = 1.0): Boolean =
    def in(lon: com.histmersion.util.basemap.BasemapLabels.WithinInLon, lat: com.histmersion.util.basemap.BasemapLabels.WithinInLat) =
      lon >= b.minLon - margin && lon <= b.maxLon + margin && lat >= b.minLat - margin && lat <= b.maxLat + margin
    if l.line.isEmpty then in(l.lon, l.lat) else l.line.exists(_.exists(in.tupled))

  // ── tiles ──────────────────────────────────────────────────────────────────────────────────────

  private val Extent = 4096

  /** World position of lon/lat in units of 1/4096 of a zoom-`z` tile. Pure. */
  def world(lon: com.histmersion.util.basemap.BasemapLabels.WorldLon, lat: com.histmersion.util.basemap.BasemapLabels.WorldLat, z: com.histmersion.util.basemap.BasemapLabels.Zoom): (com.histmersion.util.basemap.BasemapLabels.WorldResultPart1, com.histmersion.util.basemap.BasemapLabels.WorldResultPart2) =
    val n = (1L << z).toDouble * Extent
    val r = math.toRadians(lat.max(-Basemap.MaxLat).min(Basemap.MaxLat))
    ((lon + 180.0) / 360.0 * n, (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n)

  /** Douglas–Peucker: the points of `l` that keep it within `tolerance` of the original. Pure. */
  def simplify(l: Vector[(com.histmersion.util.basemap.BasemapLabels.SimplifyLPart1, com.histmersion.util.basemap.BasemapLabels.SimplifyLPart2)], tolerance: com.histmersion.util.basemap.BasemapLabels.Tolerance): Vector[(com.histmersion.util.basemap.BasemapLabels.SimplifyResultPart1, com.histmersion.util.basemap.BasemapLabels.SimplifyResultPart2)] =
    if l.size <= 2 then l
    else
      val keep = Array.fill(l.size)(false)
      keep(0) = true; keep(l.size - 1) = true
      val stack = mutable.Stack((0, l.size - 1)) // ranges still to split
      while stack.nonEmpty do
        val (a, b) = stack.pop()
        val (ax, ay) = l(a); val (bx, by) = l(b)
        val dx = bx - ax; val dy = by - ay
        val len = math.hypot(dx, dy)
        var far = -1 // the farthest point of the range and its distance
        var dist = 0.0
        var i = a + 1
        while i < b do
          val (px, py) = l(i)
          val d =
            if len == 0 then math.hypot(px - ax, py - ay) else math.abs(dy * px - dx * py + bx * ay - by * ax) / len
          if d > dist then { dist = d; far = i }
          i += 1
        if far >= 0 && dist > tolerance then
          keep(far) = true
          stack.push((a, far)); stack.push((far, b))
      l.indices.filter(keep).map(l).toVector

  /** The runs of `l` (world units) whose segments touch tile `(x, y)` grown by `buffer`, relative to the tile's corner
    * and rounded. Pure.
    */
  def clip(l: Vector[(com.histmersion.util.basemap.BasemapLabels.ClipLPart1, com.histmersion.util.basemap.BasemapLabels.ClipLPart2)], x: com.histmersion.util.basemap.BasemapLabels.ClipX, y: com.histmersion.util.basemap.BasemapLabels.ClipY, buffer: com.histmersion.util.basemap.BasemapLabels.Buffer): Vector[Vector[(com.histmersion.util.basemap.BasemapLabels.PointCount, com.histmersion.util.basemap.BasemapLabels.PointCount)]] =
    val x0 = x * Extent - buffer; val y0 = y * Extent - buffer
    val x1 = (x + 1) * Extent + buffer; val y1 = (y + 1) * Extent + buffer
    def touches(a: (com.histmersion.util.basemap.BasemapLabels.ClipTouchesAPart1, com.histmersion.util.basemap.BasemapLabels.ClipTouchesAPart2), b: (com.histmersion.util.basemap.BasemapLabels.ClipTouchesBPart1, com.histmersion.util.basemap.BasemapLabels.ClipTouchesBPart2)) =
      math.max(a._1, b._1) >= x0 && math.min(a._1, b._1) <= x1 && math.max(a._2, b._2) >= y0 && math.min(
        a._2,
        b._2
      ) <= y1
    val runs = Vector.newBuilder[Vector[(Int, Int)]]
    val run = Vector.newBuilder[(Double, Double)]
    var open = false // a run is being built
    l.indices.dropRight(1).foreach { i =>
      if touches(l(i), l(i + 1)) then
        if !open then { run.clear(); run += l(i); open = true }
        run += l(i + 1)
      else if open then { runs += rel(run.result(), x, y); open = false }
    }
    if open then runs += rel(run.result(), x, y)
    runs.result().filter(_.size >= 2)

  private def rel(r: Vector[(com.histmersion.util.basemap.BasemapLabels.RelRPart1, com.histmersion.util.basemap.BasemapLabels.RelRPart2)], x: com.histmersion.util.basemap.BasemapLabels.TileX, y: com.histmersion.util.basemap.BasemapLabels.TileY): Vector[(com.histmersion.util.basemap.BasemapLabels.PointCount, com.histmersion.util.basemap.BasemapLabels.PointCount)] =
    r.map((wx, wy) => (math.round(wx - x * Extent).toInt, math.round(wy - y * Extent).toInt))
      .foldLeft(Vector.empty[(Int, Int)])((acc, p) => if acc.lastOption.contains(p) then acc else acc :+ p)

  /** Is the label drawn by the page from data tile `z` (which serves map zoom z + 1; the deepest, every zoom past it)?
    * Its own `min_zoom` filters it further in the page. Pure.
    */
  def inTile(l: Label, z: com.histmersion.util.basemap.BasemapLabels.Zoom, maxZoom: com.histmersion.util.basemap.BasemapLabels.Label.MaxZoom): Boolean =
    (z == maxZoom || l.minZoom <= z + 1) && l.maxZoom.forall(_ >= z + 1)

  private def props(l: Label): List[(com.histmersion.util.basemap.BasemapLabels.PropKey, String | Long)] =
    ("kind" -> l.kind) :: ("min_zoom" -> l.minZoom.toLong) :: l.names ++ l.elevation.map("elevation" -> _).toList

  /** The archive's tiles, by tile id: points in the tile they fall in, rivers clipped to each tile they cross
    * (simplified to about a pixel of the zoom), zooms 0…`maxZoom`, gzip-compressed MVT. Pure.
    */
  def tiles(labels: List[Label], maxZoom: com.histmersion.util.basemap.BasemapLabels.TilesMaxZoom): Vector[(com.histmersion.util.basemap.BasemapLabels.TileKey, com.histmersion.util.basemap.BasemapLabels.TileBytes)] =
    type Features = mutable.ListBuffer[(Int, Vector[Int], List[(String, String | Long)])]
    // per tile id, its points and its lines, filled zoom by zoom
    val out = mutable.TreeMap.empty[Long, (Features, Features)]
    def slot(z: com.histmersion.util.basemap.BasemapLabels.Zoom, x: com.histmersion.util.basemap.BasemapLabels.TileX, y: com.histmersion.util.basemap.BasemapLabels.TileY) =
      out.getOrElseUpdate(PmTiles.tileId(z, x, y), (mutable.ListBuffer.empty, mutable.ListBuffer.empty))
    for z <- 0 to maxZoom do
      val n = 1L << z
      labels.filter(inTile(_, z, maxZoom)).foreach { l =>
        if l.line.isEmpty then
          val (wx, wy) = world(l.lon, l.lat, z)
          val tx = math.floor(wx / Extent).toLong.max(0).min(n - 1)
          val ty = math.floor(wy / Extent).toLong.max(0).min(n - 1)
          slot(z, tx, ty)._1 += ((
            1,
            Mvt.pointGeometry(math.round(wx - tx * Extent).toInt, math.round(wy - ty * Extent).toInt),
            props(l)
          ))
        else
          val parts = l.line.map(p => simplify(p.map(world(_, _, z)), Extent / 256.0)).filter(_.size >= 2)
          val all = parts.flatten
          if all.nonEmpty then
            val txs = (math
              .floor(all.map(_._1).min / Extent)
              .toLong
              .max(0) to math.floor(all.map(_._1).max / Extent).toLong.min(n - 1))
            val tys = (math
              .floor(all.map(_._2).min / Extent)
              .toLong
              .max(0) to math.floor(all.map(_._2).max / Extent).toLong.min(n - 1))
            for tx <- txs; ty <- tys do
              val runs = parts.flatMap(clip(_, tx, ty, 64))
              if runs.nonEmpty then slot(z, tx, ty)._2 += ((2, Mvt.lineGeometry(runs), props(l)))
      }
    out.toVector.map { case (id, (points, lines)) =>
      val layers = List(
        Option.when(points.nonEmpty)(Mvt.typedLayer("physical", points.toList)),
        Option.when(lines.nonEmpty)(Mvt.typedLayer("rivers", lines.toList))
      ).flatten
      id -> PmTiles.gzip(Mvt.tile(layers))
    }

  /** Write `labels` as the archive `key` of `store` (zooms 0…`maxZoom`, bounds `b`); answers its tiles and bytes. */
  def write(store: Store, key: Store.Key, labels: List[Label], b: BBox, maxZoom: com.histmersion.util.basemap.BasemapLabels.WriteMaxZoom, metadata: Json): (com.histmersion.util.basemap.BasemapLabels.LabelCount, com.histmersion.ByteCount) =
    val ts = tiles(labels, maxZoom)
    val w = PmTilesWriter(store, key)
    val header = PmTiles.Header(
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      true,
      PmTiles.Compression.Gzip,
      PmTiles.Compression.Gzip,
      PmTiles.TileType.Mvt,
      0,
      maxZoom,
      b.minLon,
      b.minLat,
      b.maxLon,
      b.maxLat,
      math.min(maxZoom, 5),
      (b.minLon + b.maxLon) / 2,
      (b.minLat + b.maxLat) / 2
    )
    try
      ts.foreach((id, t) => w.add(id, t))
      (ts.size, w.finish(header, metadata))
    catch case e: Exception => { w.abort(); throw e }
