package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.api.{BBox, GeoPick}
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.media.MediaProvider
import com.histmersion.web.map.MapSurface
import com.histmersion.lint.{tolerated, LintRule}

/** "Only sources in view" toggle: mirrors the map's viewport into UiState.bbox so the server-side filter and the
  * shareable URL hash always agree with what the user is looking at.
  *
  * Ported from viewport.js.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object ViewportToggle:
  private def r5(x: Double): Double = Math.round(x * 1e5) / 1e5

  /** Wrap longitude to [-180, 180]. The map can give unwrapped values past the antimeridian. */
  private def wrapLon(x: Double): Double =
    val y = ((x + 180) % 360 + 360) % 360 - 180
    if y == -180 && x > 0 then 180.0 else y

  /** Map bounds as "minLon,minLat,maxLon,maxLat", normalised to +/-180/+/-90. A viewport straddling the antimeridian
    * comes back with minLon > maxLon; the server splits it into two envelopes. A viewport wider than the world becomes
    * the whole world.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bboxOf(map: MapSurface): String =
    val b = map.bounds
    val w = b.west.value
    val e = b.east.value
    val s = Math.max(-90.0, b.south.value)
    val n = Math.min(90.0, b.north.value)
    val lons = if e - w >= 360 then (-180.0, 180.0)
    else (wrapLon(w), wrapLon(e))
    s"${r5(lons._1)},${r5(s)},${r5(lons._2)},${r5(n)}"

  /** What the map shows now, if it is up: the preset menu puts that region's presets first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def viewBox: Option[BBox] =
    Option
      .when(TheMap.isUp)(TheMap.surface)
      .flatMap(m => scala.util.Try(bboxOf(m)).toOption)
      .flatMap(t => BBox.parse(BBox.Text(t)))

  /** The help of the "States shown" menu ([[UiState.areaPick]]). */
  val AreaPickHelp: String =
    "Which states the boundaries layer draws for the period. Sites use the same year, or the midpoint " +
      "for “all states in period”; playback shows one year at a time."

  /** Pure: how many of the map's `total` layers are on, as the layers button says it. */
  def layersLabel(on: Int, total: Int): String = s"Layers · $on of $total on"

  /** The layers the button counts: the findings, then every [[MapLayer]]. */
  val LayerCount: Int = 1 + MapLayer.values.length

  /** Whether the layers panel is open (a wide screen: on a phone it is the map & tools drawer). */
  val open: Var[Boolean] = Var(false)

  private val onCount: Signal[Int] =
    UiState.sources.signal.combineWith(UiState.layers.signal).map((src, ls) => (if src then 1 else 0) + ls.size)

  /** The undated sites' or events' switch, under their layer. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def undatedBox(field: Field[Boolean], what: String): HtmlElement =
    label(
      cls := "layer-check",
      input(
        typ := "checkbox",
        idAttr := s"undated-$what",
        controlled(checked <-- field.signal, onClick.mapToChecked --> { v => UiState.set(field := v)() })
      ),
      s" Also show $what with no known date"
    )

  /** One layer's row: its switch and — only while it is on — its own options (#1446, #1449). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def layerGroup(l: MapLayer): HtmlElement =
    def opts: List[HtmlElement] = l match
      case MapLayer.Boundaries => List(areaPickSelect)
      case MapLayer.Sites      =>
        List(
          IconSelect(IconSelect.Name("site type"), UiState.siteType, Val(LayerFilters.siteTypes)),
          undatedBox(UiState.sitesUndated, "sites")
        )
      case MapLayer.Media =>
        List(
          MediaFilters.select(
            MediaFilters.FieldName("provider"),
            UiState.mediaProvider,
            MediaFilters.providers.signal,
            k => MediaFilters.ChoiceLabel(MediaProvider.title(MediaProvider.Id(k.value)).value)
          ),
          MediaFilters.select(
            MediaFilters.FieldName("licence"),
            UiState.mediaLicense,
            MediaFilters.licences.signal,
            k => MediaFilters.ChoiceLabel(LicenseName.label(k.value))
          )
        )
      case MapLayer.Events =>
        List(
          IconSelect(IconSelect.Name("event kind"), UiState.eventKind, Val(LayerFilters.eventKinds)),
          IconSelect(IconSelect.Name("tier"), UiState.eventTier, Val(LayerFilters.tiers)),
          undatedBox(UiState.eventsUndated, "events")
        )
      case MapLayer.Notes => Nil
    div(
      cls := "layer-group",
      role := "group",
      aria.label := l.label.value,
      dataAttr("layer") := l.wire.value,
      LayerToggle(l.icon, l.label.value, UiState.layerOn(l), UiState.setLayer(l, _)),
      child.maybe <-- UiState.layerOn(l).map(on => Option.when(on)(div(cls := "layer-options", opts)))
    )

  /** The boundaries layer's "States shown" menu: how it reads the period ([[GeoPick]]). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def areaPickSelect: HtmlElement =
    label(
      cls := "layer-field",
      span(cls := "layer-field-label", "States shown"),
      select(
        idAttr := "area-pick",
        cls := "field area-pick-select",
        title := AreaPickHelp,
        controlled(
          value <-- UiState.areaPick.signal.map(_.wire.value),
          onChange.mapToValue --> { v =>
            GeoPick.parse(GeoPick.Wire(v)).foreach(p => UiState.set(UiState.areaPick := p)())
          }
        ),
        GeoPick.values.toList.map(p => option(value := p.wire.value, p.label))
      )
    )

  // `var`: the pending debounce timer of the moveend handler below
  private var timer: Int = 0

  /** "On the map" (only the sources in the map's view) or "Everywhere": the results follow the map's view while it is
    * on.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def setInView(on: Boolean): Unit =
    dom.window.clearTimeout(timer)
    if on && TheMap.isUp then UiState.set(UiState.inView := true, UiState.bbox := Some(bboxOf(TheMap.surface)))()
    else UiState.set(UiState.inView := false, UiState.bbox := None)()

  /** The "Layers · N of M on" button over the map, and the panel it opens: the findings and each layer, a layer's
    * options only while it is on, the background map and a link to the map's settings. On a phone the panel is the map
    * & tools drawer.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def init(config: UiConfig): HtmlElement =
    val initialInView = UiState.inView.now()
    div(
      cls := "layers-anchor",
      button(
        typ := "button",
        cls := "btn layers-btn",
        idAttr := "layers-btn",
        aria.expanded <-- open.signal,
        htmlAttr("aria-controls", com.raquo.laminar.codecs.StringAsIsCodec) := "layers-panel",
        Icon(Icon.Layers),
        span(child.text <-- onCount.map(layersLabel(_, LayerCount))),
        onClick --> (_ => open.update(!_))
      ),
      div(
        cls := "viewport-toggle layers-panel",
        idAttr := "layers-panel",
        role := "dialog",
        aria.label := "Map layers",
        cls("is-open") <-- open.signal,
        // on a phone this panel is the map & tools drawer, and this its heading
        MobileChrome.drawerHead(MobileChrome.Name("map & tools")),
        div(
          cls := "layers-head",
          h2(cls := "layers-title", "Layers"),
          button(
            typ := "button",
            cls := "btn btn--bare btn--icon",
            aria.label := "close the layers",
            "✕",
            onClick --> (_ => open.set(false))
          )
        ),
        div(
          cls := "layer-toggles",
          // the finding markers first: they are what the map is for
          div(
            cls := "layer-group",
            role := "group",
            aria.label := "findings",
            dataAttr("layer") := "sources",
            LayerToggle(
              PointKinds.Findings.icon,
              PointKinds.Findings.layerLabel.value,
              UiState.sources.signal,
              v => UiState.set(UiState.sources := v)()
            )
          ),
          MapLayer.values.toList.map(layerGroup)
        ),
        div(
          cls := "layers-base",
          h3(cls := "layers-subtitle", "Background map"),
          BaseMaps.controls,
          RouteLink(Route.Settings(SettingsSection.Data), cls := "layers-settings", "Map settings and downloads")
        )
      ),
      // Escape closes the panel — unless it closes a menu or the map options inside it first
      documentEvents(_.onKeyDown) --> { e =>
        val inner = Option(e.target)
          .collect { case el: dom.Element => el }
          .exists(_.closest(".map-options-anchor, .icon-select") != null)
        if e.key == "Escape" && open.now() && !Viewport.narrowNow && !inner then open.set(false)
      },
      documentEvents(_.onPointerDown) --> { e =>
        val t = e.target.asInstanceOf[dom.Element]
        if open.now() && !Viewport.narrowNow && t != null && t.closest(
            ".layers-anchor, .icon-select-menu, .map-options"
          ) == null
        then open.set(false)
      },
      onMountUnmountCallbackWithState(
        mount = ctx =>
          val subs = scala.collection.mutable.ListBuffer.empty[web.map.Subscription]
          TheMap.whenReady { map =>
            if ctx.thisNode.ref.isConnected then {
              // Push bbox on map moveend while "On the map" is chosen — when the view MOVED: the chrome around
              // the map changing its size (the sidebar dragged) ends with a moveend too, and refetched every
              // list for the same place
              // `var`: where the camera stood at the last moveend
              var camera = (map.center, map.zoom)
              val sub = map.on(MapEventKind.MoveEnd) { _ =>
                val now = (map.center, map.zoom)
                val moved = now != camera
                camera = now
                if moved && UiState.inView.now() then
                  dom.window.clearTimeout(timer)
                  timer = dom.window.setTimeout(
                    () => {
                      UiState.set(UiState.bbox := Some(bboxOf(map)))()
                    },
                    config.viewportDebounceMs.value
                  )
              }

              // If restoring from URL hash with inView = true, fit the map to the saved bbox.
              if initialInView then
                val bboxStr = UiState.bbox.now().getOrElse("")
                val parts = bboxStr.split(",").flatMap(_.toDoubleOption)
                if parts.length == 4 && parts.forall(java.lang.Double.isFinite) then
                  // minLon > maxLon means the saved viewport crossed the antimeridian: the map needs
                  // a monotonically increasing east bound.
                  val east = if parts(0) > parts(2) then parts(2) + 360 else parts(2)
                  map.fitBounds(
                    GeoBounds(
                      GeoBounds.South(parts(1)),
                      GeoBounds.West(parts(0)),
                      GeoBounds.North(parts(3)),
                      GeoBounds.East(east)
                    ),
                    MapSurface.FitBoundsPadding(0),
                    map.maxZoom,
                    animate = MapSurface.FitBoundsAnimate(false)
                  )
                else UiState.set(UiState.bbox := Some(bboxOf(map)))()
              val _ = subs += sub
            }
          }
          subs
        ,
        // the map outlives this panel: its handlers go with it, and so does a pending bbox write
        unmount = (_, st) =>
          st.foreach { subs =>
            dom.window.clearTimeout(timer)
            subs.foreach(_.cancel())
            subs.clear()
          }
      )
    )
