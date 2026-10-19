package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import org.scalajs.dom

/** The map's base-map control (docs/user-guide/exploring/web-ui.md, "The base map"), in two steps:
  *
  *  1. the FAMILY — the Histmersion map (drawn here from the server's own data, customisable) or an online
  *     raster map (OpenStreetMap's tiles: Standard, Muted, Dark), each with its own small menu;
  *  2. for the Histmersion map, a MAP OPTIONS panel (a popover from the map button, open while you change
  *     things, closed with Escape or a click elsewhere): the preset — every style the server serves, the
  *     built-in ones, a style file's, other code's — and per-view options over it: names and their language,
  *     relief strength, colours by height, land cover, sea-floor depth, contours and their interval, and
  *     "reset to preset". An option whose data is not downloaded is disabled, its line saying what to fetch.
  *
  * The options are a viewer's own ([[UiState.terrainView]], this browser); the style document stays the
  * source of the presets. Each change redraws at once — only the names' layer when only the names change. */
object MapOptions:

  /** The family of a base map. Pure. */
  def family(b: BaseMap): String = if b == BaseMap.Terrain then "histmersion" else "raster"

  /** The raster map shown last, to come back to when the family is switched back. */
  private val lastRaster: Var[BaseMap] = Var(BaseMap.Standard)

  private val open: Var[Boolean] = Var(false)

  /** The style shown now (the preset), if the styles are known. */
  private val preset: Signal[Option[TerrainStyle]] =
    UiState.terrainStyle.signal.combineWith(TerrainMap.styles.signal).map((id, ss) => ss.flatMap(_.pick(id)))

  private def setOptions(f: (TerrainStyle, ViewOptions) => ViewOptions): Unit =
    TerrainMap.styles.now().flatMap(_.pick(UiState.terrainStyle.now())).foreach { s =>
      val prefs = UiState.terrainView.now()
      UiState.set(UiState.terrainView := prefs.set(s.id, TerrainView.normalise(s, f(s, prefs.of(s.id)))))()
    }

  private def familySelect: HtmlElement =
    select(
      cls := "field map-family-select",
      aria.label := "base map",
      title := "The Histmersion map is drawn from this server's own data (no modern features) and can be customised; " +
        "the online maps are OpenStreetMap's tiles",
      option(value := "histmersion", child.text <-- TerrainMap.status.signal.map(st => TerrainMap.familyLabel(st))),
      option(value := "raster", "Online map"),
      controlled(
        value <-- UiState.baseMap.signal.map(family),
        onChange.mapToValue --> { v =>
          if v == "histmersion" then UiState.set(UiState.baseMap := BaseMap.Terrain)()
          else UiState.set(UiState.baseMap := lastRaster.now())()
        }
      ),
      onMountCallback(_ => { val _ = TerrainMap.refresh() })
    )

  private def rasterSelect: HtmlElement =
    select(
      cls := "field map-style-select",
      aria.label := "online map",
      cls("is-hidden") <-- UiState.baseMap.signal.map(_ == BaseMap.Terrain),
      BaseMap.values.toList.filter(_ != BaseMap.Terrain).map(b => option(value := b.wire, b.label)),
      controlled(
        value <-- UiState.baseMap.signal.map(b => if b == BaseMap.Terrain then lastRaster.now().wire else b.wire),
        onChange.mapToValue --> { v =>
          BaseMap.fromWire(v).foreach { b => lastRaster.set(b); UiState.set(UiState.baseMap := b)() }
        }
      )
    )

  /** The button that opens the options; disabled on an online map. */
  private def optionsButton: HtmlElement =
    button(
      typ := "button",
      idAttr := "map-options-button",
      cls := "btn btn--toggle btn--icon map-options-btn",
      aria.label := "map options",
      htmlAttr("aria-haspopup", com.raquo.laminar.codecs.StringAsIsCodec) := "dialog",
      aria.controls := "map-options",
      aria.expanded <-- open.signal,
      disabled <-- UiState.baseMap.signal.map(_ != BaseMap.Terrain),
      title <-- UiState.baseMap.signal.map(b => if b == BaseMap.Terrain then "Map options: style, names, relief, colours, sea floor, contours"
        else "Map options — for the Histmersion map"),
      Icon(Icon.MapContours),
      onClick --> { _ => open.update(!_) }
    )

  /** One option's row: a checkbox and its label, disabled with the reason when it cannot be used. */
  private def check(key: String, label: String, get: ViewOptions => Option[Boolean],
      put: (ViewOptions, Boolean) => ViewOptions, missing: Signal[Map[String, String]], shown: Signal[ViewOptions]): HtmlElement =
    val why = missing.map(_.get(key))
    div(
      cls := "map-option",
      cls("is-disabled") <-- why.map(_.isDefined),
      label_(
        input(typ := "checkbox", cls := s"map-option-$key", disabled <-- why.map(_.isDefined),
          checked <-- shown.combineWith(why).map((o, w) => w.isEmpty && get(o).contains(true)),
          onInput.mapToChecked --> (on => setOptions((_, v) => put(v, on)))),
        span(label)),
      child.maybe <-- why.map(_.map(w => div(cls := "map-option-why", w)))
    )

  private def label_(mods: Modifier[HtmlElement]*): HtmlElement = htmlTag("label")(mods*)

  private def panel: HtmlElement =
    val shownS  = preset.combineWith(UiState.terrainView.signal).map((p, prefs) => p.map(s => TerrainView.shown(s, prefs.of(s.id))))
      .map(_.getOrElse(ViewOptions()))
    val missing = preset.combineWith(TerrainMap.status.signal).map((p, st) =>
      p.fold(Map.empty[String, String])(s => TerrainView.unavailable(s, st.fold(Set.empty[String])(TerrainMap.layersHad))))
    val styleOptions = TerrainMap.styles.signal.map(_.map(_.styles).getOrElse(Nil))
    div(
      idAttr := "map-options",
      cls := "map-options",
      role := "dialog",
      aria.label := "map options",
      cls("is-hidden") <-- open.signal.combineWith(UiState.baseMap.signal).map((o, b) => !o || b != BaseMap.Terrain),
      div(cls := "map-option map-option-row",
        label_(span("Style"),
          select(cls := "field terrain-style-select", aria.label := "map style",
            children <-- styleOptions.split(_.id)((id, _, s) => option(value := id, child.text <-- s.map(_.name),
              title <-- s.map(_.description))),
            controlled(
              value <-- preset.map(_.fold("")(_.id)).composeChanges(_.delay(0)),
              onChange.mapToValue --> (v => UiState.set(UiState.terrainStyle := Some(v))())
            )))),
      check("names", "Names", _.names, (v, b) => v.copy(names = Some(b)), missing, shownS),
      div(cls := "map-option map-option-row",
        label_(span("Language"),
          select(cls := "field terrain-language-select", aria.label := "names' language",
            disabled <-- missing.map(_.contains("names")),
            TerrainView.Languages.map((code, name) => option(value := code, name)),
            controlled(
              value <-- UiState.terrainView.signal.combineWith(TerrainMap.styles.signal).map((p, ss) =>
                p.language.orElse(ss.map(_.language)).getOrElse("en")),
              onChange.mapToValue --> (v => UiState.set(UiState.terrainView := UiState.terrainView.now().copy(language = Some(v)))())
            )))),
      div(cls := "map-option map-option-row",
        cls("is-disabled") <-- missing.map(_.contains("relief")),
        label_(span("Relief"),
          input(typ := "range", cls := "map-option-relief", minAttr := "0", maxAttr := "2", stepAttr := "0.1",
            aria.label := "relief strength",
            disabled <-- missing.map(_.contains("relief")),
            controlled(
              value <-- shownS.map(o => o.relief.getOrElse(1.0).toString),
              onInput.mapToValue --> (v => v.toDoubleOption.foreach(k => setOptions((_, o) => o.copy(relief = Some(k)))))
            ))),
        child.maybe <-- missing.map(_.get("relief").map(w => div(cls := "map-option-why", w)))),
      check("heights", "Colours by height", _.heights, (v, b) => v.copy(heights = Some(b)), missing, shownS),
      check("landCover", "Land cover (forest, grass, scrub, sand, ice)", _.landCover, (v, b) => v.copy(landCover = Some(b)), missing, shownS),
      check("seaFloor", "Sea floor by depth", _.seaFloor, (v, b) => v.copy(seaFloor = Some(b)), missing, shownS),
      check("contours", "Contour lines", _.contours, (v, b) => v.copy(contours = Some(b)), missing, shownS),
      div(cls := "map-option map-option-row",
        label_(span("Interval"),
          select(cls := "field terrain-interval-select", aria.label := "contour interval",
            disabled <-- missing.combineWith(shownS).map((m, o) => m.contains("contours") || !o.contours.contains(true)),
            option(value := "", "auto (by zoom)"),
            TerrainView.Intervals.map(i => option(value := i.toInt.toString, s"${i.toInt} m")),
            controlled(
              value <-- shownS.map(_.interval.fold("")(_.toInt.toString)),
              onChange.mapToValue --> (v => setOptions((_, o) => o.copy(interval = v.toDoubleOption)))
            )))),
      div(cls := "map-option map-option-row",
        button(typ := "button", cls := "btn btn--ghost btn--sm map-options-reset", "Reset to preset",
          disabled <-- preset.combineWith(UiState.terrainView.signal).map((p, prefs) => p.forall(s => prefs.of(s.id).isEmpty)),
          onClick --> { _ => setOptions((_, _) => ViewOptions()) }),
        button(typ := "button", cls := "btn btn--ghost btn--sm map-options-close", "Close",
          onClick --> { _ => close() }))
    )

  /** Where the panel goes: under its button, its right edge at the button's — moved left or right to stay
    * inside the window. Pure. */
  def placement(buttonLeft: Double, buttonRight: Double, buttonBottom: Double, width: Double, windowWidth: Double): (Double, Double) =
    val x = math.max(8.0, math.min(windowWidth - width - 8, buttonRight - width))
    (if buttonLeft < 0 then 8.0 else x, buttonBottom + 4)

  private def place(): Unit =
    for
      b <- Option(dom.document.getElementById("map-options-button"))
      p <- Option(dom.document.getElementById("map-options")).map(_.asInstanceOf[dom.HTMLElement])
    do
      val r = b.getBoundingClientRect()
      val (x, y) = placement(r.left, r.right, r.bottom, math.min(300.0, dom.window.innerWidth * 0.92), dom.window.innerWidth)
      p.style.setProperty("--map-options-x", s"${x}px")
      p.style.setProperty("--map-options-y", s"${y}px")

  private def close(): Unit =
    open.set(false)
    Option(dom.document.getElementById("map-options-button")).foreach(_.asInstanceOf[dom.HTMLElement].focus())

  /** The base-map controls of the map's toolbar: the family, the online map's menu, the options. */
  def controls: HtmlElement =
    div(
      cls := "btn-group map-base-group",
      role := "group",
      aria.label := "base map",
      familySelect,
      rasterSelect,
      div(cls := "map-options-anchor", optionsButton, panel),
      UiState.baseMap.signal --> { b => if b != BaseMap.Terrain then { lastRaster.set(b); open.set(false) } },
      // the panel's own keyboard and pointer: Escape closes it (focus back on its button), a click
      // outside it closes it; it stays open while its options change
      documentEvents(_.onKeyDown) --> { e => if e.key == "Escape" && open.now() then close() },
      documentEvents(_.onPointerDown) --> { e =>
        val t = e.target.asInstanceOf[dom.Element]
        if open.now() && t != null && t.closest(".map-options-anchor") == null then open.set(false)
      },
      open.signal.changes.filter(identity) --> { _ =>
        place()
        dom.window.setTimeout(() => Option(dom.document.querySelector("#map-options select")).foreach(_.asInstanceOf[dom.HTMLElement].focus()), 0)
      },
      windowEvents(_.onResize) --> { _ => if open.now() then place() }
    )
