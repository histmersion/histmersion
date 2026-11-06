package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.{BBox, GeoPick}
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.media.MediaProvider

/** "Only sources in view" toggle: mirrors the map's viewport into UiState.bbox so the server-side filter and the
  * shareable URL hash always agree with what the user is looking at.
  *
  * Ported from viewport.js.
  */
object ViewportToggle:

  type R5Result = R5Result.T
  object R5Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type R5X = R5X.T
  object R5X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrapLonResult = WrapLonResult.T
  object WrapLonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrapLonX = WrapLonX.T
  object WrapLonX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BboxOfResult = BboxOfResult.T
  object BboxOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AreaPickHelp = AreaPickHelpValue
  type AreaPickHelpValue = AreaPickHelpValue.T
  object AreaPickHelpValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayersLabelResult = LayersLabelResult.T
  object LayersLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayersLabelOn = LayersLabelOn.T
  object LayersLabelOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayersLabelTotal = LayersLabelTotal.T
  object LayersLabelTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerCount = LayerCountValue
  type LayerCountValue = LayerCountValue.T
  object LayerCountValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Open = Open.T
  object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnCount = OnCount.T
  object OnCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UndatedBoxField = UndatedBoxField.T
  object UndatedBoxField extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UndatedBoxWhat = UndatedBoxWhat.T
  object UndatedBoxWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Timer = Timer.T
  object Timer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetInViewOn = SetInViewOn.T
  object SetInViewOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def r5(x: com.histmersion.web.ViewportToggle.R5X): com.histmersion.web.ViewportToggle.R5Result = Math.round(x * 1e5) / 1e5

  /** Wrap longitude to [-180, 180]. The map can give unwrapped values past the antimeridian. */
  private def wrapLon(x: com.histmersion.web.ViewportToggle.WrapLonX): com.histmersion.web.ViewportToggle.WrapLonResult =
    val y = ((x + 180) % 360 + 360) % 360 - 180
    if y == -180 && x > 0 then 180.0 else y

  /** Map bounds as "minLon,minLat,maxLon,maxLat", normalised to +/-180/+/-90. A viewport straddling the antimeridian
    * comes back with minLon > maxLon; the server splits it into two envelopes. A viewport wider than the world becomes
    * the whole world.
    */
  private def bboxOf(map: MapSurface): com.histmersion.web.ViewportToggle.BboxOfResult =
    val b = map.bounds
    val w = b.west
    val e = b.east
    val s = Math.max(-90.0, b.south)
    val n = Math.min(90.0, b.north)
    val lons = if e - w >= 360 then (-180.0, 180.0)
    else (wrapLon(w), wrapLon(e))
    s"${r5(lons._1)},${r5(s)},${r5(lons._2)},${r5(n)}"

  /** What the map shows now, if it is up: the preset menu puts that region's presets first. */
  def viewBox: Option[BBox] =
    Option.when(TheMap.isUp)(TheMap.surface).flatMap(m => scala.util.Try(bboxOf(m)).toOption).flatMap(BBox.parse)

  /** The help of the "States shown" menu ([[UiState.areaPick]]). */
  val AreaPickHelp: com.histmersion.web.ViewportToggle.AreaPickHelp =
    "Which states the boundaries layer draws for the period. Sites use the same year, or the midpoint " +
      "for “all states in period”; playback shows one year at a time."

  /** Pure: how many of the map's `total` layers are on, as the layers button says it. */
  def layersLabel(on: com.histmersion.web.ViewportToggle.LayersLabelOn, total: com.histmersion.web.ViewportToggle.LayersLabelTotal): com.histmersion.web.ViewportToggle.LayersLabelResult = s"Layers · $on of $total on"

  /** The layers the button counts: the findings, then every [[MapLayer]]. */
  val LayerCount: com.histmersion.web.ViewportToggle.LayerCount = 1 + MapLayer.values.length

  /** Whether the layers panel is open (a wide screen: on a phone it is the map & tools drawer). */
  val open: Var[com.histmersion.web.ViewportToggle.Open] = Var(false)

  private val onCount: Signal[com.histmersion.web.ViewportToggle.OnCount] =
    UiState.sources.signal.combineWith(UiState.layers.signal).map((src, ls) => (if src then 1 else 0) + ls.size)

  /** The undated sites' or events' switch, under their layer. */
  private def undatedBox(field: Field[com.histmersion.web.ViewportToggle.UndatedBoxField], what: com.histmersion.web.ViewportToggle.UndatedBoxWhat): HtmlElement =
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
  private def layerGroup(l: MapLayer): HtmlElement =
    def opts: List[HtmlElement] = l match
      case MapLayer.Boundaries => List(areaPickSelect)
      case MapLayer.Sites      =>
        List(
          IconSelect("site type", UiState.siteType, Val(LayerFilters.siteTypes)),
          undatedBox(UiState.sitesUndated, "sites")
        )
      case MapLayer.Media =>
        List(
          MediaFilters.select("provider", UiState.mediaProvider, MediaFilters.providers.signal, MediaProvider.title),
          MediaFilters.select("licence", UiState.mediaLicense, MediaFilters.licences.signal, LicenseName.label)
        )
      case MapLayer.Events =>
        List(
          IconSelect("event kind", UiState.eventKind, Val(LayerFilters.eventKinds)),
          IconSelect("tier", UiState.eventTier, Val(LayerFilters.tiers)),
          undatedBox(UiState.eventsUndated, "events")
        )
      case MapLayer.Notes => Nil
    div(
      cls := "layer-group",
      role := "group",
      aria.label := l.label,
      dataAttr("layer") := l.wire,
      LayerToggle(l.icon, l.label, UiState.layerOn(l), UiState.setLayer(l, _)),
      child.maybe <-- UiState.layerOn(l).map(on => Option.when(on)(div(cls := "layer-options", opts)))
    )

  /** The boundaries layer's "States shown" menu: how it reads the period ([[GeoPick]]). */
  private def areaPickSelect: HtmlElement =
    label(
      cls := "layer-field",
      span(cls := "layer-field-label", "States shown"),
      select(
        idAttr := "area-pick",
        cls := "field area-pick-select",
        title := AreaPickHelp,
        controlled(
          value <-- UiState.areaPick.signal.map(_.wire),
          onChange.mapToValue --> { v => GeoPick.parse(v).foreach(p => UiState.set(UiState.areaPick := p)()) }
        ),
        GeoPick.values.toList.map(p => option(value := p.wire, p.label))
      )
    )

  // `var`: the pending debounce timer of the moveend handler below
  private var timer: com.histmersion.web.ViewportToggle.Timer = 0

  /** "On the map" (only the sources in the map's view) or "Everywhere": the results follow the map's view while it is
    * on.
    */
  def setInView(on: com.histmersion.web.ViewportToggle.SetInViewOn): Unit =
    dom.window.clearTimeout(timer)
    if on && TheMap.isUp then UiState.set(UiState.inView := true, UiState.bbox := Some(bboxOf(TheMap.surface)))()
    else UiState.set(UiState.inView := false, UiState.bbox := None)()

  /** The "Layers · N of M on" button over the map, and the panel it opens: the findings and each layer, a layer's
    * options only while it is on, the background map and a link to the map's settings. On a phone the panel is the map
    * & tools drawer.
    */
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
        MobileChrome.drawerHead("map & tools"),
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
              PointKinds.Findings.layerLabel,
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
        mount = _ =>
          Option.when(TheMap.isUp)(TheMap.surface).map { map =>
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
                  config.viewportDebounceMs
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
                map.fitBounds(GeoBounds(parts(1), parts(0), parts(3), east), 0, map.maxZoom, animate = false)
              else UiState.set(UiState.bbox := Some(bboxOf(map)))()
            sub
          },
        // the map outlives this panel: its handlers go with it, and so does a pending bbox write
        unmount = (_, st) =>
          st.flatten.foreach { sub =>
            dom.window.clearTimeout(timer)
            sub.cancel()
          }
      )
    )
