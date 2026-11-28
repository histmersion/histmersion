package com.histmersion
package web.map.maplibre

import com.histmersion.web.map.*
import com.histmersion.web.ui.ThemeTokens
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.api.basemap.Basemap
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.map.MapSurface
import com.histmersion.web.map.BaseMapSpec
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.basemap.BaseMapSource
import com.histmersion.api.BBox
import com.histmersion.lint.{tolerated, LintRule}

/** The library's styles for the app's base maps ([[BaseMapSpec]]): the two vector styles are MapLibre style documents
  * in the web resources (`web/styles/<style>.json`, every value with the rule it follows, docs/research-map-styles.md),
  * their sources pointed at the server's tiles here; the online maps are a raster source.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object MapLibreStyles:
  type Origin = Origin.T
  object Origin extends Newtype[String, Pure]

  private val docs = mutable.HashMap.empty[VectorStyle.Id, Future[js.Dynamic]]

  /** The style document of `s`, fetched once. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def doc(s: VectorStyle): Future[js.Dynamic] =
    docs.getOrElseUpdate(
      s.id, {
        import scala.scalajs.js.Thenable.Implicits.*
        val f = dom
          .fetch(s"/web/styles/${s.id}.json")
          .toFuture
          .flatMap(r => r.json().toFuture)
          .map(_.asInstanceOf[js.Dynamic])
        // a failed fetch is not kept: the next ask fetches again
        f.failed.foreach(_ => docs.remove(s.id))
        f
      }
    )

  /** A copy of a JSON value. */
  private def copy(v: js.Any): js.Dynamic = js.JSON.parse(js.JSON.stringify(v)).asInstanceOf[js.Dynamic]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def background(): String = ThemeTokens(ThemeTokens.Name("--bg")).value

  /** The page's own background and nothing else. */
  def blank(): js.Object =
    js.Dynamic
      .literal(
        version = 8,
        sources = js.Dynamic.literal(),
        layers = js.Array(
          js.Dynamic.literal(
            id = "background",
            `type` = "background",
            paint = js.Dynamic.literal(`background-color` = background())
          )
        )
      )
      .asInstanceOf[js.Object]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def style(b: BaseMapSpec, origin: Origin): Future[js.Any] = b match
    case BaseMapSpec.Blank     => Future.successful(blank())
    case r: BaseMapSpec.Raster => Future.successful(raster(r))
    case v: BaseMapSpec.Vector =>
      val withContours =
        v.contours.shown && !v.reduced.reduced && v.style == VectorStyle.Physical && deeperRelief(v.status).isDefined
      doc(v.style).flatMap { d =>
        val s = resolve(copy(d), v.status, v.lang, v.names, v.reduced, origin, v.seaDepth)
        if withContours then
          Future.delegate(contours(s, v.status, origin)).map(_ => s: js.Any).recover { case _ =>
            drop(s, MapSurface.LayerId("contours")); s
          }
        else { drop(s, MapSurface.LayerId("contours")); Future.successful(s) }
      }

  /** The credits a style owes: each source's `attribution` that a layer drawn (not `visibility: none`) reads, once
    * each, in the style's order. Pure over the JSON.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def credits(s: js.Dynamic): Seq[BaseMapSpec.Raster.Attribution] =
    if js.isUndefined(s) || s == null || js.isUndefined(s.sources) || js.isUndefined(s.layers) then Nil
    else
      val drawn = s.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .toSeq
        .filter(l =>
          !js.isUndefined(l.source) &&
            (js.isUndefined(l.layout) || js.isUndefined(l.layout.visibility) || l.layout.visibility.toString != "none")
        )
        .map(_.source.toString)
        .distinct
      drawn
        .flatMap { id =>
          val src = s.sources.selectDynamic(id)
          if js.isUndefined(src) || src == null || js.isUndefined(src.attribution) || src.attribution == null then None
          else Some(src.attribution.toString).filter(_.trim.nonEmpty)
        }
        .distinct
        .map(a => BaseMapSpec.Raster.Attribution(a))

  def carryOverlays(previous: js.Dynamic, next: js.Dynamic, overlays: Set[String]): js.Dynamic =
    def dict(d: js.Dynamic): js.Dictionary[js.Any] =
      if js.isUndefined(d) || d == null then js.Dictionary.empty[js.Any] else d.asInstanceOf[js.Dictionary[js.Any]]
    def layers(s: js.Dynamic): js.Array[js.Dynamic] =
      if js.isUndefined(s.layers) || s.layers == null then js.Array() else s.layers.asInstanceOf[js.Array[js.Dynamic]]
    if js.isUndefined(previous) || previous == null || js.isUndefined(next) || next == null then next
    else
      val nextSources = dict(next.sources)
      val sources = js.Dictionary.empty[js.Any]
      nextSources.foreach((k, v) => sources(k) = v)
      dict(previous.sources).foreach((k, v) => if overlays(k) && !nextSources.contains(k) then sources(k) = v)
      val ids = layers(next).map(_.id.toString).toSet
      val carried =
        layers(previous).filter(l => !js.isUndefined(l.source) && overlays(l.source.toString) && !ids(l.id.toString))
      val out = js.Object.assign(js.Dynamic.literal(), next.asInstanceOf[js.Object]).asInstanceOf[js.Dynamic]
      out.sources = sources
      out.layers = layers(next).concat(carried)
      out

  /** `{s}` → the subdomains a, b, c ([[BaseMapSource.Subdomains]], which the server's policy lists too). Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def expand(template: BasemapApi.TileTemplate): js.Array[String] =
    val t = template.value
    if t.contains("{s}") then js.Array(BaseMapSource.Subdomains.map(x => t.replace("{s}", x))*)
    else js.Array(t)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def raster(r: BaseMapSpec.Raster): js.Object =
    val paint = js.Dynamic.literal(`raster-opacity` = r.opacity.value)
    if r.dark.dark then
      // the light tiles drawn dark: inverted, the hues turned back, a little dimmed
      paint.`raster-brightness-min` = 0.9
      paint.`raster-brightness-max` = 0.06
      paint.`raster-hue-rotate` = 180
      paint.`raster-saturation` = -0.15
    js.Dynamic
      .literal(
        version = 8,
        sources = js.Dynamic.literal(online =
          js.Dynamic.literal(
            `type` = "raster",
            tiles = expand(r.template),
            tileSize = 256,
            maxzoom = r.maxZoom.value,
            attribution = r.attribution.value
          )
        ),
        layers = js.Array(
          js.Dynamic.literal(
            id = "background",
            `type` = "background",
            paint = js.Dynamic.literal(`background-color` = background())
          ),
          js.Dynamic.literal(id = "online", `type` = "raster", source = "online", paint = paint)
        )
      )
      .asInstanceOf[js.Object]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def info(s: Basemap.Status, layer: String): Option[Basemap.LayerInfo] =
    s.layers.find(i => i.layer.value == layer && i.present.present && i.error.isEmpty)

  /** The relief, when it reaches deeper than the extract — a focus region, or the on-demand relief: what the contours
    * are traced from (the extract's relief alone is too coarse for them).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def deeperRelief(s: Basemap.Status): Option[Basemap.LayerInfo] =
    info(s, "terrain").filter(i => i.focus.nonEmpty || i.onDemand.exists(_.enabled.enabled))

  /** Take source `id` and every layer drawing it out of style `s`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def drop(s: js.Dynamic, id: MapSurface.LayerId): Unit =
    js.special.delete(s.sources, id)
    s.layers =
      s.layers.asInstanceOf[js.Array[js.Dynamic]].filter(l => js.isUndefined(l.source) || l.source.toString != id.value)

  /** A style document made drawable over this server's extracts. Pure over the JSON (`s` is changed and answered): each
    * source pointed at the layer the server has (its version in the URL: a new fetch is never a stale cached tile, its
    * credits, its depth — the relief to the deepest zoom any archive holds, the server cutting the rest from their
    * ancestors), a source the server lacks taken out with every layer that draws it; the names in `lang` (`local`: each
    * place's own), none without `names`; `reduced`: the relief left out (the cheapest drawing, for a browser without a
    * GPU); without `seaDepth` the layers drawing the sea floor are hidden (`visibility: none`: their tiles are not
    * asked for, and turning them on again is a diff of their layout).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolve(
      s: js.Dynamic,
      status: Basemap.Status,
      lang: BaseMapSpec.Vector.Lang,
      names: BaseMapSpec.Vector.Names,
      reduced: BaseMapSpec.Vector.Reduced,
      origin: Origin,
      seaDepth: BaseMapSpec.Vector.SeaDepth = BaseMapSpec.Vector.SeaDepth.Shown
  ): js.Dynamic =
    val meta = s.metadata.selectDynamic("histmersion:sources").asInstanceOf[js.Dictionary[String]]
    js.Object.keys(s.sources.asInstanceOf[js.Object]).foreach { id =>
      val src = s.sources.selectDynamic(id)
      meta.get(id) match
        case Some("contours") => ()
        case Some(layer)      =>
          info(status, layer) match
            case None    => drop(s, MapSurface.LayerId(id))
            case Some(i) =>
              val v = i.version.map(_.value).getOrElse("0")
              src.tiles = src.tiles.asInstanceOf[js.Array[String]].map(t => origin.value + t.replace("{version}", v))
              // the deepest the server serves: the extract, a focus region, the tiles read on demand (#3760)
              val depth = (id, layer) match
                case ("lc", _) => Some(math.min(7, i.maxZoom.map(_.value).getOrElse(7)))
                case _         => i.deepest.map(_.value)
              depth.foreach(z => src.maxzoom = z)
              if i.attribution.value.nonEmpty && id != "lc" then src.attribution = i.attribution.value
              // where the server has tiles: the map asks for none outside — unless the server reads the layer on
              // demand there (#3760), or (the relief) a focus region lies outside it
              i.bbox
                .flatMap(BBox.parse)
                .filter(_ => i.focus.isEmpty && !i.onDemand.exists(_.enabled.enabled))
                .foreach(b => src.bounds = js.Array(b.minLon.value, b.minLat.value, b.maxLon.value, b.maxLat.value))
        case None => drop(s, MapSurface.LayerId(id))
    }
    if reduced.reduced then
      s.layers = s.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l => l.`type`.toString != "hillshade" && l.`type`.toString != "color-relief")
      List("dem", "sea").foreach(id =>
        if !js.isUndefined(s.sources.selectDynamic(id)) then drop(s, MapSurface.LayerId(id))
      )
    if !names.shown then
      s.layers = s.layers.asInstanceOf[js.Array[js.Dynamic]].filter(l => l.`type`.toString != "symbol")
    val seaSources =
      js.Object.keys(s.sources.asInstanceOf[js.Object]).filter(id => meta.get(id).contains("bathymetry")).toSet
    s.layers.asInstanceOf[js.Array[js.Dynamic]].foreach { l =>
      if !js.isUndefined(l.source) && seaSources.contains(l.source.toString) then
        if js.isUndefined(l.layout) then l.layout = js.Dynamic.literal()
        l.layout.visibility = if seaDepth.shown then "visible" else "none"
    }
    // the names' language: `name:{lang}` in every text field
    val key = if lang.value == "local" then "name" else s"name:${lang.value}"
    s.layers.asInstanceOf[js.Array[js.Dynamic]].foreach { l =>
      if !js.isUndefined(l.layout) && !js.isUndefined(l.layout.`text-field`) then
        l.layout.`text-field` = replaceString(l.layout.`text-field`, "name:{lang}", key)
    }
    val faces = s.selectDynamic("font-faces")
    if !js.isUndefined(faces) then
      js.Object
        .keys(faces.asInstanceOf[js.Object])
        .foreach(f =>
          faces
            .selectDynamic(f)
            .asInstanceOf[js.Array[js.Dynamic]]
            .foreach(face => face.url = origin.value + face.url.toString)
        )
    js.special.delete(s, "metadata")
    s

  private def replaceString(v: js.Dynamic, from: String, to: String): js.Dynamic =
    if js.typeOf(v) == "string" then (if v.toString == from then to.asInstanceOf[js.Dynamic] else v)
    else if js.Array.isArray(v) then
      v.asInstanceOf[js.Array[js.Dynamic]].map(x => replaceString(x, from, to)).asInstanceOf[js.Dynamic]
    else v

  // ── contours: maplibre-contour (BSD-3, vendored), loaded on first use ─────────────────────────

  private lazy val contourLib: Future[js.Dynamic] =
    if js.typeOf(js.Dynamic.global.mlcontour) != "undefined" then Future.successful(js.Dynamic.global.mlcontour)
    else
      val p = Promise[js.Dynamic]()
      val sc = dom.document.createElement("script").asInstanceOf[dom.HTMLScriptElement]
      sc.src = "/web/vendor/maplibre-contour/maplibre-contour.js"
      sc.addEventListener("load", (_: dom.Event) => { val _ = p.trySuccess(js.Dynamic.global.mlcontour) })
      sc.addEventListener(
        "error",
        (_: dom.Event) => { val _ = p.tryFailure(Exception("maplibre-contour did not load")) }
      )
      dom.document.head.appendChild(sc)
      p.future

  // one contour source per relief version and depth (the library registers a page protocol for it)
  private val demSources = mutable.HashMap.empty[String, js.Dynamic]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def contours(s: js.Dynamic, status: Basemap.Status, origin: Origin): Future[Unit] =
    contourLib.map { lib =>
      val t = deeperRelief(status).get
      val deepest = t.deepest.map(_.value).getOrElse(t.maxZoom.map(_.value).getOrElse(8))
      val dem = demSources.getOrElseUpdate(
        s"${t.version.map(_.value).getOrElse("0")}@$deepest", {
          val d = js.Dynamic.newInstance(lib.DemSource)(
            js.Dynamic.literal(
              url = s"${origin.value}/api/basemap/terrain/{z}/{x}/{y}?v=${t.version.map(_.value).getOrElse("0")}",
              encoding = "terrarium",
              maxzoom = deepest,
              worker = true
            )
          )
          d.setupMaplibre(js.Dynamic.global.maplibregl)
          d
        }
      )
      s.sources.contours.tiles = js.Array(
        dem.contourProtocolUrl(
          js.Dynamic.literal(
            multiplier = 1,
            thresholds = js.Dynamic.literal(
              `10` = js.Array(100, 500),
              `11` = js.Array(50, 250),
              `12` = js.Array(20, 100),
              `13` = js.Array(10, 50)
            ),
            elevationKey = "ele",
            levelKey = "level",
            contourLayer = "contours"
          )
        )
      )
      ()
    }
