package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.scalajs.js

/** "Only sources in view" toggle: mirrors the Leaflet viewport into UiState.bbox so the server-side
  * filter and the shareable URL hash always agree with what the user is looking at.
  *
  * Ported from viewport.js. */
object ViewportToggle:

  private def r5(x: Double): Double = Math.round(x * 1e5) / 1e5

  /** Wrap longitude to [-180, 180]. Leaflet can produce unwrapped values past the antimeridian. */
  private def wrapLon(x: Double): Double =
    val y = ((x + 180) % 360 + 360) % 360 - 180
    if y == -180 && x > 0 then 180.0 else y

  /** Map bounds as "minLon,minLat,maxLon,maxLat", normalised to +/-180/+/-90.
    * A viewport straddling the antimeridian comes back with minLon > maxLon; the server splits it
    * into two envelopes. A viewport wider than the world becomes the whole world. */
  private def bboxOf(map: LeafletMap): String =
    val b = map.getBounds()
    val w = b.getWest()
    val e = b.getEast()
    val s = Math.max(-90.0, b.getSouth())
    val n = Math.min(90.0, b.getNorth())
    val lons = if e - w >= 360 then (-180.0, 180.0)
               else (wrapLon(w), wrapLon(e))
    s"${r5(lons._1)},${r5(s)},${r5(lons._2)},${r5(n)}"

  /** What the map shows now, if it is up: the preset menu puts that region's presets first. */
  def viewBox: Option[BBox] =
    Option(MapView.leafletMap).flatMap(m => scala.util.Try(bboxOf(m)).toOption).flatMap(BBox.parse)

  private def baseMapSelect: HtmlElement =
    select(
      cls := "field map-style-select",
      aria.label := "base map",
      controlled(
        value <-- UiState.baseMap.signal.map(_.wire),
        onChange.mapToValue --> { v => BaseMap.fromWire(v).foreach(b => UiState.set(UiState.baseMap := b)()) }
      ),
      BaseMap.values.toList.map(b => option(value := b.wire, b.label))
    )

  /** The tools (upload, jobs, the lists, the key), for the map & tools drawer on a phone, where the
    * filter bar that holds them on a wide screen is a drawer of its own. The same buttons — they show
    * the same state — so nothing here can disagree with the wide layout. Choosing one closes the
    * drawer: its window is what the reader wants now. */
  private def drawerTools: HtmlElement =
    div(
      cls := "drawer-tools",
      div(cls := "drawer-subtitle", "tools"),
      Toolbar.element,
      onClick --> { e =>
        if Option(e.target.asInstanceOf[dom.Element].closest("button")).isDefined then MobileChrome.close()
      }
    )

  def init(config: UiConfig): HtmlElement =
    // `var`: the pending debounce timer of the moveend handler below
    var timer: Int = 0
    val initialInView = UiState.inView.now()

    def setInView(on: Boolean): Unit =
      dom.window.clearTimeout(timer)
      if on then UiState.set(UiState.inView := true, UiState.bbox := Some(bboxOf(MapView.leafletMap)))()
      else UiState.set(UiState.inView := false, UiState.bbox := None)()

    div(
      cls := "viewport-toggle",
      // on a phone this row is the map & tools drawer, and this its heading
      MobileChrome.drawerHead("map & tools"),
      // three groups, one separator between each (`.toolbar`): what the map draws, the base map
      // under it, and whether the list follows the map
      div(
        cls := "toolbar layer-toggles",
        div(cls := "btn-group", role := "group", aria.label := "map layers",
          // the finding markers first: they are what the map is for, the overlays come after
          LayerToggle(Icon.Sources, "sources", UiState.sources.signal, v => UiState.set(UiState.sources := v)()),
          MapLayer.values.toList.map(l => LayerToggle(l.icon, l.label, UiState.layerOn(l), UiState.setLayer(l, _)))),
        div(cls := "btn-group", baseMapSelect),
        // the media layer's provider and licence filters, shown while the layer is on (#1446)
        div(cls := "btn-group media-filters", cls("is-hidden") <-- UiState.layerOn(MapLayer.Media).map(!_),
          role := "group", aria.label := "media filters",
          MediaFilters.select("provider", UiState.mediaProvider, MediaFilters.providers.signal, MediaProvider.title),
          MediaFilters.select("licence", UiState.mediaLicense, MediaFilters.licences.signal, identity)),
        // the events layer's kind and tier menus, and the sites layer's type menu (#1449)
        div(cls := "btn-group event-filters", cls("is-hidden") <-- UiState.layerOn(MapLayer.Events).map(!_),
          role := "group", aria.label := "event filters",
          LayerFilters.select("event kind", UiState.eventKind, LayerFilters.eventKinds),
          LayerFilters.select("tier", UiState.eventTier, LayerFilters.tiers)),
        div(cls := "btn-group site-filters", cls("is-hidden") <-- UiState.layerOn(MapLayer.Sites).map(!_),
          role := "group", aria.label := "site filters",
          LayerFilters.select("site type", UiState.siteType, LayerFilters.siteTypes)),
        // a highlighted toggle like the layers, not a checkbox: it shows UiState.inView itself
        div(cls := "btn-group",
          LayerToggle(Icon.InView, "only sources in view", UiState.inView.signal, setInView))
      ),
      drawerTools,
      onMountCallback { ctx =>
        val map = MapView.leafletMap
        if map != null then
          // Push bbox on map moveend/zoomend when the checkbox is checked.
          val handler: js.Function1[LeafletEvent, Unit] = { (_: LeafletEvent) =>
            if UiState.inView.now() then
              dom.window.clearTimeout(timer)
              timer = dom.window.setTimeout(() => {
                UiState.set(UiState.bbox := Some(bboxOf(map)))()
              }, config.viewportDebounceMs)
          }
          map.on("moveend", handler)
          map.on("zoomend", handler)

          // If restoring from URL hash with inView = true, fit the map to the saved bbox.
          if initialInView then
            val bboxStr = UiState.bbox.now().getOrElse("")
            val parts   = bboxStr.split(",").flatMap(_.toDoubleOption)
            if parts.length == 4 && parts.forall(java.lang.Double.isFinite) then
              // minLon > maxLon means the saved viewport crossed the antimeridian.
              // Leaflet needs a monotonically increasing east bound.
              val east = if parts(0) > parts(2) then parts(2) + 360 else parts(2)
              map.fitBounds(js.Array(
                js.Array(parts(1), parts(0)),
                js.Array(parts(3), east)
              ))
            else
              UiState.set(UiState.bbox := Some(bboxOf(map)))()
      }
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
      label: String => String): HtmlElement =
    com.raquo.laminar.api.L.select(
      cls := "field", aria.label := s"media $name", title := s"media $name",
      onMountCallback(_ => ask()),
      children <-- counts.combineWith(field.signal).map { (cs, chosen) =>
        options(cs, chosen, label).map((v, t) =>
          option(value := v, selected := chosen.getOrElse("") == v, s"$name: $t"))
      },
      onChange.mapToValue --> { v => UiState.set(field := Option(v).filter(_.nonEmpty))() }
    )

/** The events and sites layers' menus (#1449): fixed vocabularies — the kind taxonomy (its two groups
  * first) and the tiers; the site types. "all" is no filter. Pure option lists. */
object LayerFilters:
  val eventKinds: List[(String, String)] =
    ("" -> "all") :: EventTaxonomy.groups.map(g => g -> s"$g (all)") ++ EventTaxonomy.kinds.map(k => k.kind -> k.label)
  val tiers: List[(String, String)] =
    List("" -> "all", EventTier.Attested -> "attested by a source", EventTier.Catalogue -> "catalogue only")
  val siteTypes: List[(String, String)] = ("" -> "all") :: SiteTypes.all.map(t => t.id -> t.label)

  def select(name: String, field: Field[Option[String]], options: List[(String, String)]): HtmlElement =
    com.raquo.laminar.api.L.select(
      cls := "field", aria.label := name, title := name,
      children <-- field.signal.map { chosen =>
        options.map((v, t) => option(value := v, selected := chosen.getOrElse("") == v, s"$name: $t"))
      },
      onChange.mapToValue --> { v => UiState.set(field := Option(v).filter(_.nonEmpty))() }
    )
