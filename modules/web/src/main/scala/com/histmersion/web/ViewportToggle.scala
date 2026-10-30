package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** "Only sources in view" toggle: mirrors the map's viewport into UiState.bbox so the server-side
  * filter and the shareable URL hash always agree with what the user is looking at.
  *
  * Ported from viewport.js. */
object ViewportToggle:

  private def r5(x: Double): Double = Math.round(x * 1e5) / 1e5

  /** Wrap longitude to [-180, 180]. The map can give unwrapped values past the antimeridian. */
  private def wrapLon(x: Double): Double =
    val y = ((x + 180) % 360 + 360) % 360 - 180
    if y == -180 && x > 0 then 180.0 else y

  /** Map bounds as "minLon,minLat,maxLon,maxLat", normalised to +/-180/+/-90.
    * A viewport straddling the antimeridian comes back with minLon > maxLon; the server splits it
    * into two envelopes. A viewport wider than the world becomes the whole world. */
  private def bboxOf(map: MapSurface): String =
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
  private def undatedBox(field: Field[Boolean], what: String): HtmlElement =
    label(
      cls := "layer-check",
      input(typ := "checkbox", idAttr := s"undated-$what",
        controlled(checked <-- field.signal, onClick.mapToChecked --> { v => UiState.set(field := v)() })),
      s" Also show $what with no known date"
    )

  /** One layer's row: its switch and — only while it is on — its own options (#1446, #1449). */
  private def layerGroup(l: MapLayer): HtmlElement =
    def opts: List[HtmlElement] = l match
      case MapLayer.Boundaries => List(areaPickSelect)
      case MapLayer.Sites      => List(IconSelect("site type", UiState.siteType, Val(LayerFilters.siteTypes)), undatedBox(UiState.sitesUndated, "sites"))
      case MapLayer.Media      => List(
        MediaFilters.select("provider", UiState.mediaProvider, MediaFilters.providers.signal, MediaProvider.title),
        MediaFilters.select("licence", UiState.mediaLicense, MediaFilters.licences.signal, LicenseName.label))
      case MapLayer.Events     => List(IconSelect("event kind", UiState.eventKind, Val(LayerFilters.eventKinds)),
        IconSelect("tier", UiState.eventTier, Val(LayerFilters.tiers)), undatedBox(UiState.eventsUndated, "events"))
      case MapLayer.Notes      => Nil
    div(cls := "layer-group", role := "group", aria.label := l.label, dataAttr("layer") := l.wire,
      LayerToggle(l.icon, l.label, UiState.layerOn(l), UiState.setLayer(l, _)),
      child.maybe <-- UiState.layerOn(l).map(on => Option.when(on)(div(cls := "layer-options", opts))))

  /** The boundaries layer's "States shown" menu: how it reads the period ([[GeoPick]]). */
  private def areaPickSelect: HtmlElement =
    label(cls := "layer-field",
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
      ))

  // `var`: the pending debounce timer of the moveend handler below
  private var timer: Int = 0

  /** "On the map" (only the sources in the map's view) or "Everywhere": the results follow the map's view
    * while it is on. */
  def setInView(on: Boolean): Unit =
    dom.window.clearTimeout(timer)
    if on && TheMap.isUp then UiState.set(UiState.inView := true, UiState.bbox := Some(bboxOf(TheMap.surface)))()
    else UiState.set(UiState.inView := false, UiState.bbox := None)()

  /** The "Layers · N of M on" button over the map, and the panel it opens: the findings and each layer, a
    * layer's options only while it is on, the background map and a link to the map's settings. On a phone
    * the panel is the map & tools drawer. */
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
        div(cls := "layers-head",
          h2(cls := "layers-title", "Layers"),
          button(typ := "button", cls := "btn btn--bare btn--icon", aria.label := "close the layers", "✕",
            onClick --> (_ => open.set(false)))),
        div(
          cls := "layer-toggles",
          // the finding markers first: they are what the map is for
          div(cls := "layer-group", role := "group", aria.label := "findings", dataAttr("layer") := "sources",
            LayerToggle(PointKinds.Findings.icon, PointKinds.Findings.layerLabel, UiState.sources.signal,
              v => UiState.set(UiState.sources := v)())),
          MapLayer.values.toList.map(layerGroup)
        ),
        div(cls := "layers-base",
          h3(cls := "layers-subtitle", "Background map"),
          BaseMaps.controls,
          RouteLink(Route.Settings(SettingsSection.Data), cls := "layers-settings", "Map settings and downloads"))
      ),
      // Escape closes the panel — unless it closes a menu or the map options inside it first
      documentEvents(_.onKeyDown) --> { e =>
        val inner = Option(e.target).collect { case el: dom.Element => el }
          .exists(_.closest(".map-options-anchor, .icon-select") != null)
        if e.key == "Escape" && open.now() && !Viewport.narrowNow && !inner then open.set(false)
      },
      documentEvents(_.onPointerDown) --> { e =>
        val t = e.target.asInstanceOf[dom.Element]
        if open.now() && !Viewport.narrowNow && t != null && t.closest(".layers-anchor, .icon-select-menu, .map-options") == null then open.set(false)
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
                timer = dom.window.setTimeout(() => {
                  UiState.set(UiState.bbox := Some(bboxOf(map)))()
                }, config.viewportDebounceMs)
            }

            // If restoring from URL hash with inView = true, fit the map to the saved bbox.
            if initialInView then
              val bboxStr = UiState.bbox.now().getOrElse("")
              val parts   = bboxStr.split(",").flatMap(_.toDoubleOption)
              if parts.length == 4 && parts.forall(java.lang.Double.isFinite) then
                // minLon > maxLon means the saved viewport crossed the antimeridian: the map needs
                // a monotonically increasing east bound.
                val east = if parts(0) > parts(2) then parts(2) + 360 else parts(2)
                map.fitBounds(GeoBounds(parts(1), parts(0), parts(3), east), 0, map.maxZoom, animate = false)
              else
                UiState.set(UiState.bbox := Some(bboxOf(map)))()
            sub
          },
        // the map outlives this panel: its handlers go with it, and so does a pending bbox write
        unmount = (_, st) => st.flatten.foreach { sub =>
          dom.window.clearTimeout(timer)
          sub.cancel()
        }
      )
    )

/** The media layer's two filter menus (#1446): provider and licence, each "all" or one value, their
  * options the server's counts (`/api/media/facets`, fetched once when the layer is first shown). */
object MediaFilters:
  val providers: Var[List[MediaCount]] = Var(Nil)
  val licences: Var[List[MediaCount]]  = Var(Nil)
  // `var`: the facets are fetched once per page
  private var asked = false

  private def ask(): Unit =
    if !asked then
      asked = true
      ApiClient.mediaFacets().foreach { f =>
        providers.set(f.providers)
        licences.set(f.licences)
      }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** Pure: a menu's options — "all", then each value with its count, the one chosen kept even when
    * the counts no longer list it. */
  def options(counts: List[MediaCount], chosen: Option[String], label: String => String): List[(String, String)] =
    val listed = counts.map(c => c.key -> s"${label(c.key)} (${c.n})")
    val kept = chosen.filterNot(c => counts.exists(_.key == c)).map(c => c -> label(c)).toList
    ("" -> "all") :: (listed ++ kept)

  def select(name: String, field: Field[Option[String]], counts: Signal[List[MediaCount]],
      label: String => String, disabled: Signal[Boolean] = Val(false), why: String = ""): HtmlElement =
    IconSelect(s"media $name", field, counts.combineWith(field.signal).map { (cs, chosen) =>
      options(cs, chosen, label).map((v, t) =>
        IconSelect.Opt(v, t, if v.isEmpty then LayerFilters.layerIcon(PointKinds.Media) else LayerFilters.badge(Shape.Photo, CardIcons.Media)))
    }, disabled, why).amend(onMountCallback(_ => ask()))

/** The events and sites layers' menus (#1449): fixed vocabularies — the kind taxonomy, listed under its
  * two groups (each group's "all" first), and the tiers; the site types. "all" is no filter. Each option
  * shows what the map draws for it: the kind's or the type's badge, the tier's filled or dashed square;
  * "all" the layer's own icon. Pure option lists. */
object LayerFilters:
  /** Pure: a badge of `shape` with `glyph`, as an option's icon (in the accent colour: no date). */
  def badge(shape: Shape, glyph: String): String =
    s"""<span class="hd-badge hd-badge--${shape.cls} opt-badge">${Glyphs.badge(glyph)}</span>"""

  /** Pure: a layer's own switch icon, the "all" option's. */
  def layerIcon(k: PointKind[?]): String = Icon.html(k.icon)

  val eventKinds: List[IconSelect.Opt] =
    IconSelect.Opt("", "all", layerIcon(PointKinds.Events)) :: EventTaxonomy.groups.flatMap { g =>
      val under = Some(s"$g events")
      IconSelect.Opt(g, s"all $g", layerIcon(PointKinds.Events), under) ::
        EventTaxonomy.kinds.filter(_.group == g).map(k => IconSelect.Opt(k.kind, k.label, badge(Shape.Square, k.icon), under))
    }
  /** What each event tier means; the tier menu's tooltips. */
  def tierMeaning(tier: String): Option[String] = tier match
    case EventTier.Attested  => Some("Events a source in the corpus attests: its own record, or an attestation linked to it.")
    case EventTier.Catalogue => Some("Events only an external catalogue knows; no source in the corpus attests them yet.")
    case _                   => None

  val tiers: List[IconSelect.Opt] = List(
    IconSelect.Opt("", "all", Icon.html(Icon.TierPair), hint = Some("Every event, attested or not.")),
    IconSelect.Opt(EventTier.Attested, "attested by a source", """<span class="hd-badge hd-badge--square hd-ev hd-ev--attested opt-badge"></span>""",
      hint = tierMeaning(EventTier.Attested)),
    IconSelect.Opt(EventTier.Catalogue, "catalogue only", """<span class="hd-badge hd-badge--square hd-ev hd-ev--catalogue opt-badge"></span>""",
      hint = tierMeaning(EventTier.Catalogue)))
  val siteTypes: List[IconSelect.Opt] =
    IconSelect.Opt("", "all", layerIcon(PointKinds.Sites)) :: SiteTypes.all.map(t => IconSelect.Opt(t.id, t.label, badge(Shape.Circle, t.icon)))
