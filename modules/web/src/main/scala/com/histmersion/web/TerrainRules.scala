package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*

import scala.scalajs.js

/** A terrain style ([[TerrainStyle]], served by `GET /api/basemap/styles`) compiled ONCE into
  * protomaps-leaflet's paint and label rules: every selector a set lookup, every colour resolved, every
  * zoom-dependent value a small function — nothing is parsed or looked up per feature per frame. */
object TerrainRules:

  /** What one style layer selects, precompiled. `geom`: 0 any, 1 point, 2 line, 3 polygon. */
  final case class Selector(geom: Int, kinds: Set[String], notKinds: Set[String], details: Set[String], ownMinZoom: Boolean):
    /** Does a feature of this geometry, `kind`, `kind_detail` and own `min_zoom` (NaN: none) match
      * at map zoom `zoom`? Pure. */
    def matches(geomType: Int, kind: String, detail: String, ownMin: Double, zoom: Double): Boolean =
      (geom == 0 || geom == geomType) &&
        (kinds.isEmpty || kinds(kind)) && !notKinds(kind) &&
        (details.isEmpty || details(detail)) &&
        (!ownMinZoom || ownMin.isNaN || zoom >= ownMin)

  def geomCode(g: Option[String]): Int = g match
    case Some("point")   => 1
    case Some("line")    => 2
    case Some("polygon") => 3
    case _               => 0

  def selector(l: StyleLayer): Selector =
    Selector(geomCode(l.geometry), l.kinds.toSet, l.notKinds.toSet, l.details.toSet, l.draw == "label" && l.ownMinZoom)

  private val Token = """var\((--[a-zA-Z0-9-]+)\)""".r

  /** A style colour as the canvas takes it: `var(--token)` is the stylesheet's value (transparent when
    * the stylesheet has no such token), anything else as written. Pure given `lookup`. */
  def colour(c: String, lookup: String => String): String = c.trim match
    case Token(t) => Option(lookup(t)).map(_.trim).filter(_.nonEmpty).getOrElse("transparent")
    case other    => other

  /** A zoom-dependent value as protomaps-leaflet takes it: a number, or a function of the zoom it
    * passes — which for a paint rule is the DATA tile's zoom, one below the map's (`offset` 1). */
  def zoomed(v: ZoomValue, offset: Double): js.Any =
    if v.constant then v.at(0)
    else ((z: Double) => v.at(z + offset)): js.Function1[Double, Double]

  /** The layers that can be drawn: those of a source the server has. Pure. */
  def usable(layers: List[StyleLayer], sources: Set[String]): List[StyleLayer] = layers.filter(l => sources(l.source))

  /** What a set of style layers reads, per source: each data layer, its geometries and kinds (empty:
    * all) — the `?layers=` a tile URL asks the server for ([[Basemap.subsetParam]]), so the page never
    * downloads or decodes what the style does not draw. A layer drawn by a rule with `not-kinds` or
    * without `kinds` is read whole (all its kinds). Pure. */
  def subsets(layers: List[StyleLayer]): Map[String, Map[String, Basemap.Read]] =
    layers.groupBy(_.source).view.mapValues { ls =>
      ls.flatMap { l =>
        val kinds = if l.notKinds.isEmpty then l.kinds.toSet else Set.empty[String]
        val geoms = geomCode(l.geometry) match { case 0 => Set.empty[Int]; case g => Set(g) }
        l.dataLayers.map(_ -> Basemap.Read(geoms, kinds))
      }.groupMapReduce(_._1)(_._2)(_.merge(_))
    }.toMap

  /** The language a style's names are in: its own, else the document's. */
  def language(style: TerrainStyle, styles: TerrainStyles): String = style.language.getOrElse(styles.language)

  /** A number from a feature property that may be a number or text; NaN when neither. */
  private def num(v: Option[js.Any]): Double =
    v.flatMap(x => if js.typeOf(x) == "number" then Some(x.asInstanceOf[Double]) else x.toString.toDoubleOption)
      .getOrElse(Double.NaN)

  private def text(f: PmFeature, k: String): String = f.props.get(k).map(_.toString).getOrElse("")

  /** Label order: negative when `a` (its own min_zoom, its population rank) is placed before `b` — the
    * lower min_zoom first, then the higher rank; a missing value after any present one. Pure. */
  def order(aMin: Double, aRank: Double, bMin: Double, bRank: Double): Double =
    val byZoom = (if aMin.isNaN then 99 else aMin) - (if bMin.isNaN then 99 else bMin)
    if byZoom != 0 then byZoom else (if bRank.isNaN then -1 else bRank) - (if aRank.isNaN then -1 else aRank)

  /** The feature properties the paint rules read (their filters). */
  val PaintKeys: List[String] = List("kind", "kind_detail")

  /** The feature properties the label rules read: their filters, their order, and the names in
    * `language` ([[TerrainStyles.labelProps]]). Pure. */
  def labelKeys(language: String): List[String] =
    List("kind", "kind_detail", "min_zoom", "population_rank") ++ TerrainStyles.labelProps(language)

  /** The style's rules, ready for `protomapsL.leafletLayer`, with what each of its two layers asks the
    * server for: the data layers and geometries, and the properties. */
  final case class Compiled(paint: js.Array[js.Object], labels: js.Array[js.Object],
      paintReads: Map[String, Map[String, Basemap.Read]], labelReads: Map[String, Map[String, Basemap.Read]],
      paintKeys: List[String] = PaintKeys, labelKeys: List[String] = Nil)

  /** Compile `style` for the `sources` the server has; the label rules only when `labelsOn`. */
  def compile(style: TerrainStyle, language: String, sources: Set[String], labelsOn: Boolean, lookup: String => String): Compiled =
    val layers = usable(style.layers, sources)
    val paint = layers.filter(l => l.draw == "fill" || l.draw == "line").flatMap { l =>
      val sel = selector(l)
      val sym: js.Object =
        if l.draw == "fill" then
          val o = js.Dynamic.literal(fill = colour(l.fill.getOrElse("transparent"), lookup))
          l.opacity.foreach(v => o.opacity = zoomed(v, 1))
          new PolygonSymbolizer(o)
        else
          val o = js.Dynamic.literal(color = colour(l.color.getOrElse("transparent"), lookup),
            width = zoomed(l.width.getOrElse(ZoomValue.const(1)), 1))
          l.opacity.foreach(v => o.opacity = zoomed(v, 1))
          if l.dash.nonEmpty then { o.dash = js.Array(l.dash*); o.dashColor = o.color; o.dashWidth = o.width }
          new LineSymbolizer(o)
      val filter: js.Function2[Double, PmFeature, Boolean] =
        (z, f) => sel.matches(f.geomType, text(f, "kind"), text(f, "kind_detail"), Double.NaN, z + 1)
      l.dataLayers.map(dl => rule(l, dl, sym, filter))
    }
    val props = js.Array(TerrainStyles.labelProps(language)*)
    val labels =
      if !labelsOn || !style.labels then Nil
      else layers.filter(_.draw == "label").flatMap { l =>
        val sel  = selector(l)
        val font = l.font.getOrElse(StyleFont())
        val o = js.Dynamic.literal(
          labelProps = props,
          fill = colour(l.color.getOrElse("transparent"), lookup),
          stroke = colour(l.halo.getOrElse("transparent"), lookup),
          width = zoomed(l.haloWidth.getOrElse(ZoomValue.const(0)), 0),
          fontFamily = font.family, fontSize = zoomed(font.size, 0), fontWeight = font.weight, fontStyle = font.style)
        l.transform.filter(_ != "none").foreach(t => o.textTransform = t)
        l.letterSpacing.foreach(s => o.letterSpacing = s)
        l.maxLineChars.foreach(n => o.maxLineChars = n)
        val sym: js.Object = if l.placement == "line" then new LineLabelSymbolizer(o) else new CenteredTextSymbolizer(o)
        val filter: js.Function2[Double, PmFeature, Boolean] =
          (z, f) => sel.matches(f.geomType, text(f, "kind"), text(f, "kind_detail"), num(f.props.get("min_zoom")), z)
        // the more important first: the lower a feature's own min_zoom, the earlier it is placed; among
        // equals, the larger place (`population_rank`, the Protomaps places' size class)
        val sort: js.Function2[js.Dictionary[js.Any], js.Dictionary[js.Any], Double] =
          (a, b) => order(num(a.get("min_zoom")), num(a.get("population_rank")), num(b.get("min_zoom")), num(b.get("population_rank")))
        l.dataLayers.map { dl =>
          val r = rule(l, dl, sym, filter).asInstanceOf[js.Dynamic]
          r.sort = sort
          r.asInstanceOf[js.Object]
        }
      }
    val drawn = layers.filter(l => l.draw == "fill" || l.draw == "line")
    val named = if !labelsOn || !style.labels then Nil else layers.filter(_.draw == "label")
    Compiled(js.Array(paint*), js.Array(labels*), subsets(drawn), subsets(named), PaintKeys, labelKeys(language))

  private def rule(l: StyleLayer, dataLayer: String, sym: js.Object, filter: js.Function2[Double, PmFeature, Boolean]): js.Object =
    val r = js.Dynamic.literal(dataSource = l.source, dataLayer = dataLayer, symbolizer = sym, filter = filter)
    l.minZoom.foreach(z => r.minzoom = z)
    l.maxZoom.foreach(z => r.maxzoom = z)
    r
