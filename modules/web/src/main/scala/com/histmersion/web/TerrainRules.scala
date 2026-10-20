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

  /** From which TILE zoom each data layer is read, per source — when every layer reading it starts at a
    * zoom (`min-zoom`, a map zoom: the tile is one zoom less, `levelDiff` 1); a layer read at every zoom
    * is absent. The server then leaves it out of shallower tiles ([[Basemap.subsetAt]]). Pure. */
  def subsetZooms(layers: List[StyleLayer]): Map[String, Map[String, Int]] =
    layers.groupBy(_.source).view.mapValues { ls =>
      ls.flatMap(l => l.dataLayers.map(_ -> l.minZoom)).groupMap(_._1)(_._2).collect {
        case (dl, zs) if zs.forall(_.isDefined) => dl -> math.max(0, math.floor(zs.flatten.min).toInt - 1)
      }
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
      paintKeys: List[String] = PaintKeys, labelKeys: List[String] = Nil,
      paintZooms: Map[String, Map[String, Int]] = Map.empty, labelZooms: Map[String, Map[String, Int]] = Map.empty)

  /** Compile `style` for the `sources` the server has; the label rules only when `labelsOn`. */
  def compile(style: TerrainStyle, language: String, sources: Set[String], labelsOn: Boolean, lookup: String => String,
      paintOnly: StyleLayer => Boolean = _ => true): Compiled =
    val layers = usable(style.layers, sources).filter(l => l.draw == "label" || paintOnly(l))
    val paint = layers.filter(l => l.draw == "fill" || l.draw == "line").flatMap { l =>
      val sel = selector(l)
      val sym: js.Object =
        if l.draw == "fill" && l.glow.isDefined then
          val g = l.glow.get
          TerrainLabels.glowFill(colour(l.fill.getOrElse("transparent"), lookup),
            z => l.opacity.fold(1.0)(_.at(z + 1)), colour(g.color, lookup), z => g.width.at(z + 1))
        else if l.draw == "fill" then
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
        // a name at a point: our own symbolizer, its box the whole block (TerrainLabels); along a line, the library's
        val sym: js.Object =
          if l.placement == "line" then new LineLabelSymbolizer(o)
          else TerrainLabels.symbolizer(TerrainLabels.Spec(props, z => cssFont(font, z), z => font.size.at(z),
            colour(l.color.getOrElse("transparent"), lookup), colour(l.halo.getOrElse("transparent"), lookup),
            z => l.haloWidth.fold(0.0)(_.at(z)), l.transform.filter(_ != "none"), l.letterSpacing.getOrElse(0),
            l.maxLineChars.getOrElse(15), l.padding, l.lineHeight))
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
    Compiled(js.Array(paint*), js.Array(labels*), subsets(drawn), subsets(named), PaintKeys, labelKeys(language),
      subsetZooms(drawn), subsetZooms(named))

  /** Is a layer the GROUND under a tinted relief — the land's fill or the open sea's — drawn below the
    * colours by height and depth, so they cover it where there is elevation and it shows where there is
    * none? Everything else (land cover, lakes, rivers, the shore) lies over them. Pure. */
  def isGround(l: StyleLayer): Boolean =
    l.draw == "fill" && l.glow.isEmpty &&
      ((l.dataLayers == List("earth") && l.kinds.isEmpty) || (l.dataLayers == List("water") && l.kinds == List("ocean")))

  /** A label font as the canvas takes it: style, weight, size at zoom `z`, family. Pure. */
  def cssFont(f: StyleFont, z: Double): String = s"${f.style} ${f.weight} ${f.size.at(z)}px ${f.family}"

  /** The font families a style's names use (to load them before drawing). Pure. */
  def fontsOf(style: TerrainStyle): List[String] =
    (style.layers.filter(_.draw == "label").flatMap(_.font).map(f => s"${f.style} ${f.weight} 12px ${f.family}") ++
      Option.when(style.hasContours)(style.relief.contours.font).map(f => s"${f.style} ${f.weight} 12px ${f.family}").toList).distinct

  private def rule(l: StyleLayer, dataLayer: String, sym: js.Object, filter: js.Function2[Double, PmFeature, Boolean]): js.Object =
    val r = js.Dynamic.literal(dataSource = l.source, dataLayer = dataLayer, symbolizer = sym, filter = filter)
    l.minZoom.foreach(z => r.minzoom = z)
    l.maxZoom.foreach(z => r.maxzoom = z)
    r
